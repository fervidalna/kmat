-- Prueba transaccional: solo usa fixtures nuevos y revierte todos sus cambios.
-- Ejecutar despues de 20261024005000_borrado_definitivo_estudiante.sql.
begin;

create temporary table prueba_eliminar_perfil (
  responsable uuid, otro uuid, propio uuid, compartido uuid, ajeno uuid
) on commit drop;
insert into prueba_eliminar_perfil
values (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid());
grant select on prueba_eliminar_perfil to authenticated;

insert into auth.users (id, email, raw_user_meta_data)
select responsable, 'prueba-eliminar-' || responsable || '@example.invalid',
       '{"nombre_mostrado":"Prueba transaccional","auto_rol":"RESPONSABLE"}'::jsonb
from prueba_eliminar_perfil
union all
select otro, 'prueba-eliminar-' || otro || '@example.invalid',
       '{"nombre_mostrado":"Prueba transaccional 2","auto_rol":"RESPONSABLE"}'::jsonb
from prueba_eliminar_perfil;

insert into public.perfil (id_usuario, nombre_mostrado)
select responsable, 'Prueba transaccional' from prueba_eliminar_perfil
union all select otro, 'Prueba transaccional 2' from prueba_eliminar_perfil
on conflict (id_usuario) do nothing;
insert into public.usuario_rol (id_usuario, codigo_rol)
select responsable, 'RESPONSABLE' from prueba_eliminar_perfil
union all select otro, 'RESPONSABLE' from prueba_eliminar_perfil
on conflict do nothing;

insert into public.estudiante (id_estudiante, nombre_mostrado, codigo_nivel_educativo)
select propio, 'Prueba propio', 'BASICO_1' from prueba_eliminar_perfil
union all select compartido, 'Prueba compartido', 'BASICO_1' from prueba_eliminar_perfil
union all select ajeno, 'Prueba ajeno', 'BASICO_1' from prueba_eliminar_perfil;
insert into public.responsable_estudiante (id_estudiante, id_responsable, es_principal)
select propio, responsable, true from prueba_eliminar_perfil
union all select compartido, responsable, true from prueba_eliminar_perfil
union all select compartido, otro, false from prueba_eliminar_perfil
union all select ajeno, otro, true from prueba_eliminar_perfil;
insert into public.consentimiento (id_estudiante, id_responsable, proposito, version_documento, estado)
select propio, responsable, 'USO_PLATAFORMA', 'prueba-transaccional', 'OTORGADO'
from prueba_eliminar_perfil;
insert into public.sesion_aprendizaje (id_estudiante, tipo_sesion)
select propio, 'PRACTICA' from prueba_eliminar_perfil;
insert into public.grupo (nombre, codigo_union)
select 'Prueba borrado', 'PR' || left(propio::text, 10) from prueba_eliminar_perfil;
insert into public.inscripcion_grupo (id_grupo, id_estudiante)
select g.id_grupo, p.propio
from public.grupo g cross join prueba_eliminar_perfil p
where g.codigo_union = 'PR' || left(p.propio::text, 10);
insert into public.auditoria (entidad, id_entidad, accion, datos_nuevos)
select 'estudiante', propio, 'INSERT', jsonb_build_object('id_estudiante', propio)
from prueba_eliminar_perfil;

select set_config('request.jwt.claim.sub', responsable::text, true),
       set_config('request.jwt.claims', jsonb_build_object('sub', responsable, 'role', 'authenticated')::text, true)
from prueba_eliminar_perfil;
set local role authenticated;

do $$
declare
  v record;
begin
  select * into v from prueba_eliminar_perfil;
  begin
    perform public.eliminar_mi_estudiante(v.ajeno);
    raise exception 'ERROR: permitio eliminar un perfil ajeno';
  exception when insufficient_privilege then null;
  end;
  begin
    perform public.eliminar_mi_estudiante(gen_random_uuid());
    raise exception 'ERROR: acepto un perfil inexistente';
  exception when insufficient_privilege then null;
  end;
  begin
    perform public.eliminar_mi_estudiante(v.compartido);
    raise exception 'ERROR: permitio borrar un perfil compartido';
  exception when sqlstate 'KM001' then null;
  end;
  if public.eliminar_mi_estudiante(v.propio) is distinct from true then
    raise exception 'ERROR: no confirmo el borrado propio';
  end if;
  begin
    perform public.eliminar_mi_estudiante(v.propio);
    raise exception 'ERROR: permitio borrar dos veces un perfil inexistente';
  exception when insufficient_privilege then null;
  end;
  if (select count(*) from public.listar_mis_estudiantes()) <> 1 then
    raise exception 'ERROR: el listado propio debe conservar solo el perfil compartido';
  end if;
end;
$$;

reset role;
do $$
declare
  v record;
begin
  select * into v from prueba_eliminar_perfil;
  if exists (select 1 from public.estudiante where id_estudiante = v.propio) then
    raise exception 'ERROR: dejo el estudiante borrado en la tabla';
  end if;
  if exists (select 1 from public.responsable_estudiante where id_estudiante = v.propio)
      or exists (select 1 from public.consentimiento where id_estudiante = v.propio)
      or exists (select 1 from public.sesion_aprendizaje where id_estudiante = v.propio)
      or exists (select 1 from public.inscripcion_grupo where id_estudiante = v.propio)
      or exists (select 1 from public.auditoria where id_entidad = v.propio) then
    raise exception 'ERROR: dejo datos vinculados al estudiante borrado';
  end if;
  if (select estado from public.estudiante where id_estudiante = v.compartido) is distinct from 'ACTIVO' then
    raise exception 'ERROR: desactivo un estudiante compartido';
  end if;
  if (select count(*) from public.responsable_estudiante where id_estudiante = v.compartido and activo) <> 2 then
    raise exception 'ERROR: altero un perfil compartido';
  end if;
  if has_function_privilege('anon', 'public.eliminar_mi_estudiante(uuid)', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.borrar_datos_estudiante(uuid)', 'EXECUTE') then
    raise exception 'ERROR: el borrado permite acceso no autorizado';
  end if;
end;
$$;

select set_config('request.jwt.claim.sub', otro::text, true),
       set_config('request.jwt.claims', jsonb_build_object('sub', otro, 'role', 'authenticated')::text, true)
from prueba_eliminar_perfil;
set local role authenticated;
do $$
begin
  if (select count(*) from public.listar_mis_estudiantes()) <> 2 then
    raise exception 'ERROR: el otro responsable perdio acceso a sus perfiles';
  end if;
end;
$$;
reset role;

rollback;
select 'OK: borrado definitivo, autorizacion, referencias, perfil compartido y auditoria. Fixtures revertidos.' as resultado;
