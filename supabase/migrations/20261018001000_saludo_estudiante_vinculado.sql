-- Si la cuenta tiene un estudiante activo vinculado, el saludo usa su nombre.
-- Las cuentas sin estudiante asociado conservan el nombre ingresado al registrarse.

create or replace function public.mi_nombre_usuario()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (
      select e.nombre_mostrado
      from public.responsable_estudiante re
      join public.estudiante e on e.id_estudiante = re.id_estudiante
      where re.id_responsable = auth.uid()
        and re.activo
        and e.estado = 'ACTIVO'
      order by re.es_principal desc, re.creado_en asc
      limit 1
    ),
    (
      select p.nombre_mostrado
      from public.perfil p
      where p.id_usuario = auth.uid()
      limit 1
    )
  );
$$;

revoke all on function public.mi_nombre_usuario() from public;
grant execute on function public.mi_nombre_usuario() to authenticated;
