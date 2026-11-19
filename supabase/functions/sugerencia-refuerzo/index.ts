import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { AXES, summarize, type CatalogOption, type Axis, type LearningSession } from "./logic.ts";
import { prepare, requestChoices, resolve } from "./decision.ts";
import { globalBrief } from "./global.ts";
import { generateGlobalSuggestion, GeminiError, generateSuggestion } from "./gemini.ts";

const headers = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type", "Content-Type": "application/json" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers });
const minimum = 5;
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function databaseError(step: string, code?: string) {
  const diagnostic = code && /^[A-Z0-9]{3,12}$/.test(code) ? ` (${code})` : "";
  return json({ error: `Supabase no pudo ${step}${diagnostic}.` }, 502);
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers });
  if (request.method !== "POST") return json({ error: "Método no permitido" }, 405);
  const authorization = request.headers.get("Authorization");
  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!authorization) return json({ error: "Se requiere una sesión docente." }, 401);
  if (!url || !anonKey || !serviceKey) return json({ error: "Falta la configuración de Supabase en la función de sugerencias." }, 503);
  let body: { action?: string; student_id?: string; axis?: string };
  try { body = await request.json(); } catch { return json({ error: "Solicitud inválida" }, 400); }
  if (!body.student_id || !uuidPattern.test(body.student_id) || !["read", "generate", "generate_global"].includes(body.action ?? "") ||
      (body.action === "generate" && !AXES.includes(body.axis as Axis))) return json({ error: "Solicitud inválida" }, 400);
  const client = createClient(url, anonKey, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } });
  const { data: auth, error: authError } = await client.auth.getUser();
  if (authError && (authError.status ?? 500) >= 500) return json({ error: "El servicio de autenticación de Supabase no está disponible." }, 503);
  if (authError || !auth.user) return json({ error: "Sesión docente inválida o vencida." }, 401);
  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const studentId = body.student_id;
  let lease: string | undefined;
  let step = "consultar el progreso";
  const global = body.action === "generate_global";
  const requestedAxis = global ? "GLOBAL" : body.axis as Axis;
  try {
    // This existing RPC verifies the caller is an active teacher of this child.
    // Only aggregates and whitelisted topic codes leave this function for Gemini.
    const sessions: LearningSession[] = [];
    let after: string | null = null;
    for (let page = 0; ; page++) {
      if (page >= 1000) throw new Error("Demasiadas sesiones");
      const pageResponse: { data: { sessions?: LearningSession[] } | null; error: { code?: string } | null } =
        await client.rpc("docente_descargar_aprendizaje", { p_student_id: studentId, p_after: after });
      if (pageResponse.error?.code === "42501") return json({ error: "No tienes acceso al progreso de este estudiante" }, 403);
      if (pageResponse.error) return databaseError("leer el progreso del estudiante", pageResponse.error.code);
      const rows: LearningSession[] | undefined = pageResponse.data?.sessions;
      if (!Array.isArray(rows)) throw new Error("No se pudo leer el progreso");
      sessions.push(...rows);
      if (rows.length < 50) break;
      const next: string | undefined = rows[rows.length - 1]?.id;
      if (typeof next !== "string" || next === after) throw new Error("Paginación inválida");
      after = next;
    }
    step = "consultar el catálogo";
    const { data: catalog, error: catalogError } = await client.rpc("docente_catalogo_sugerencia");
    if (catalogError || !Array.isArray(catalog)) return databaseError("leer el catálogo de ejercicios", catalogError?.code);
    const summaries = AXES.map(axis => summarize(sessions, axis, catalog as CatalogOption[]));
    type Generated = { topic: string; evidence: string; activity: string; generated_at: string; practice_count: number };
    const summary = summaries.find(item => item.axis === requestedAxis);
    const practiceCount = global ? summaries.reduce((sum, s) => sum + s.totalPractice, 0) : summary?.totalPractice ?? 0;
    const rows = global ? [{ axis: "GLOBAL", totalPractice: practiceCount }] : summaries;
    const responseFor = (generated: Generated | null = null) => ({
      subjects: rows.map(item => ({
        axis: item.axis, practice_count: item.totalPractice, minimum,
        new_answers: 0, can_generate: true,
        suggestion: item.axis === requestedAxis ? generated : null,
      })),
    });
    if (body.action === "read" || practiceCount < minimum) return json(responseFor());
    const { data: reservation, error: reserveError } = await admin.rpc("reservar_sugerencia_docente", {
      p_estudiante: studentId, p_eje: requestedAxis,
    });
    if (reserveError) return databaseError("reservar la generación de la sugerencia", reserveError.code);
    if (reservation?.status === "busy") return json({ error: "Ya se está generando una sugerencia para este estudiante." }, 409);
    if (reservation?.status !== "reserved" || typeof reservation.lease !== "string") throw new Error("Reserva inválida");
    lease = reservation.lease;
    const geminiKey = Deno.env.get("GEMINI_API_KEY");
    if (!geminiKey) return json({ error: "Gemini no está configurado." }, 503);
    step = "obtener y validar la respuesta de Gemini";
    let validated;
    if (global) {
      const brief = globalBrief(summaries, catalog);
      validated = await generateGlobalSuggestion(geminiKey, brief, reservation.activity);
    } else {
      if (!summary) throw new Error("Materia desconocida");
      const candidates = prepare(summary, catalog);
      const allowed = requestChoices(candidates, reservation);
      const generated = await generateSuggestion(geminiKey, summary, candidates, allowed);
      validated = resolve(generated, candidates);
      if (validated.topic === reservation.topic && validated.activity === reservation.activity) {
        validated = resolve(allowed[0], candidates);
      }
    }
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify(validated)));
    const signature = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
    step = "guardar la sugerencia";
    const { data: stored, error: storeError } = await admin.rpc("finalizar_sugerencia_v3", {
      p_estudiante: studentId, p_eje: requestedAxis, p_reserva: lease, p_total: practiceCount,
      p_firma: signature, p_tema: validated.topic, p_evidencia: validated.evidence, p_propuesta: validated.activity,
    });
    if (storeError) return databaseError("guardar la sugerencia", storeError.code);
    if (stored !== true) return json({ error: "La reserva de generación venció antes de guardar la sugerencia." }, 409);
    lease = undefined;
    return json(responseFor({ ...validated, generated_at: new Date().toISOString(), practice_count: practiceCount }));
  } catch (error) {
    if (error instanceof GeminiError && error.status === 429) return json({ error: "Gemini alcanzó su límite de uso. Inténtalo más tarde." }, 429);
    if (error instanceof GeminiError) return json({ error: error.message }, 502);
    if (error instanceof DOMException && error.name === "TimeoutError") return json({ error: "Se agotó el tiempo de espera al consultar Gemini." }, 504);
    console.error("No se pudo preparar la sugerencia", error instanceof Error ? error.message : "error");
    const known = ["Plan general inválido", "Gemini no devolvió una sugerencia", "Reserva inválida",
      "No se pudo leer el progreso", "Paginación inválida", "La decisión no corresponde a la evidencia o al catálogo."];
    const detail = error instanceof Error && known.includes(error.message) ? error.message
      : error instanceof SyntaxError ? "La respuesta no contiene JSON válido." : "Consulta el registro de la función.";
    return json({ error: `Error al ${step}. ${detail}` }, 502);
  } finally {
    if (lease) await admin.rpc("cancelar_sugerencia_ia", { p_estudiante: studentId, p_eje: requestedAxis, p_reserva: lease });
  }
});

