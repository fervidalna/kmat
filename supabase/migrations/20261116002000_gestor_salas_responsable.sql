-- Permite a un responsable consultar las salas activas de un estudiante
-- y retirar su inscripcion sin borrar el historial.
create or replace function public.responsable_listar_salas_estudiante(p_id_estudiante uuid)
returns table (
  id_grupo uuid,
  nombre text,
  docentes text,
  fecha_inicio timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null or not public.es_responsable_de(p_id_estudiante) then
    raise exception 'No tienes acceso a este estudiante' using errcode = '42501';
  end if;

  return query
  select g.id_grupo,
         g.nombre,
         coalesce(string_agg(distinct p.nombre_mostrado, ', '), 'Docente'),
         min(ig.fecha_inicio)
  from public.inscripcion_grupo ig
  join public.grupo g on g.id_grupo = ig.id_grupo
  left join public.docente_grupo dg on dg.id_grupo = g.id_grupo and dg.activo
  left join public.perfil p on p.id_usuario = dg.id_docente and p.estado = 'ACTIVO'
  where ig.id_estudiante = p_id_estudiante
    and ig.estado = 'ACTIVA'
    and g.estado = 'ACTIVO'
  group by g.id_grupo, g.nombre
  order by min(ig.fecha_inicio), g.nombre;
end;
$$;

create or replace function public.responsable_salir_de_sala(
  p_id_estudiante uuid,
  p_id_grupo uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null or not public.es_responsable_de(p_id_estudiante) then
    raise exception 'No tienes acceso a este estudiante' using errcode = '42501';
  end if;

  update public.inscripcion_grupo ig
  set estado = 'RETIRADA', fecha_fin = greatest(now(), ig.fecha_inicio)
  where ig.id_estudiante = p_id_estudiante
    and ig.id_grupo = p_id_grupo
    and ig.estado = 'ACTIVA';

  return found;
end;
$$;

revoke all on function public.responsable_listar_salas_estudiante(uuid) from public, anon;
revoke all on function public.responsable_salir_de_sala(uuid, uuid) from public, anon;
grant execute on function public.responsable_listar_salas_estudiante(uuid) to authenticated;
grant execute on function public.responsable_salir_de_sala(uuid, uuid) to authenticated;
