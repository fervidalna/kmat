begin;

-- Las salas eliminadas salen del espacio docente y dejan de aceptar uniones.
-- Las inscripciones y el historial permanecen almacenados para conservar datos.
create or replace function public.docente_listar_grupos()
returns table (id_grupo uuid, nombre text, codigo_union text, cantidad_estudiantes bigint)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not exists (select 1 from public.usuario_rol where id_usuario = auth.uid() and codigo_rol = 'DOCENTE') then
    raise exception 'Se requiere el rol DOCENTE' using errcode = '42501';
  end if;
  return query
  select g.id_grupo, g.nombre, g.codigo_union,
         count(distinct ig.id_estudiante) filter (where ig.estado = 'ACTIVA' and e.estado = 'ACTIVO')
  from public.grupo g
  join public.docente_grupo dg on dg.id_grupo = g.id_grupo and dg.id_docente = auth.uid() and dg.activo
  left join public.inscripcion_grupo ig on ig.id_grupo = g.id_grupo
  left join public.estudiante e on e.id_estudiante = ig.id_estudiante
  where g.estado = 'ACTIVO'
  group by g.id_grupo
  order by g.nombre;
end;
$$;

create or replace function public.docente_eliminar_grupo(p_id_grupo uuid)
returns boolean
language plpgsql security definer set search_path = ''
as $$
begin
  if not exists (
    select 1 from public.usuario_rol
    where id_usuario = auth.uid() and codigo_rol = 'DOCENTE'
  ) then
    raise exception 'Se requiere el rol DOCENTE' using errcode = '42501';
  end if;

  if not exists (
    select 1 from public.docente_grupo dg
    join public.grupo g on g.id_grupo = dg.id_grupo
    where dg.id_docente = auth.uid() and dg.id_grupo = p_id_grupo
      and dg.activo and g.estado = 'ACTIVO'
  ) then
    raise exception 'No tienes acceso a esta sala' using errcode = '42501';
  end if;

  update public.inscripcion_grupo
  set estado = 'FINALIZADA', fecha_fin = coalesce(fecha_fin, now())
  where id_grupo = p_id_grupo and estado = 'ACTIVA';

  update public.docente_grupo
  set activo = false, fecha_fin = coalesce(fecha_fin, now())
  where id_grupo = p_id_grupo and activo;

  update public.grupo
  set estado = 'INACTIVO', actualizado_en = now(), version_registro = version_registro + 1
  where id_grupo = p_id_grupo and estado = 'ACTIVO';

  return true;
end;
$$;

revoke all on function public.docente_eliminar_grupo(uuid) from public, anon;
grant execute on function public.docente_eliminar_grupo(uuid) to authenticated;

commit;
