-- El listado usa SECURITY DEFINER para no depender de las politicas RLS de
-- lectura de las tablas relacionadas. El filtro siempre queda limitado al
-- responsable autenticado y a sus vinculos activos.
create or replace function public.listar_mis_estudiantes()
returns table (
  id_estudiante uuid,
  nombre_mostrado text,
  codigo_nivel_educativo text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Se requiere una sesion autenticada' using errcode = '42501';
  end if;

  if not exists (
    select 1
    from public.usuario_rol ur
    where ur.id_usuario = auth.uid()
      and ur.codigo_rol = 'RESPONSABLE'
  ) then
    raise exception 'Se requiere el rol RESPONSABLE' using errcode = '42501';
  end if;

  return query
  select e.id_estudiante,
         e.nombre_mostrado,
         e.codigo_nivel_educativo
  from public.estudiante e
  join public.responsable_estudiante re on re.id_estudiante = e.id_estudiante
  where re.id_responsable = auth.uid()
    and re.activo
    and e.estado = 'ACTIVO'
  order by e.creado_en, e.id_estudiante;
end;
$$;

revoke all on function public.listar_mis_estudiantes() from public;
grant execute on function public.listar_mis_estudiantes() to authenticated;
