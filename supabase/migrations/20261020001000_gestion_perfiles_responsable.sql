-- Flujo seguro de perfiles de estudiantes administrados por el responsable.
-- El JWT del usuario determina el responsable; el cliente no puede elegir otro.

create or replace function public.listar_mis_estudiantes()
returns table (
  id_estudiante uuid,
  nombre_mostrado text,
  codigo_nivel_educativo text
)
language sql
stable
security invoker
set search_path = public
as $$
  select e.id_estudiante, e.nombre_mostrado, e.codigo_nivel_educativo
  from public.estudiante e
  join public.responsable_estudiante re on re.id_estudiante = e.id_estudiante
  where re.id_responsable = auth.uid() and re.activo and e.estado = 'ACTIVO'
  order by e.creado_en, e.id_estudiante;
$$;

create or replace function public.crear_mi_estudiante(
  p_nombre text,
  p_codigo_nivel_educativo text,
  p_version_consentimiento text
)
returns table (
  id_estudiante uuid,
  nombre_mostrado text,
  codigo_nivel_educativo text
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id_estudiante uuid;
  v_nombre text := trim(coalesce(p_nombre, ''));
  v_responsable uuid := auth.uid();
begin
  if v_responsable is null then
    raise exception 'Se requiere una sesión autenticada' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.usuario_rol ur
    where ur.id_usuario = v_responsable and ur.codigo_rol = 'RESPONSABLE'
  ) then
    raise exception 'Se requiere el rol RESPONSABLE' using errcode = '42501';
  end if;
  if char_length(v_nombre) not between 1 and 100 then
    raise exception 'El nombre debe tener entre 1 y 100 caracteres' using errcode = '22023';
  end if;
  if p_codigo_nivel_educativo not in ('BASICO_1', 'BASICO_2') then
    raise exception 'Nivel educativo no válido' using errcode = '22023';
  end if;
  if nullif(trim(coalesce(p_version_consentimiento, '')), '') is null then
    raise exception 'Se requiere registrar la versión del consentimiento' using errcode = '22023';
  end if;

  insert into public.estudiante (nombre_mostrado, codigo_nivel_educativo)
  values (v_nombre, p_codigo_nivel_educativo)
  returning estudiante.id_estudiante into v_id_estudiante;

  insert into public.responsable_estudiante (
    id_estudiante, id_responsable, tipo_vinculo, es_principal
  ) values (v_id_estudiante, v_responsable, 'RESPONSABLE', true);

  insert into public.consentimiento (
    id_estudiante, id_responsable, proposito, version_documento, estado
  ) values (
    v_id_estudiante, v_responsable, 'USO_PLATAFORMA', trim(p_version_consentimiento), 'OTORGADO'
  );

  return query select e.id_estudiante, e.nombre_mostrado, e.codigo_nivel_educativo
    from public.estudiante e where e.id_estudiante = v_id_estudiante;
end;
$$;

revoke all on function public.listar_mis_estudiantes() from public;
revoke all on function public.crear_mi_estudiante(text, text, text) from public;
grant execute on function public.listar_mis_estudiantes() to authenticated;
grant execute on function public.crear_mi_estudiante(text, text, text) to authenticated;

