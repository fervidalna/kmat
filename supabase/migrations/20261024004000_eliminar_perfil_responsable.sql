-- Eliminar de la cuenta = desactivar su vinculo. El historial no se borra.
-- Si no quedan responsables activos, el estudiante queda INACTIVO.
begin;

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

  -- Bloquear el estudiante serializa eliminaciones de responsables compartidos.
  -- Un id ajeno o inexistente recibe el mismo error, sin revelar su existencia.
  perform e.id_estudiante
  from public.estudiante e
  join public.responsable_estudiante re on re.id_estudiante = e.id_estudiante
  where e.id_estudiante = p_id_estudiante and re.id_responsable = v_responsable
  for update of e;
  if not found then
    raise exception 'No tienes permiso para eliminar este perfil' using errcode = '42501';
  end if;

  update public.responsable_estudiante
  set activo = false, es_principal = false
  where id_estudiante = p_id_estudiante and id_responsable = v_responsable and activo;

  -- Reintentar tras perder la respuesta es seguro y no duplica la auditoria.
  if not found then
    return true;
  end if;

  if not exists (
    select 1 from public.responsable_estudiante re
    where re.id_estudiante = p_id_estudiante and re.activo
  ) then
    update public.estudiante set estado = 'INACTIVO'
    where id_estudiante = p_id_estudiante and estado = 'ACTIVO';
  end if;

  insert into public.auditoria (id_actor, entidad, id_entidad, accion, datos_anteriores, datos_nuevos)
  values (
    v_responsable, 'responsable_estudiante', p_id_estudiante, 'UPDATE',
    jsonb_build_object('id_responsable', v_responsable, 'activo', true),
    jsonb_build_object('id_responsable', v_responsable, 'activo', false, 'motivo', 'ELIMINADO_POR_RESPONSABLE')
  );
  return true;
end;
$$;

revoke all on function public.eliminar_mi_estudiante(uuid) from public, anon;
grant execute on function public.eliminar_mi_estudiante(uuid) to authenticated;

commit;
