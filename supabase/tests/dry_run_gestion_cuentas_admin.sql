-- El administrador puede eliminar cuentas completas y sus datos asociados.
-- La operacion se ejecuta en una sola transaccion dentro de PostgreSQL.
begin;
create or replace function public.eliminar_cuenta_admin(p_id_usuario uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin uuid := auth.uid();
  v_estudiantes uuid[];
  v_asignaciones uuid[];
  v_registros uuid[];
begin
  if v_admin is null or not exists (
    select 1
    from public.usuario_rol ur
    join public.perfil p on p.id_usuario = ur.id_usuario
    where ur.id_usuario = v_admin
      and ur.codigo_rol = 'ADMINISTRADOR'
      and p.estado = 'ACTIVO'
  ) then
    raise exception 'Se requiere una cuenta administradora activa' using errcode = '42501';
  end if;

  if p_id_usuario = v_admin then
    raise exception 'No puedes eliminar tu propia cuenta administradora' using errcode = '42501';
  end if;

  if not exists (select 1 from auth.users u where u.id = p_id_usuario) then
    return false;
  end if;

  if exists (
       select 1 from public.usuario_rol ur
       join public.perfil p on p.id_usuario = ur.id_usuario
       where ur.id_usuario = p_id_usuario and ur.codigo_rol = 'ADMINISTRADOR'
         and p.estado = 'ACTIVO'
     )
     and (select count(*) from public.usuario_rol ur
          join public.perfil p on p.id_usuario = ur.id_usuario
          where ur.codigo_rol = 'ADMINISTRADOR' and p.estado = 'ACTIVO') <= 1 then
    raise exception 'No se puede eliminar la ultima cuenta administradora' using errcode = '23514';
  end if;

  select array_agg(distinct re.id_estudiante)
  into v_estudiantes
  from public.responsable_estudiante re
  where re.id_responsable = p_id_usuario;

  delete from public.consentimiento where id_responsable = p_id_usuario;
  delete from public.responsable_estudiante where id_responsable = p_id_usuario;

  -- Si otro responsable conserva cualquier vinculo, conserva tambien el perfil.
  if v_estudiantes is not null then
    perform public.borrar_datos_estudiante(e.id_estudiante)
    from public.estudiante e
    where e.id_estudiante = any(v_estudiantes)
      and not exists (
        select 1 from public.responsable_estudiante re
        where re.id_estudiante = e.id_estudiante
      );
  end if;

  select array_agg(distinct a.id_asignacion)
  into v_asignaciones
  from public.asignacion_ejercicio a
  join public.refuerzo_docente r on r.id_refuerzo = a.id_refuerzo
  where r.id_docente = p_id_usuario;

  select array_agg(distinct relacionado.id)
  into v_registros
  from (
    select r.id_refuerzo as id from public.refuerzo_docente r where r.id_docente = p_id_usuario
    union all select unnest(v_asignaciones)
    union all select ie.id_intento from public.intento_ejercicio ie where ie.id_asignacion = any(coalesce(v_asignaciones, array[]::uuid[]))
    union all select dd.id_deteccion from public.deteccion_dificultad dd where dd.id_asignacion = any(coalesce(v_asignaciones, array[]::uuid[]))
    union all select ia.id_intervencion from public.intervencion_apoyo ia where ia.id_asignacion = any(coalesce(v_asignaciones, array[]::uuid[]))
  ) relacionado;

  delete from public.intervencion_apoyo where id_asignacion = any(coalesce(v_asignaciones, array[]::uuid[]));
  delete from public.intento_ejercicio where id_asignacion = any(coalesce(v_asignaciones, array[]::uuid[]));
  delete from public.deteccion_dificultad where id_asignacion = any(coalesce(v_asignaciones, array[]::uuid[]));
  delete from public.asignacion_ejercicio where id_asignacion = any(coalesce(v_asignaciones, array[]::uuid[]));
  delete from public.refuerzo_docente where id_docente = p_id_usuario;
  delete from public.docente_grupo where id_docente = p_id_usuario;

  delete from public.auditoria
  where id_actor = p_id_usuario
     or id_entidad = any(coalesce(v_registros, array[]::uuid[]))
     or datos_anteriores ->> 'id_usuario' = p_id_usuario::text
     or datos_nuevos ->> 'id_usuario' = p_id_usuario::text
     or datos_anteriores ->> 'id_responsable' = p_id_usuario::text
     or datos_nuevos ->> 'id_responsable' = p_id_usuario::text
     or datos_anteriores ->> 'id_docente' = p_id_usuario::text
     or datos_nuevos ->> 'id_docente' = p_id_usuario::text;

  -- auth.users elimina en cascada el perfil, los roles y las sesiones de acceso.
  delete from auth.users where id = p_id_usuario;
  return found;
end;
$$;

revoke all on function public.eliminar_cuenta_admin(uuid) from public, anon;
grant execute on function public.eliminar_cuenta_admin(uuid) to authenticated;

rollback;
