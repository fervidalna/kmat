-- Repara cuentas Auth sin perfil al cambiar su tipo desde administración.
-- La fila de perfil debe existir antes de insertar en usuario_rol por su FK.
begin;

create or replace function public.cambiar_rol_cuenta_admin(
  p_id_usuario uuid,
  p_codigo_rol text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin uuid := auth.uid();
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

  if p_codigo_rol not in ('DOCENTE', 'RESPONSABLE') then
    raise exception 'El rol solicitado no se puede asignar desde esta operación' using errcode = '22023';
  end if;

  if p_id_usuario = v_admin then
    raise exception 'No puedes cambiar el rol de tu propia cuenta administradora' using errcode = '42501';
  end if;

  if not exists (select 1 from auth.users u where u.id = p_id_usuario) then
    return false;
  end if;

  if exists (
    select 1 from public.usuario_rol ur
    where ur.id_usuario = p_id_usuario and ur.codigo_rol = 'ADMINISTRADOR'
  ) then
    raise exception 'No se puede cambiar el rol de otra cuenta administradora' using errcode = '42501';
  end if;

  -- Algunas cuentas se crearon antes de que el trigger de registro quedara
  -- instalado. Recuperar el nombre de metadata Auth y completar su perfil.
  insert into public.perfil (id_usuario, nombre_mostrado)
  select
    u.id,
    coalesce(
      nullif(left(trim(u.raw_user_meta_data ->> 'nombre_mostrado'), 100), ''),
      'Usuario'
    )
  from auth.users u
  where u.id = p_id_usuario
  on conflict (id_usuario) do nothing;

  delete from public.usuario_rol
  where id_usuario = p_id_usuario
    and codigo_rol in ('DOCENTE', 'RESPONSABLE');

  insert into public.usuario_rol (id_usuario, codigo_rol)
  values (p_id_usuario, p_codigo_rol);

  return true;
end;
$$;

revoke all on function public.cambiar_rol_cuenta_admin(uuid, text) from public, anon;
grant execute on function public.cambiar_rol_cuenta_admin(uuid, text) to authenticated;

commit;
