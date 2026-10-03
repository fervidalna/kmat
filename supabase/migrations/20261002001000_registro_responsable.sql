-- K-MAT IA · Registro público seguro de responsables
-- Ejecutar una vez después del esquema principal.
-- Las altas desde Android solo pueden recibir el rol RESPONSABLE.

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
    nullif(left(split_part(coalesce(new.email, ''), '@', 1), 100), ''),
    'Usuario'
  );

  insert into public.perfil (id_usuario, nombre_mostrado)
  values (new.id, nombre)
  on conflict (id_usuario) do nothing;

  -- El cliente solo puede solicitar este rol. Docentes y administradores se
  -- asignan mediante el flujo administrativo protegido.
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
