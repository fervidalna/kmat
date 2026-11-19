begin;

create or replace function public.docente_descargar_aprendizaje(p_student_id uuid, p_after uuid default null)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare result jsonb;
begin
  if auth.uid() is null or not exists (
    select 1 from public.usuario_rol ur
    join public.perfil p on p.id_usuario = ur.id_usuario
    where ur.id_usuario = auth.uid() and ur.codigo_rol = 'DOCENTE' and p.estado = 'ACTIVO'
  ) or not exists (
    select 1 from public.docente_grupo dg
    join public.grupo g on g.id_grupo = dg.id_grupo and g.estado = 'ACTIVO'
    join public.inscripcion_grupo ig on ig.id_grupo = g.id_grupo and ig.estado = 'ACTIVA'
    join public.estudiante e on e.id_estudiante = ig.id_estudiante and e.estado = 'ACTIVO'
    where dg.id_docente = auth.uid() and dg.activo and e.id_estudiante = p_student_id
  ) then
    raise exception 'El estudiante no pertenece a tus grupos' using errcode = '42501';
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


revoke all on function public.docente_descargar_aprendizaje(uuid, uuid) from public, anon;
grant execute on function public.docente_descargar_aprendizaje(uuid, uuid) to authenticated;

commit;
