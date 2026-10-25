-- Operaciones seguras para grupos, estudiantes, reportes y refuerzos docentes.
begin;

create or replace function public.docente_listar_grupos()
returns table (id_grupo uuid, nombre text, codigo_union text, cantidad_estudiantes bigint)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not exists (select 1 from public.usuario_rol where id_usuario = auth.uid() and codigo_rol = 'DOCENTE') then
    raise exception 'Se requiere el rol DOCENTE' using errcode = '42501';
  end if;
  return query
  select g.id_grupo, g.nombre, g.codigo_union,
         count(distinct ig.id_estudiante) filter (where ig.estado = 'ACTIVA' and e.estado = 'ACTIVO')
  from public.grupo g
  join public.docente_grupo dg on dg.id_grupo = g.id_grupo and dg.id_docente = auth.uid() and dg.activo
  left join public.inscripcion_grupo ig on ig.id_grupo = g.id_grupo
  left join public.estudiante e on e.id_estudiante = ig.id_estudiante
  group by g.id_grupo
  order by g.nombre;
end;
$$;

create or replace function public.docente_crear_grupo(p_nombre text)
returns table (id_grupo uuid, nombre text, codigo_union text, cantidad_estudiantes bigint)
language plpgsql security definer set search_path = ''
as $$
declare
  v_nombre text := trim(coalesce(p_nombre, ''));
  v_codigo text;
  v_id uuid;
begin
  if not exists (select 1 from public.usuario_rol where id_usuario = auth.uid() and codigo_rol = 'DOCENTE') then
    raise exception 'Se requiere el rol DOCENTE' using errcode = '42501';
  end if;
  if char_length(v_nombre) not between 1 and 80 then
    raise exception 'El nombre debe tener entre 1 y 80 caracteres' using errcode = '22023';
  end if;
  loop
    v_codigo := 'KMAT-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 7));
    exit when not exists (select 1 from public.grupo where codigo_union = v_codigo);
  end loop;
  insert into public.grupo (nombre, codigo_union) values (v_nombre, v_codigo) returning grupo.id_grupo into v_id;
  insert into public.docente_grupo (id_docente, id_grupo, tipo_participacion, activo)
  values (auth.uid(), v_id, 'TITULAR', true);
  return query select v_id, v_nombre, v_codigo, 0::bigint;
end;
$$;

create or replace function public.docente_listar_estudiantes(p_id_grupo uuid)
returns table (id_estudiante uuid, nombre_mostrado text, codigo_nivel_educativo text)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not exists (
    select 1 from public.docente_grupo dg
    where dg.id_docente = auth.uid() and dg.id_grupo = p_id_grupo and dg.activo
  ) then
    raise exception 'No tienes acceso a este grupo' using errcode = '42501';
  end if;
  return query
  select e.id_estudiante, e.nombre_mostrado, e.codigo_nivel_educativo
  from public.inscripcion_grupo ig
  join public.estudiante e on e.id_estudiante = ig.id_estudiante
  where ig.id_grupo = p_id_grupo and ig.estado = 'ACTIVA' and e.estado = 'ACTIVO'
  order by e.nombre_mostrado;
end;
$$;

create or replace function public.docente_catalogo_refuerzos()
returns table (id_contenido uuid, nombre text, ejercicios_disponibles bigint)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not exists (select 1 from public.usuario_rol where id_usuario = auth.uid() and codigo_rol = 'DOCENTE') then
    raise exception 'Se requiere el rol DOCENTE' using errcode = '42501';
  end if;
  return query
  select c.id_contenido, c.nombre, count(ev.id_ejercicio_version)
  from public.contenido c
  join public.ejercicio_version ev on ev.id_contenido = c.id_contenido and ev.estado = 'PUBLICADO'
  group by c.id_contenido, c.nombre
  order by c.nombre;
end;
$$;

create or replace function public.docente_asignar_refuerzo(
  p_id_estudiante uuid,
  p_id_contenido uuid,
  p_cantidad integer,
  p_observacion text default null
)
returns integer
language plpgsql security definer set search_path = ''
as $$
declare
  v_id_refuerzo uuid;
  v_disponibles integer;
  v_asignados integer;
begin
  if not exists (select 1 from public.usuario_rol where id_usuario = auth.uid() and codigo_rol = 'DOCENTE') then
    raise exception 'Se requiere el rol DOCENTE' using errcode = '42501';
  end if;
  if p_cantidad not between 1 and 20 then
    raise exception 'La cantidad debe estar entre 1 y 20' using errcode = '22023';
  end if;
  if not public.es_docente_de(p_id_estudiante) then
    raise exception 'El estudiante no pertenece a tus grupos' using errcode = '42501';
  end if;
  select count(*) into v_disponibles
  from public.ejercicio_version ev
  where ev.id_contenido = p_id_contenido and ev.estado = 'PUBLICADO';
  if v_disponibles < p_cantidad then
    raise exception 'No hay suficientes ejercicios publicados para esta actividad' using errcode = '22023';
  end if;

  insert into public.refuerzo_docente (
    id_docente, id_estudiante, id_contenido, tipo_refuerzo,
    cantidad_ejercicios, estado, observacion
  ) values (
    auth.uid(), p_id_estudiante, p_id_contenido, 'MAS_EJERCICIOS',
    p_cantidad, 'ACTIVO', nullif(trim(coalesce(p_observacion, '')), '')
  ) returning id_refuerzo into v_id_refuerzo;

  insert into public.asignacion_ejercicio (
    id_estudiante, id_ejercicio_version, id_refuerzo, origen,
    prioridad, orden_cola, estado
  )
  select p_id_estudiante, elegidos.id_ejercicio_version, v_id_refuerzo,
         'DOCENTE', 1, elegidos.orden, 'PENDIENTE'
  from (
    select ev.id_ejercicio_version,
           row_number() over (order by ev.dificultad, ev.creado_en, ev.id_ejercicio_version)::integer as orden
    from public.ejercicio_version ev
    where ev.id_contenido = p_id_contenido and ev.estado = 'PUBLICADO'
    order by ev.dificultad, ev.creado_en, ev.id_ejercicio_version
    limit p_cantidad
  ) elegidos;
  get diagnostics v_asignados = row_count;
  if v_asignados <> p_cantidad then
    raise exception 'No se pudieron asignar todos los ejercicios' using errcode = '22023';
  end if;
  return v_asignados;
end;
$$;

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

create or replace function public.docente_reporte_grupo(p_id_grupo uuid)
returns table (
  id_estudiante uuid, nombre text, nivel text, sesiones bigint,
  correctas bigint, incorrectas bigint, ultima_actividad timestamptz
)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not exists (
    select 1 from public.docente_grupo dg
    where dg.id_docente = auth.uid() and dg.id_grupo = p_id_grupo and dg.activo
  ) then
    raise exception 'No tienes acceso a este grupo' using errcode = '42501';
  end if;
  return query
  select e.id_estudiante, e.nombre_mostrado, e.codigo_nivel_educativo,
    (select count(*) from public.sesion_aprendizaje s where s.id_estudiante = e.id_estudiante),
    (select count(*) from public.intento_ejercicio i join public.asignacion_ejercicio a on a.id_asignacion = i.id_asignacion where a.id_estudiante = e.id_estudiante and i.es_correcta is true),
    (select count(*) from public.intento_ejercicio i join public.asignacion_ejercicio a on a.id_asignacion = i.id_asignacion where a.id_estudiante = e.id_estudiante and i.es_correcta is false),
    (select max(i.respondido_en) from public.intento_ejercicio i join public.asignacion_ejercicio a on a.id_asignacion = i.id_asignacion where a.id_estudiante = e.id_estudiante)
  from public.inscripcion_grupo ig
  join public.estudiante e on e.id_estudiante = ig.id_estudiante
  where ig.id_grupo = p_id_grupo and ig.estado = 'ACTIVA' and e.estado = 'ACTIVO'
  order by e.nombre_mostrado;
end;
$$;

create or replace function public.responsable_unirse_grupo(p_id_estudiante uuid, p_codigo_union text)
returns text
language plpgsql security definer set search_path = ''
as $$
declare v_nombre_grupo text; v_id_grupo uuid;
begin
  if not exists (
    select 1 from public.usuario_rol where id_usuario = auth.uid() and codigo_rol = 'RESPONSABLE'
  ) then
    raise exception 'Se requiere el rol RESPONSABLE' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.responsable_estudiante re
    join public.estudiante e on e.id_estudiante = re.id_estudiante
    where re.id_responsable = auth.uid() and re.id_estudiante = p_id_estudiante
      and re.activo and e.estado = 'ACTIVO'
  ) then
    raise exception 'No tienes acceso a este perfil de estudiante' using errcode = '42501';
  end if;
  select g.id_grupo, g.nombre into v_id_grupo, v_nombre_grupo
  from public.grupo g where upper(g.codigo_union) = upper(trim(p_codigo_union)) and g.estado = 'ACTIVO';
  if v_id_grupo is null then
    raise exception 'El cÃ³digo no corresponde a una sala activa' using errcode = '22023';
  end if;
  if exists (
    select 1 from public.inscripcion_grupo where id_grupo = v_id_grupo
      and id_estudiante = p_id_estudiante and estado = 'ACTIVA'
  ) then
    raise exception 'Este estudiante ya pertenece a la sala' using errcode = '23505';
  end if;
  insert into public.inscripcion_grupo (id_grupo, id_estudiante, estado)
  values (v_id_grupo, p_id_estudiante, 'ACTIVA');
  return v_nombre_grupo;
end;
$$;

revoke all on function public.docente_listar_grupos() from public, anon;
revoke all on function public.docente_crear_grupo(text) from public, anon;
revoke all on function public.docente_listar_estudiantes(uuid) from public, anon;
revoke all on function public.docente_catalogo_refuerzos() from public, anon;
revoke all on function public.docente_asignar_refuerzo(uuid, uuid, integer, text) from public, anon;
revoke all on function public.docente_reporte_estudiante(uuid) from public, anon;
revoke all on function public.docente_reporte_grupo(uuid) from public, anon;
revoke all on function public.responsable_unirse_grupo(uuid, text) from public, anon;
grant execute on function public.docente_listar_grupos() to authenticated;
grant execute on function public.docente_crear_grupo(text) to authenticated;
grant execute on function public.docente_listar_estudiantes(uuid) to authenticated;
grant execute on function public.docente_catalogo_refuerzos() to authenticated;
grant execute on function public.docente_asignar_refuerzo(uuid, uuid, integer, text) to authenticated;
grant execute on function public.docente_reporte_estudiante(uuid) to authenticated;
grant execute on function public.docente_reporte_grupo(uuid) to authenticated;
grant execute on function public.responsable_unirse_grupo(uuid, text) to authenticated;

rollback;
