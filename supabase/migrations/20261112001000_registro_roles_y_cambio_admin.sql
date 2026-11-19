-- Permite el registro publico de responsables y docentes, y deja el cambio de
-- categoria bajo una RPC exclusiva para administradores.
begin;

create or replace function public.crear_perfil_usuario_registrado()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  nombre text;
  rol_solicitado text;
begin
  nombre := coalesce(
    nullif(left(trim(new.raw_user_meta_data ->> 'nombre_mostrado'), 100), ''),
    'Usuario'
  );

  insert into public.perfil (id_usuario, nombre_mostrado)
  values (new.id, nombre)
  on conflict (id_usuario) do nothing;

  rol_solicitado := new.raw_user_meta_data ->> 'auto_rol';
  if rol_solicitado in ('RESPONSABLE', 'DOCENTE') then
    insert into public.usuario_rol (id_usuario, codigo_rol)
    values (new.id, rol_solicitado)
    on conflict (id_usuario, codigo_rol) do nothing;
  end if;

  return new;
end;
$$;

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
