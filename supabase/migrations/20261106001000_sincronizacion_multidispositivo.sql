-- The session owns its exercise plan and diagnostic report. Completed sessions
-- and immutable attempts can be replayed safely after a lost HTTP response.
begin;
alter table public.sesion_aprendizaje
  add column plan_ejercicios jsonb,
  add column informe_diagnostico jsonb;

create or replace function public.sincronizar_aprendizaje(p_events jsonb)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  e jsonb;
  p jsonb;
  student_id uuid;
  session_id uuid;
  assignment_id uuid;
  attempt_id uuid;
  answered_at timestamptz;
  s public.sesion_aprendizaje;
begin
  if auth.uid() is null or jsonb_typeof(p_events) <> 'array'
    or jsonb_array_length(p_events) not between 1 and 50 then
    raise exception 'Lote de sincronizacion invalido';
  end if;
  for e in select value from jsonb_array_elements(p_events) loop
    p := e->'payload';
    student_id := (p->>'student_local_id')::uuid;
    session_id := (p->>'local_session_id')::uuid;
    if not public.es_responsable_de(student_id) then
      raise exception 'El perfil no pertenece a esta cuenta';
    end if;

    if e->>'operation' = 'SESSION_STARTED' then
      insert into public.sesion_aprendizaje(id_sesion, id_estudiante, tipo_sesion, inicio_en)
      values(session_id, student_id,
        case when p->>'session_type' = 'DIAGNOSTICO' then 'DIAGNOSTICO' else 'PRACTICA' end,
        to_timestamp((p->>'started_at')::double precision / 1000))
      on conflict (id_sesion) do nothing;
    end if;

    select * into s from public.sesion_aprendizaje where id_sesion = session_id for update;
    if not found or s.id_estudiante <> student_id then
      raise exception 'La sesion no corresponde al perfil';
    end if;

    case e->>'operation'
    when 'SESSION_STARTED' then null;
    when 'SESSION_PLANNED' then
      if jsonb_typeof(p->'exercises') <> 'array' or jsonb_array_length(p->'exercises') not between 1 and 18 then
        raise exception 'Plan de ejercicios invalido';
      end if;
      -- The first plan wins; a delayed device cannot replace another device's plan.
      update public.sesion_aprendizaje set plan_ejercicios = p->'exercises'
      where id_sesion = session_id and plan_ejercicios is null;
    when 'SESSION_COMPLETED' then
      update public.sesion_aprendizaje
      set estado = 'COMPLETADA', fin_en = greatest(inicio_en, to_timestamp((p->>'ended_at')::double precision / 1000))
      where id_sesion = session_id and estado <> 'COMPLETADA';
    when 'DIAGNOSTIC_RECORDED' then
      if s.tipo_sesion <> 'DIAGNOSTICO' or s.estado <> 'COMPLETADA'
        or jsonb_typeof(p->'report') <> 'object'
        or (p->'report'->>'questionCount')::integer not between 10 and 18
        or (p->'report'->>'overallLevel')::integer not between 1 and 3
        or jsonb_array_length(p->'report'->'axes') <> 5 then
        raise exception 'Informe de diagnostico invalido';
      end if;
      update public.sesion_aprendizaje set informe_diagnostico = p->'report'
      where id_sesion = session_id and informe_diagnostico is null;
    when 'ATTEMPT_RECORDED' then
      attempt_id := (p->>'local_attempt_id')::uuid;
      assignment_id := coalesce(nullif(p->>'teacher_assignment_id', '')::uuid, attempt_id);
      answered_at := to_timestamp((p->>'recorded_at')::double precision / 1000);
      -- Check ownership even on replays; never let a conflicting ID mutate another profile.
      if exists(select 1 from public.asignacion_ejercicio where id_asignacion = assignment_id and id_estudiante <> student_id)
        or exists(select 1 from public.intento_ejercicio where id_intento = attempt_id and id_asignacion <> assignment_id) then
        raise exception 'El intento no corresponde al perfil';
      end if;
      if exists(select 1 from public.intento_ejercicio where id_intento = attempt_id) then
        continue;
      end if;
      if nullif(p->>'teacher_assignment_id', '') is not null then
        update public.asignacion_ejercicio set id_sesion = session_id, estado = 'COMPLETADA',
          iniciado_en = greatest(asignado_en, answered_at), completado_en = greatest(asignado_en, answered_at)
        where id_asignacion = assignment_id and id_estudiante = student_id and origen = 'DOCENTE';
        if not found then raise exception 'Actividad docente no disponible'; end if;
      else
        insert into public.asignacion_ejercicio(id_asignacion, id_estudiante, id_ejercicio_version,
          id_sesion, origen, estado, asignado_en, iniciado_en, completado_en)
        values(assignment_id, student_id, (p->>'exercise_version_remote_id')::uuid, session_id,
          case when s.tipo_sesion = 'DIAGNOSTICO' then 'DIAGNOSTICO' else 'MOTOR' end,
          'COMPLETADA', answered_at, answered_at, answered_at)
        on conflict (id_asignacion) do nothing;
      end if;
      insert into public.intento_ejercicio(id_intento, id_asignacion, numero_intento,
        respuesta_reconocida, respuesta_final, es_correcta, tiempo_respuesta_ms, respondido_en)
      values(attempt_id, assignment_id, (p->>'attempt_number')::integer, p->>'answer',
        jsonb_build_object('answer', p->'answer', 'strokes', p->'strokes',
          'exercise_local_id', p->'exercise_local_id', 'exercise', p->'exercise'),
        (p->>'is_correct')::boolean, (p->>'elapsed_millis')::integer, answered_at)
      on conflict do nothing;
    else
      raise exception 'Operacion de sincronizacion desconocida';
    end case;
  end loop;
  return jsonb_build_object('synchronized', jsonb_array_length(p_events));
end;
$$;

create or replace function public.descargar_aprendizaje(p_student_id uuid, p_after uuid default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare result jsonb;
begin
  if auth.uid() is null or not public.es_responsable_de(p_student_id) then
    raise exception 'El perfil no pertenece a esta cuenta';
  end if;
  -- Keyset pagination avoids the REST row limit and timestamps supplied by devices.
  select coalesce(jsonb_agg(row_data order by id_sesion), '[]'::jsonb) into result
  from (
    select s.id_sesion, jsonb_build_object(
      'id', s.id_sesion, 'studentId', s.id_estudiante, 'sessionType', s.tipo_sesion,
      'startedAt', (extract(epoch from s.inicio_en) * 1000)::bigint,
      'endedAt', (extract(epoch from s.fin_en) * 1000)::bigint,
      'status', case when s.estado = 'COMPLETADA' then 'COMPLETED' else 'ACTIVE' end,
      'plan', s.plan_ejercicios, 'report', s.informe_diagnostico,
      'attempts', coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', i.id_intento, 'exerciseId', coalesce(i.respuesta_final->>'exercise_local_id', a.id_asignacion::text),
          'answer', coalesce(i.respuesta_final->>'answer', i.respuesta_reconocida, ''),
          'isCorrect', coalesce(i.es_correcta, false), 'attemptNumber', i.numero_intento,
          'elapsedMillis', coalesce(i.tiempo_respuesta_ms, 0),
          'createdAt', (extract(epoch from i.respondido_en) * 1000)::bigint,
          'exercise', i.respuesta_final->'exercise'
        ) order by i.respondido_en, i.id_intento)
        from public.asignacion_ejercicio a join public.intento_ejercicio i using(id_asignacion)
        where a.id_sesion = s.id_sesion and a.id_estudiante = p_student_id
      ), '[]'::jsonb)
    ) row_data
    from public.sesion_aprendizaje s
    where s.id_estudiante = p_student_id and (p_after is null or s.id_sesion > p_after)
    order by s.id_sesion limit 50
  ) page;
  return jsonb_build_object('sessions', result);
end;
$$;

revoke all on function public.sincronizar_aprendizaje(jsonb) from public, anon;
revoke all on function public.descargar_aprendizaje(uuid, uuid) from public, anon;
grant execute on function public.sincronizar_aprendizaje(jsonb) to authenticated;
grant execute on function public.descargar_aprendizaje(uuid, uuid) to authenticated;

create or replace function public.retomar_sesion_aprendizaje(
  p_student_id uuid, p_session_type text, p_session_id uuid, p_started_at bigint
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare selected_id uuid;
begin
  if auth.uid() is null or not public.es_responsable_de(p_student_id)
    or p_session_type not in ('DIAGNOSTICO', 'PRACTICA') then
    raise exception 'Perfil o tipo de sesion no autorizado';
  end if;
  -- Concurrent online devices reserve the same session before choosing its plan.
  perform pg_advisory_xact_lock(hashtextextended(p_student_id::text || p_session_type, 0));
  select id_sesion into selected_id from public.sesion_aprendizaje
  where id_estudiante = p_student_id and tipo_sesion = p_session_type and estado = 'EN_CURSO'
  order by inicio_en, id_sesion limit 1;
  if selected_id is null then
    insert into public.sesion_aprendizaje(id_sesion, id_estudiante, tipo_sesion, inicio_en)
    values(p_session_id, p_student_id, p_session_type, to_timestamp(p_started_at::double precision / 1000));
    selected_id := p_session_id;
  end if;
  return jsonb_build_object('session_id', selected_id);
end;
$$;
revoke all on function public.retomar_sesion_aprendizaje(uuid, text, uuid, bigint) from public, anon;
grant execute on function public.retomar_sesion_aprendizaje(uuid, text, uuid, bigint) to authenticated;
commit;
