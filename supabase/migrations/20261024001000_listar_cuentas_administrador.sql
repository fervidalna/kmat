-- Permite que la app consulte cuentas solo a una sesion con rol administrador.
-- No habilita altas, cambios de rol ni desactivaciones desde el cliente.
create or replace function public.listar_cuentas_admin()
returns table (
  id_usuario uuid,
  nombre_mostrado text,
  correo text,
  roles text,
  estado text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not exists (
    select 1
    from public.usuario_rol ur
    where ur.id_usuario = auth.uid()
      and ur.codigo_rol = 'ADMINISTRADOR'
  ) then
    raise exception 'Se requiere el rol ADMINISTRADOR' using errcode = '42501';
  end if;

  return query
  select u.id,
         coalesce(p.nombre_mostrado, 'Usuario'),
         coalesce(u.email, '')::text,
         coalesce(string_agg(ur.codigo_rol, ', ' order by ur.codigo_rol), ''),
         coalesce(p.estado, 'SIN PERFIL')
  from auth.users u
  left join public.perfil p on p.id_usuario = u.id
  left join public.usuario_rol ur on ur.id_usuario = u.id
  group by u.id, u.created_at, p.nombre_mostrado, u.email, p.estado
  order by u.created_at desc, coalesce(p.nombre_mostrado, 'Usuario');
end;
$$;

revoke all on function public.listar_cuentas_admin() from public;
grant execute on function public.listar_cuentas_admin() to authenticated;
