-- Recorrido docente/familiar de prueba. Todos los fixtures se revierten.
begin;
create temporary table prueba_herramientas_docente (
  docente uuid, responsable uuid, estudiante_1 uuid, estudiante_2 uuid
) on commit drop;
insert into prueba_herramientas_docente values (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid());
grant select on prueba_herramientas_docente to authenticated;

insert into auth.users (id, email, raw_user_meta_data)
select docente, 'prueba-docente-' || docente || '@example.invalid', '{"nombre_mostrado":"Docente prueba"}'::jsonb
from prueba_herramientas_docente
union all
select responsable, 'prueba-familia-' || responsable || '@example.invalid', '{"nombre_mostrado":"Familia prueba"}'::jsonb
from prueba_herramientas_docente;
insert into public.perfil (id_usuario, nombre_mostrado)
select docente, 'Docente prueba' from prueba_herramientas_docente
union all select responsable, 'Familia prueba' from prueba_herramientas_docente
on conflict (id_usuario) do nothing;
insert into public.usuario_rol (id_usuario, codigo_rol)
select docente, 'DOCENTE' from prueba_herramientas_docente
union all select responsable, 'RESPONSABLE' from prueba_herramientas_docente
on conflict do nothing;
insert into public.estudiante (id_estudiante, nombre_mostrado, codigo_nivel_educativo)
select estudiante_1, 'Estudiante nivel uno', 'BASICO_1' from prueba_herramientas_docente
union all select estudiante_2, 'Estudiante nivel dos', 'BASICO_2' from prueba_herramientas_docente;
insert into public.responsable_estudiante (id_estudiante, id_responsable, es_principal)
select estudiante_1, responsable, true from prueba_herramientas_docente
union all select estudiante_2, responsable, false from prueba_herramientas_docente;

set local role authenticated;
do $$
declare
  v record;
  g record;
  v_nombre text;
  v_count integer;
  v_content uuid;
begin
  select * into v from prueba_herramientas_docente;
  perform set_config('request.jwt.claim.sub', v.docente::text, true);
  perform set_config('request.jwt.claims', jsonb_build_object('sub', v.docente, 'role', 'authenticated')::text, true);
  select * into g from public.docente_crear_grupo('Sala de prueba');
  if g.codigo_union is null or char_length(g.codigo_union) < 6 then
    raise exception 'ERROR: no genero codigo de union';
  end if;
  if (select count(*) from public.docente_listar_grupos()) <> 1 then
    raise exception 'ERROR: no aparece el grupo creado';
  end if;

  perform set_config('request.jwt.claim.sub', v.responsable::text, true);
  perform set_config('request.jwt.claims', jsonb_build_object('sub', v.responsable, 'role', 'authenticated')::text, true);
  v_nombre := public.responsable_unirse_grupo(v.estudiante_1, g.codigo_union);
  if v_nombre <> 'Sala de prueba' then raise exception 'ERROR: no vinculo el estudiante'; end if;
  perform public.responsable_unirse_grupo(v.estudiante_2, g.codigo_union);

  perform set_config('request.jwt.claim.sub', v.docente::text, true);
  perform set_config('request.jwt.claims', jsonb_build_object('sub', v.docente, 'role', 'authenticated')::text, true);
  if (select count(*) from public.docente_listar_estudiantes(g.id_grupo)) <> 2
     or (select count(distinct codigo_nivel_educativo) from public.docente_listar_estudiantes(g.id_grupo)) <> 2 then
    raise exception 'ERROR: el docente no ve estudiantes de ambos niveles';
  end if;
  if public.docente_reporte_estudiante(v.estudiante_1) ->> 'nombre' <> 'Estudiante nivel uno' then
    raise exception 'ERROR: no genero reporte individual';
  end if;
  if (select count(*) from public.docente_reporte_grupo(g.id_grupo)) <> 2 then
    raise exception 'ERROR: no genero reporte grupal';
  end if;
  select id_contenido into v_content from public.docente_catalogo_refuerzos() limit 1;
  if v_content is not null then
    v_count := public.docente_asignar_refuerzo(v.estudiante_1, v_content, 1, 'Refuerzo de prueba');
    if v_count <> 1 then raise exception 'ERROR: no asigno el refuerzo'; end if;
  end if;
end;
$$;
reset role;
rollback;
select 'OK: crear sala, compartir código, vincular estudiante, consultar progreso/reporte y asignar refuerzo. Fixtures revertidos.' as resultado;
