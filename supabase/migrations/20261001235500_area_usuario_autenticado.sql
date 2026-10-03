-- K-MAT IA · Lectura segura del área del usuario autenticado
-- Ejecutar una vez después del esquema principal.
-- No modifica perfiles, roles ni datos existentes.

create or replace function public.mi_area_usuario()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select case
    when exists (
      select 1 from public.usuario_rol
      where id_usuario = auth.uid() and codigo_rol = 'ADMINISTRADOR'
    ) then 'ADMINISTRADOR'
    when exists (
      select 1 from public.usuario_rol
      where id_usuario = auth.uid() and codigo_rol = 'DOCENTE'
    ) then 'DOCENTE'
    when exists (
      select 1 from public.usuario_rol
      where id_usuario = auth.uid() and codigo_rol = 'RESPONSABLE'
    ) then 'RESPONSABLE'
    else null
  end;
$$;

revoke all on function public.mi_area_usuario() from public;
grant execute on function public.mi_area_usuario() to authenticated;
