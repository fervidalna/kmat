-- Agrega al reporte individual una serie cronologica de aciertos por sesion.
create or replace function public.docente_reporte_estudiante(p_id_estudiante uuid)
returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare v_reporte jsonb;
begin
  if not public.es_docente_de(p_id_estudiante) then
    raise exception 'El estudiante no pertenece a tus grupos' using errcode = '42501';
  end if;
  select jsonb_build_object(
    'id_estudiante', e.id_estudiante,
    'nombre', e.nombre_mostrado,
    'nivel', e.codigo_nivel_educativo,
    'sesiones', (select count(*) from public.sesion_aprendizaje s where s.id_estudiante = e.id_estudiante),
    'correctas', (select count(*) from public.intento_ejercicio i join public.asignacion_ejercicio a on a.id_asignacion = i.id_asignacion where a.id_estudiante = e.id_estudiante and i.es_correcta is true),
    'incorrectas', (select count(*) from public.intento_ejercicio i join public.asignacion_ejercicio a on a.id_asignacion = i.id_asignacion where a.id_estudiante = e.id_estudiante and i.es_correcta is false),
    'progreso_sesiones', coalesce((
      select jsonb_agg(jsonb_build_object(
        'fecha', resumen.inicio_en,
        'correctas', resumen.correctas,
        'total', resumen.total
      ) order by resumen.inicio_en)
      from (
        select s.id_sesion, s.inicio_en,
          count(i.id_intento) filter (where i.es_correcta is true) as correctas,
          count(i.id_intento) filter (where i.es_correcta is not null) as total
        from public.sesion_aprendizaje s
        join public.asignacion_ejercicio a on a.id_sesion = s.id_sesion
        join public.intento_ejercicio i on i.id_asignacion = a.id_asignacion
        where s.id_estudiante = e.id_estudiante
        group by s.id_sesion, s.inicio_en
        having count(i.id_intento) filter (where i.es_correcta is not null) > 0
      ) resumen
    ), '[]'::jsonb),
    'historial', coalesce((
      select jsonb_agg(jsonb_build_object(
        'actividad', c.nombre,
        'tipo', ev.tipo_ejercicio,
        'correcta', i.es_correcta,
        'respuesta', i.respuesta_reconocida,
        'duracion_ms', i.tiempo_respuesta_ms,
        'fecha', i.respondido_en
      ) order by i.respondido_en desc)
      from public.asignacion_ejercicio a
      join public.ejercicio_version ev on ev.id_ejercicio_version = a.id_ejercicio_version
      join public.contenido c on c.id_contenido = ev.id_contenido
      join public.intento_ejercicio i on i.id_asignacion = a.id_asignacion
      where a.id_estudiante = e.id_estudiante
    ), '[]'::jsonb)
  ) into v_reporte
  from public.estudiante e where e.id_estudiante = p_id_estudiante;
  return v_reporte;
end;
$$;

revoke all on function public.docente_reporte_estudiante(uuid) from public, anon;
grant execute on function public.docente_reporte_estudiante(uuid) to authenticated;
