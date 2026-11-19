import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
};

type SyncEvent = {
  id: string;
  operation: "SESSION_STARTED" | "SESSION_COMPLETED" | "ATTEMPT_RECORDED" | "SESSION_PLANNED" | "DIAGNOSTIC_RECORDED";
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

  const body = await request.json() as {
    action?: string; student_local_id?: string; after?: string; events?: SyncEvent[];
    session_id?: string; session_type?: string; started_at?: number;
  };
  const admin = createClient(supabaseUrl, serviceRoleKey);
  // Read the caller's own links under RLS; this does not require service-role table grants.
  const { data: relations, error: relationError } = await authenticatedClient
    .from("responsable_estudiante")
    .select("id_estudiante")
    .eq("id_responsable", user.id)
    .eq("activo", true);
  if (relationError) return new Response(JSON.stringify({ error: relationError.message }), { status: 422, headers: corsHeaders });
  const studentIds = (relations ?? []).map((relation) => relation.id_estudiante as string);

  if (body.action === "claim_learning_session") {
    const { data, error } = await authenticatedClient.rpc("retomar_sesion_aprendizaje", {
      p_student_id: body.student_local_id, p_session_type: body.session_type,
      p_session_id: body.session_id, p_started_at: body.started_at,
    });
    return new Response(JSON.stringify(error ? { error: error.message } : data), {
      status: error ? 422 : 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  if (body.action === "sync_profiles") {
    return new Response(JSON.stringify({ student_ids: studentIds }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  if (body.action === "download_learning_state") {
    const { data, error } = await authenticatedClient.rpc("descargar_aprendizaje", {
      p_student_id: body.student_local_id,
      p_after: body.after ?? null,
    });
    return new Response(JSON.stringify(error ? { error: error.message } : data), {
      status: error ? 422 : 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  if (body.action === "download_progress") {
    if (!body.student_local_id || !studentIds.includes(body.student_local_id)) {
      return new Response(JSON.stringify({ progress: [] }), { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }
    const { data, error } = await admin
      .from("asignacion_ejercicio")
      .select("id_estudiante,ejercicio_version!inner(tipo_ejercicio),sesion_aprendizaje!inner(inicio_en),intento_ejercicio(es_correcta,tiempo_respuesta_ms,respondido_en,respuesta_final)")
      .eq("id_estudiante", body.student_local_id)
      .not("id_sesion", "is", null);
    if (error) return new Response(JSON.stringify({ error: error.message }), { status: 422, headers: corsHeaders });

    const progress = (data ?? []).flatMap((assignment) => {
      const exercise = assignment.ejercicio_version as unknown as { tipo_ejercicio: string };
      const session = assignment.sesion_aprendizaje as unknown as { inicio_en: string };
      const attempts = assignment.intento_ejercicio as unknown as Array<{
        es_correcta: boolean | null;
        tiempo_respuesta_ms: number | null;
        respondido_en: string;
        respuesta_final: { exercise_local_id?: string } | null;
      }>;
      return (attempts ?? []).map((attempt) => ({
        exercise_type: exercise.tipo_ejercicio,
        exercise_local_id: attempt.respuesta_final?.exercise_local_id ?? null,
        started_at: session.inicio_en,
        is_correct: attempt.es_correcta ?? false,
        elapsed_millis: attempt.tiempo_respuesta_ms ?? 0,
        recorded_at: attempt.respondido_en,
      }));
    });
    return new Response(JSON.stringify({ progress }), { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  }

  if (body.action === "download_teacher_assignments") {
    const studentId = typeof body.student_local_id === "string" ? body.student_local_id : "";
    if (!studentIds.includes(studentId)) {
      return new Response(JSON.stringify({ error: "El perfil no pertenece a esta cuenta" }), { status: 403, headers: corsHeaders });
    }
    const { data, error } = await authenticatedClient.rpc("responsable_refuerzos_pendientes", { p_estudiante: studentId });
    if (error) return new Response(JSON.stringify({ error: error.message }), { status: 422, headers: corsHeaders });
    const assignments = data ?? [];
    return new Response(JSON.stringify({ assignments }), { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  }

  const events = body.events;
  if (!Array.isArray(events) || events.length === 0 || events.length > 50) {
    return new Response(JSON.stringify({ error: "Lote de sincronización inválido" }), { status: 400, headers: corsHeaders });
  }

  // One transaction covers the entire batch, including plans and diagnostic reports.
  const { data, error } = await authenticatedClient.rpc("sincronizar_aprendizaje", { p_events: events });
  const knownErrors = ["El perfil no pertenece a esta cuenta", "La sesion no corresponde al perfil", "Informe de diagnostico invalido", "Actividad docente no disponible", "Plan de ejercicios invalido", "Lote de sincronizacion invalido"];
  return new Response(JSON.stringify(error ? {
    error: error.message,
    error_code: error.code,
    error_kind: knownErrors.includes(error.message) ? error.message : "otro",
  } : data), {
    status: error ? 422 : 200,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});
