import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
};

type SyncEvent = {
  id: string;
  operation: "SESSION_STARTED" | "SESSION_COMPLETED" | "ATTEMPT_RECORDED";
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

  const body = await request.json() as { action?: string; student_local_id?: string; events?: SyncEvent[] };
  const admin = createClient(supabaseUrl, serviceRoleKey);
  const { data: relations, error: relationError } = await admin
    .from("responsable_estudiante")
    .select("id_estudiante")
    .eq("id_responsable", user.id)
    .eq("activo", true);
  if (relationError) return new Response(JSON.stringify({ error: relationError.message }), { status: 422, headers: corsHeaders });
  const studentIds = (relations ?? []).map((relation) => relation.id_estudiante as string);

  if (body.action === "download_progress") {
    if (studentIds.length === 0) {
      return new Response(JSON.stringify({ progress: [] }), { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } });
    }
    const { data, error } = await admin
      .from("asignacion_ejercicio")
      .select("id_estudiante,ejercicio_version!inner(tipo_ejercicio),sesion_aprendizaje!inner(inicio_en),intento_ejercicio(es_correcta,tiempo_respuesta_ms,respondido_en,respuesta_final)")
      .in("id_estudiante", studentIds)
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
    const { data, error } = await admin
      .from("asignacion_ejercicio")
      .select("id_asignacion,id_ejercicio_version,ejercicio_version!inner(id_ejercicio_version,enunciado,solucion,dificultad,contenido!inner(nombre))")
      .eq("id_estudiante", studentId)
      .eq("origen", "DOCENTE")
      .in("estado", ["PENDIENTE", "EN_CURSO"])
      .order("orden_cola", { ascending: true, nullsFirst: true });
    if (error) return new Response(JSON.stringify({ error: error.message }), { status: 422, headers: corsHeaders });
    const assignments = (data ?? []).map((assignment) => {
      const exercise = assignment.ejercicio_version as unknown as {
        id_ejercicio_version: string;
        enunciado: string;
        solucion: unknown;
        dificultad: number;
        contenido: { nombre: string };
      };
      return {
        assignment_id: assignment.id_asignacion,
        exercise_version_id: exercise.id_ejercicio_version,
        activity: exercise.contenido.nombre,
        statement: exercise.enunciado,
        solution: exercise.solucion,
        difficulty: exercise.dificultad,
      };
    });
    return new Response(JSON.stringify({ assignments }), { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  }

  const events = body.events;
  if (!Array.isArray(events) || events.length === 0 || events.length > 50) {
    return new Response(JSON.stringify({ error: "Lote de sincronización inválido" }), { status: 400, headers: corsHeaders });
  }

  for (const event of events) {
    const requestedStudentId = typeof event.payload.student_local_id === "string"
      ? event.payload.student_local_id
      : "";
    if (!studentIds.includes(requestedStudentId)) {
      return new Response(JSON.stringify({ error: "El evento no corresponde a un perfil vinculado a esta cuenta" }), { status: 403, headers: corsHeaders });
    }
    const studentId = requestedStudentId;

    if (event.operation === "SESSION_STARTED") {
      const { error } = await admin.from("sesion_aprendizaje").upsert({
        id_sesion: event.payload.local_session_id,
        id_estudiante: studentId,
        tipo_sesion: event.payload.session_type === "DIAGNOSTICO" ? "DIAGNOSTICO" : "PRACTICA",
        estado: "EN_CURSO",
        inicio_en: new Date(Number(event.payload.started_at)).toISOString(),
      }, { onConflict: "id_sesion" });
      if (error) return new Response(JSON.stringify({ error: error.message }), { status: 422, headers: corsHeaders });
    }

    if (event.operation === "SESSION_COMPLETED") {
      const { error } = await admin.from("sesion_aprendizaje").update({
        estado: "COMPLETADA",
        fin_en: new Date(Number(event.payload.ended_at)).toISOString(),
      })
        .eq("id_sesion", event.payload.local_session_id)
        .eq("id_estudiante", studentId);
      if (error) return new Response(JSON.stringify({ error: error.message }), { status: 422, headers: corsHeaders });
    }

    if (event.operation === "ATTEMPT_RECORDED") {
      const attemptId = String(event.payload.local_attempt_id);
      const teacherAssignmentId = typeof event.payload.teacher_assignment_id === "string"
        ? event.payload.teacher_assignment_id
        : "";
      const assignmentId = teacherAssignmentId || attemptId;
      const exerciseVersionId = String(event.payload.exercise_version_remote_id);
      const respondedAt = new Date(Number(event.payload.recorded_at)).toISOString();
      let assignmentData: { id_asignacion: string } | null = null;
      let assignmentError: { message: string } | null = null;
      if (teacherAssignmentId) {
        const result = await admin.from("asignacion_ejercicio").update({
          id_sesion: event.payload.local_session_id,
          estado: "COMPLETADA",
          iniciado_en: respondedAt,
          completado_en: respondedAt,
        }).eq("id_asignacion", assignmentId).eq("id_estudiante", studentId).eq("origen", "DOCENTE")
          .select("id_asignacion").maybeSingle()
        assignmentData = result.data;
        assignmentError = result.error;
      } else {
        const result = await admin.from("asignacion_ejercicio").upsert({
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
        assignmentError = result.error;
      }
      if (assignmentError) return new Response(JSON.stringify({ error: assignmentError.message }), { status: 422, headers: corsHeaders });
      if (teacherAssignmentId && !assignmentData) {
        return new Response(JSON.stringify({ error: "La actividad asignada ya no está disponible" }), { status: 409, headers: corsHeaders });
      }

      const { error: attemptError } = await admin.from("intento_ejercicio").upsert({
        id_intento: attemptId,
        id_asignacion: assignmentId,
        numero_intento: Number(event.payload.attempt_number),
        respuesta_reconocida: event.payload.answer,
        respuesta_final: {
          answer: event.payload.answer,
          strokes: event.payload.strokes,
          exercise_local_id: event.payload.exercise_local_id,
        },
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
