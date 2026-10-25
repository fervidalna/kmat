-- Borra definitivamente un perfil infantil y todos sus datos relacionados.
-- Solo la cuenta responsable vinculada puede solicitarlo. Los perfiles
-- compartidos requieren desvincular previamente a los otros responsables.
begin;

create or replace function public.borrar_datos_estudiante(p_id_estudiante uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_ids_auditoria uuid[];
begin
  -- Guardar los identificadores antes de borrar; los triggers de algunas
  -- tablas crean registros de auditoria durante esta misma transaccion.
  select array_agg(distinct relacionado.id)
  into v_ids_auditoria
  from (
    select p_id_estudiante as id
    union all select id_consentimiento from public.consentimiento where id_estudiante = p_id_estudiante
    union all select id_inscripcion from public.inscripcion_grupo where id_estudiante = p_id_estudiante
    union all select id_sesion from public.sesion_aprendizaje where id_estudiante = p_id_estudiante
    union all select id_refuerzo from public.refuerzo_docente where id_estudiante = p_id_estudiante
    union all select id_asignacion from public.asignacion_ejercicio where id_estudiante = p_id_estudiante
    union all select ie.id_intento from public.intento_ejercicio ie
      join public.asignacion_ejercicio a on a.id_asignacion = ie.id_asignacion
      where a.id_estudiante = p_id_estudiante
    union all select dd.id_deteccion from public.deteccion_dificultad dd
      join public.asignacion_ejercicio a on a.id_asignacion = dd.id_asignacion
      where a.id_estudiante = p_id_estudiante
    union all select ia.id_intervencion from public.intervencion_apoyo ia
      join public.asignacion_ejercicio a on a.id_asignacion = ia.id_asignacion
      where a.id_estudiante = p_id_estudiante
  ) relacionado;

  delete from public.intervencion_apoyo
  where id_asignacion in (select id_asignacion from public.asignacion_ejercicio where id_estudiante = p_id_estudiante);
  delete from public.intento_ejercicio
  where id_asignacion in (select id_asignacion from public.asignacion_ejercicio where id_estudiante = p_id_estudiante);
  delete from public.deteccion_dificultad
  where id_asignacion in (select id_asignacion from public.asignacion_ejercicio where id_estudiante = p_id_estudiante);
  delete from public.asignacion_ejercicio where id_estudiante = p_id_estudiante;
  delete from public.resultado_diagnostico where id_estudiante = p_id_estudiante;
  delete from public.sesion_aprendizaje where id_estudiante = p_id_estudiante;
  delete from public.refuerzo_docente where id_estudiante = p_id_estudiante;
  delete from public.inscripcion_grupo where id_estudiante = p_id_estudiante;
  delete from public.consentimiento where id_estudiante = p_id_estudiante;
  delete from public.responsable_estudiante where id_estudiante = p_id_estudiante;
  delete from public.estudiante where id_estudiante = p_id_estudiante;

  delete from public.auditoria
  where id_entidad = any(v_ids_auditoria)
     or datos_anteriores ->> 'id_estudiante' = p_id_estudiante::text
     or datos_nuevos ->> 'id_estudiante' = p_id_estudiante::text;
end;
$$;

revoke all on function public.borrar_datos_estudiante(uuid) from public, anon, authenticated;

create or replace function public.eliminar_mi_estudiante(p_id_estudiante uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_responsable uuid := auth.uid();
begin
  if v_responsable is null or not exists (
    select 1 from public.usuario_rol ur
    join public.perfil p on p.id_usuario = ur.id_usuario
    where ur.id_usuario = v_responsable
      and ur.codigo_rol = 'RESPONSABLE' and p.estado = 'ACTIVO'
  ) then
    raise exception 'Se requiere una cuenta responsable activa' using errcode = '42501';
  end if;

  perform e.id_estudiante
  from public.estudiante e
  join public.responsable_estudiante re on re.id_estudiante = e.id_estudiante
  where e.id_estudiante = p_id_estudiante
    and re.id_responsable = v_responsable and re.activo
  for update of e;
  if not found then
    raise exception 'No tienes permiso para eliminar este perfil' using errcode = '42501';
  end if;

  if exists (
    select 1 from public.responsable_estudiante re
    where re.id_estudiante = p_id_estudiante
      and re.id_responsable <> v_responsable and re.activo
  ) then
    raise exception 'El perfil esta compartido con otro responsable' using errcode = 'KM001';
  end if;

  perform public.borrar_datos_estudiante(p_id_estudiante);
  return true;
end;
$$;

revoke all on function public.eliminar_mi_estudiante(uuid) from public, anon;
grant execute on function public.eliminar_mi_estudiante(uuid) to authenticated;

-- Eliminar tambien los perfiles que el boton anterior dejo desactivados.
-- La marca de auditoria identifica precisamente esas bajas anteriores.
do $$
declare
  v_id uuid;
begin
  for v_id in
    select e.id_estudiante
    from public.estudiante e
    where e.estado = 'INACTIVO'
      and not exists (
        select 1 from public.responsable_estudiante re
        where re.id_estudiante = e.id_estudiante and re.activo
      )
      and exists (
        select 1 from public.auditoria a
        where a.id_entidad = e.id_estudiante
          and a.datos_nuevos ->> 'motivo' = 'ELIMINADO_POR_RESPONSABLE'
      )
  loop
    perform public.borrar_datos_estudiante(v_id);
  end loop;
end;
$$;

rollback;
