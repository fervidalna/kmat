-- El saludo de Android usa siempre el nombre del perfil autenticado.
-- No sustituir un nombre ausente por la parte local del correo.

create or replace function public.mi_nombre_usuario()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select p.nombre_mostrado
  from public.perfil p
  where p.id_usuario = auth.uid()
  limit 1;
$$;

revoke all on function public.mi_nombre_usuario() from public;
grant execute on function public.mi_nombre_usuario() to authenticated;

-- Para registros nuevos, el nombre proviene de los datos enviados en el
-- formulario. Si faltara, usar un texto neutro, nunca el correo.
create or replace function public.crear_perfil_usuario_registrado()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  nombre text;
begin
  nombre := coalesce(
    nullif(left(trim(new.raw_user_meta_data ->> 'nombre_mostrado'), 100), ''),
    'Usuario'
  );

  insert into public.perfil (id_usuario, nombre_mostrado)
  values (new.id, nombre)
  on conflict (id_usuario) do nothing;

  if new.raw_user_meta_data ->> 'auto_rol' = 'RESPONSABLE' then
    insert into public.usuario_rol (id_usuario, codigo_rol)
    values (new.id, 'RESPONSABLE')
    on conflict (id_usuario, codigo_rol) do nothing;
  end if;

  return new;
end;
$$;

drop trigger if exists trg_auth_usuario_registrado on auth.users;
create trigger trg_auth_usuario_registrado
  after insert on auth.users
  for each row execute function public.crear_perfil_usuario_registrado();
