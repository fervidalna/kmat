import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
};

type SyncEvent = {
  id: string;
  operation: "SESSION_STARTED" | "ATTEMPT_RECORDED";
  payload: Record<string, unknown>;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authorization = request.headers.get("Authorization");
  if (!authorization) {
    return new Response(JSON.stringify({ error: "Sesión autenticada requerida" }), { status: 401, headers: corsHeaders });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const authenticatedClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const { data: { user }, error: userError } = await authenticatedClient.auth.getUser();
  if (userError || !user) {
    return new Response(JSON.stringify({ error: "Sesión inválida" }), { status: 401, headers: corsHeaders });
  }

  const { events } = await request.json() as { events: SyncEvent[] };
  if (!Array.isArray(events) || events.length === 0 || events.length > 50) {
    return new Response(JSON.stringify({ error: "Lote de sincronización inválido" }), { status: 400, headers: corsHeaders });
  }

  const admin = createClient(supabaseUrl, serviceRoleKey);
  for (const event of events) {
    const studentId = String(event.payload.student_remote_id ?? "");
    const { data: relation } = await admin
      .from("responsable_estudiante")
      .select("id_estudiante")
      .eq("id_estudiante", studentId)
      .eq("id_responsable", user.id)
      .eq("activo", true)
      .maybeSingle();
    if (!relation) {
      return new Response(JSON.stringify({ error: "El usuario no puede sincronizar este estudiante" }), { status: 403, headers: corsHeaders });
    }

    if (event.operation === "SESSION_STARTED") {
      const { error } = await admin.from("sesion_aprendizaje").upsert({
        id_sesion: event.payload.local_session_id,
        id_estudiante: studentId,
        tipo_sesion: "PRACTICA",
        estado: "EN_CURSO",
        inicio_en: new Date(Number(event.payload.started_at)).toISOString(),
      }, { onConflict: "id_sesion" });
      if (error) return new Response(JSON.stringify({ error: error.message }), { status: 422, headers: corsHeaders });
    }

    if (event.operation === "ATTEMPT_RECORDED") {
      const assignmentId = String(event.payload.local_attempt_id);
      const exerciseVersionId = String(event.payload.exercise_version_remote_id);
      const respondedAt = new Date(Number(event.payload.recorded_at)).toISOString();
      const { error: assignmentError } = await admin.from("asignacion_ejercicio").upsert({
        id_asignacion: assignmentId,
        id_estudiante: studentId,
        id_ejercicio_version: exerciseVersionId,
        id_sesion: event.payload.local_session_id,
        origen: "MOTOR",
        estado: "COMPLETADA",
        asignado_en: respondedAt,
        iniciado_en: respondedAt,
        completado_en: respondedAt,
      }, { onConflict: "id_asignacion" });
      if (assignmentError) return new Response(JSON.stringify({ error: assignmentError.message }), { status: 422, headers: corsHeaders });

      const { error: attemptError } = await admin.from("intento_ejercicio").upsert({
        id_intento: assignmentId,
        id_asignacion: assignmentId,
        numero_intento: Number(event.payload.attempt_number),
        respuesta_reconocida: event.payload.answer,
        respuesta_final: { answer: event.payload.answer, strokes: event.payload.strokes },
        es_correcta: Boolean(event.payload.is_correct),
        tiempo_respuesta_ms: Number(event.payload.elapsed_millis),
        respondido_en: respondedAt,
      }, { onConflict: "id_intento" });
      if (attemptError) return new Response(JSON.stringify({ error: attemptError.message }), { status: 422, headers: corsHeaders });
    }
  }

  return new Response(JSON.stringify({ synchronized: events.length }), {
    status: 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});
