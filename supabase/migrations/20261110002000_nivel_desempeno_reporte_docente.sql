-- Añade el nivel de desempeño del diagnóstico inicial al reporte individual docente.
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
    'nivel_desempeno', (
      select case (s.informe_diagnostico->>'overallLevel')::integer
        when 3 then 'Avanzado'
        when 2 then 'En desarrollo'
        when 1 then 'Nivel inicial'
        else null
      end
      from public.sesion_aprendizaje s
      where s.id_estudiante = e.id_estudiante
        and s.tipo_sesion = 'DIAGNOSTICO'
        and s.informe_diagnostico is not null
      order by s.inicio_en desc
      limit 1
    ),
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
        'area', coalesce(
          nullif(i.respuesta_final #>> '{exercise,axis}', ''),
          case
            when c.codigo like 'CONTEO%' or c.codigo like 'SUMA%'
              or ev.tipo_ejercicio in ('CONTEO', 'CONTEO_SALTOS', 'SUMA', 'RESTA')
              then 'NUMEROS_OPERACIONES'
            when c.codigo like '%PATRON%' or ev.tipo_ejercicio in ('PATRON', 'SECUENCIA')
              then 'PATRONES_ALGEBRA'
            when c.codigo like '%GEOMETRIA%' or c.codigo like '%FIGURA%'
              then 'GEOMETRIA'
            when c.codigo like '%MEDIC%' or c.codigo like '%LONGITUD%'
              then 'MEDICION'
            when c.codigo like '%DATOS%' or c.codigo like '%PROBABIL%'
              then 'DATOS_PROBABILIDAD'
            else null
          end
        ),
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