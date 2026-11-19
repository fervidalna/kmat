begin;

create or replace function public.eje_refuerzo(p_codigo text, p_tipo text)
returns text language sql immutable set search_path = '' as $$
  select case
    when upper(p_codigo) like '%PATRON%' then 'PATRONES_ALGEBRA'
    when upper(p_codigo) like '%GEOMETRIA%' or upper(p_codigo) like '%FIGURA%' then 'GEOMETRIA'
    when upper(p_codigo) like '%MEDIC%' or upper(p_codigo) like '%LONGITUD%' then 'MEDICION'
    when upper(p_codigo) like '%DATOS%' or upper(p_codigo) like '%PROBABIL%' then 'DATOS_PROBABILIDAD'
    when upper(p_codigo) like 'CONTEO%' or upper(p_codigo) like 'SUMA%' or upper(p_codigo) like 'RESTA%'
      then 'NUMEROS_OPERACIONES'
    else null end;
$$;

create or replace function public.docente_catalogo_refuerzos_nivel()
returns table(id_contenido uuid, nombre text, eje text, dificultad integer, ejercicios_disponibles bigint)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not exists (select 1 from public.usuario_rol ur join public.perfil p on p.id_usuario=ur.id_usuario
                 where ur.id_usuario=auth.uid() and ur.codigo_rol='DOCENTE' and p.estado='ACTIVO') then
    raise exception 'Se requiere una cuenta docente activa' using errcode='42501';
  end if;
  return query select c.id_contenido, c.nombre, public.eje_refuerzo(c.codigo, ev.tipo_ejercicio),
    ev.dificultad::integer - 1, count(*)
  from public.contenido c join public.ejercicio_version ev on ev.id_contenido=c.id_contenido
  where c.activo and ev.estado='PUBLICADO' and ev.dificultad between 1 and 3
    and public.eje_refuerzo(c.codigo, ev.tipo_ejercicio) is not null
  group by c.id_contenido,c.nombre,public.eje_refuerzo(c.codigo,ev.tipo_ejercicio),ev.dificultad
  order by c.nombre,ev.dificultad;
end;
$$;

create or replace function public.docente_asignar_refuerzo_nivel(p_id_estudiante uuid, p_id_contenido uuid, p_eje text, p_dificultad integer, p_cantidad integer, p_observacion text default null)
returns integer language plpgsql security definer set search_path = '' as $$
declare v_refuerzo uuid; v_disponibles integer; v_asignados integer;
begin
  if not exists (select 1 from public.usuario_rol ur join public.perfil p on p.id_usuario=ur.id_usuario
                 where ur.id_usuario=auth.uid() and ur.codigo_rol='DOCENTE' and p.estado='ACTIVO') or not exists (
    select 1 from public.docente_grupo dg join public.grupo g on g.id_grupo=dg.id_grupo and g.estado='ACTIVO'
    join public.inscripcion_grupo ig on ig.id_grupo=g.id_grupo and ig.estado='ACTIVA'
    join public.estudiante e on e.id_estudiante=ig.id_estudiante and e.estado='ACTIVO'
    where dg.id_docente=auth.uid() and dg.activo and e.id_estudiante=p_id_estudiante
  ) then raise exception 'Estudiante no autorizado' using errcode='42501'; end if;
  if p_cantidad is null or p_dificultad is null or p_eje is null or p_cantidad not between 1 and 20 or p_dificultad not between 0 and 2 then
    raise exception 'Cantidad o nivel no válido' using errcode='22023'; end if;
  select count(*) into v_disponibles from public.ejercicio_version ev
    join public.contenido c on c.id_contenido=ev.id_contenido
    where c.id_contenido=p_id_contenido and c.activo and ev.estado='PUBLICADO'
      and ev.dificultad=p_dificultad+1 and public.eje_refuerzo(c.codigo,ev.tipo_ejercicio)=p_eje;
  if v_disponibles < p_cantidad then raise exception 'No hay suficientes ejercicios disponibles' using errcode='22023'; end if;
  insert into public.refuerzo_docente(id_docente,id_estudiante,id_contenido,tipo_refuerzo,cantidad_ejercicios,estado,observacion)
    values(auth.uid(),p_id_estudiante,p_id_contenido,'MAS_EJERCICIOS',p_cantidad,'ACTIVO',nullif(trim(coalesce(p_observacion,'')),'')) returning id_refuerzo into v_refuerzo;
  insert into public.asignacion_ejercicio(id_estudiante,id_ejercicio_version,id_refuerzo,origen,prioridad,orden_cola,estado)
    select p_id_estudiante, items.id_ejercicio_version,v_refuerzo,'DOCENTE',1,items.orden,'PENDIENTE'
    from (select ev.id_ejercicio_version,row_number() over (order by ev.creado_en,ev.id_ejercicio_version)::integer as orden
      from public.ejercicio_version ev join public.contenido c on c.id_contenido=ev.id_contenido
      where c.id_contenido=p_id_contenido and c.activo and ev.estado='PUBLICADO' and ev.dificultad=p_dificultad+1
        and public.eje_refuerzo(c.codigo,ev.tipo_ejercicio)=p_eje
      order by ev.creado_en,ev.id_ejercicio_version limit p_cantidad) items;
  get diagnostics v_asignados = row_count;
  if v_asignados <> p_cantidad then raise exception 'No se pudieron asignar todos los ejercicios'; end if;
  return v_asignados;
end;
$$;

create or replace function public.responsable_refuerzos_pendientes(p_estudiante uuid)
returns table(assignment_id uuid, exercise_version_id uuid, activity text, statement text, solution jsonb, difficulty integer, axis text)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null or not public.es_responsable_de(p_estudiante) then
    raise exception 'Perfil no autorizado' using errcode='42501'; end if;
  return query select a.id_asignacion,ev.id_ejercicio_version,c.nombre,ev.enunciado,ev.solucion,
      ev.dificultad::integer - 1,public.eje_refuerzo(c.codigo,ev.tipo_ejercicio)
    from public.asignacion_ejercicio a join public.ejercicio_version ev on ev.id_ejercicio_version=a.id_ejercicio_version
    join public.contenido c on c.id_contenido=ev.id_contenido
    where a.id_estudiante=p_estudiante and a.origen='DOCENTE' and a.estado in ('PENDIENTE','EN_CURSO')
    order by a.orden_cola nulls last,a.asignado_en,a.id_asignacion;
end;
$$;

revoke all on function public.eje_refuerzo(text,text) from public,anon,authenticated;
revoke all on function public.docente_catalogo_refuerzos_nivel() from public,anon;
revoke all on function public.docente_asignar_refuerzo_nivel(uuid,uuid,text,integer,integer,text) from public,anon;
revoke all on function public.responsable_refuerzos_pendientes(uuid) from public,anon;
grant execute on function public.docente_catalogo_refuerzos_nivel() to authenticated;
grant execute on function public.docente_asignar_refuerzo_nivel(uuid,uuid,text,integer,integer,text) to authenticated;
grant execute on function public.responsable_refuerzos_pendientes(uuid) to authenticated;
commit;
