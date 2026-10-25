-- Prueba la eliminacion completa en fixtures y revierte todos los cambios.
begin;
insert into public.rol (codigo, descripcion)
values ('ADMINISTRADOR', 'Administrador de prueba')
on conflict (codigo) do nothing;

create temporary table prueba_cuenta_admin (
  admin uuid, cuenta uuid, responsable uuid, otro_responsable uuid,
  propio uuid, compartido uuid
) on commit drop;
insert into prueba_cuenta_admin
values (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid());
grant select on prueba_cuenta_admin to authenticated;

insert into auth.users (id, email, raw_user_meta_data)
select admin, 'prueba-admin-' || admin || '@example.invalid', '{"nombre_mostrado":"Admin prueba"}'::jsonb from prueba_cuenta_admin
union all
select cuenta, 'prueba-cuenta-' || cuenta || '@example.invalid', '{"nombre_mostrado":"Cuenta prueba"}'::jsonb from prueba_cuenta_admin
union all
select responsable, 'prueba-responsable-' || responsable || '@example.invalid', '{"nombre_mostrado":"Responsable prueba"}'::jsonb from prueba_cuenta_admin
union all
select otro_responsable, 'prueba-otro-' || otro_responsable || '@example.invalid', '{"nombre_mostrado":"Otro responsable"}'::jsonb from prueba_cuenta_admin;

insert into public.perfil (id_usuario, nombre_mostrado)
select admin, 'Admin prueba' from prueba_cuenta_admin
union all select cuenta, 'Cuenta prueba' from prueba_cuenta_admin
union all select responsable, 'Responsable prueba' from prueba_cuenta_admin
union all select otro_responsable, 'Otro responsable' from prueba_cuenta_admin
on conflict (id_usuario) do nothing;
insert into public.usuario_rol (id_usuario, codigo_rol)
select admin, 'ADMINISTRADOR' from prueba_cuenta_admin
union all select cuenta, 'RESPONSABLE' from prueba_cuenta_admin
union all select responsable, 'RESPONSABLE' from prueba_cuenta_admin
union all select otro_responsable, 'RESPONSABLE' from prueba_cuenta_admin
on conflict do nothing;

insert into public.estudiante (id_estudiante, nombre_mostrado, codigo_nivel_educativo)
select propio, 'Estudiante propio', 'BASICO_1' from prueba_cuenta_admin
union all select compartido, 'Estudiante compartido', 'BASICO_1' from prueba_cuenta_admin;
insert into public.responsable_estudiante (id_estudiante, id_responsable, es_principal)
select propio, cuenta, true from prueba_cuenta_admin
union all select compartido, cuenta, true from prueba_cuenta_admin
union all select compartido, otro_responsable, false from prueba_cuenta_admin;
insert into public.consentimiento (id_estudiante, id_responsable, proposito, version_documento, estado)
select propio, cuenta, 'USO_PLATAFORMA', 'prueba-eliminacion-cuenta', 'OTORGADO' from prueba_cuenta_admin;
insert into public.sesion_aprendizaje (id_estudiante, tipo_sesion)
select propio, 'PRACTICA' from prueba_cuenta_admin;
insert into public.auditoria (id_actor, entidad, id_entidad, accion, datos_nuevos)
select cuenta, 'cuenta', cuenta, 'INSERT', jsonb_build_object('id_usuario', cuenta) from prueba_cuenta_admin;

select set_config('request.jwt.claim.sub', admin::text, true),
       set_config('request.jwt.claims', jsonb_build_object('sub', admin, 'role', 'authenticated')::text, true)
from prueba_cuenta_admin;
set local role authenticated;
do $$
declare v record;
begin
  select * into v from prueba_cuenta_admin;
  begin
    perform public.eliminar_cuenta_admin(v.admin);
    raise exception 'ERROR: permitio autoeliminar al administrador';
  exception when insufficient_privilege then null;
  end;
  if public.eliminar_cuenta_admin(v.cuenta) is distinct from true then
    raise exception 'ERROR: no confirmo la eliminacion de la cuenta';
  end if;
end;
$$;
reset role;
do $$
declare v record;
begin
  select * into v from prueba_cuenta_admin;
  if (select count(*) from public.estudiante where id_estudiante = v.propio) <> 0
     or exists (select 1 from public.responsable_estudiante where id_responsable = v.cuenta)
     or exists (select 1 from public.consentimiento where id_responsable = v.cuenta)
     or exists (select 1 from public.sesion_aprendizaje where id_estudiante = v.propio)
     or exists (select 1 from public.auditoria where id_actor = v.cuenta or id_entidad = v.cuenta)
     or exists (select 1 from auth.users where id = v.cuenta)
     or exists (select 1 from public.perfil where id_usuario = v.cuenta) then
    raise exception 'ERROR: quedaron datos de la cuenta eliminada';
  end if;
  if (select count(*) from public.responsable_estudiante where id_estudiante = v.compartido) <> 1
     or not exists (select 1 from public.estudiante where id_estudiante = v.compartido and estado = 'ACTIVO') then
    raise exception 'ERROR: elimino los datos que conserva el otro responsable';
  end if;
end;
$$;
reset role;
rollback;
select 'OK: cuenta, perfil, sesiones y auditoria eliminados; perfil compartido conservado. Fixtures revertidos.' as resultado;
