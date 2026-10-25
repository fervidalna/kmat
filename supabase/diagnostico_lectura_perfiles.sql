-- Solo lectura: comprueba los permisos y las politicas usados por Android.
select jsonb_build_object(
  'tablas', (
    select jsonb_agg(jsonb_build_object(
      'tabla', c.relname,
      'rls_habilitado', c.relrowsecurity,
      'puede_leer', has_table_privilege('authenticated', c.oid, 'SELECT')
    ))
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relname in ('estudiante', 'responsable_estudiante')
  ),
  'politicas', (
    select jsonb_agg(jsonb_build_object(
      'tabla', tablename, 'politica', policyname, 'roles', roles, 'operacion', cmd, 'filtro', qual
    )) from pg_policies
    where schemaname = 'public'
      and tablename in ('estudiante', 'responsable_estudiante')
  ),
  'funciones', (
    select jsonb_agg(jsonb_build_object(
      'funcion', p.proname,
      'ejecuta_como_propietario', p.prosecdef,
      'puede_ejecutar', has_function_privilege('authenticated', p.oid, 'EXECUTE')
    )) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in ('listar_mis_estudiantes', 'es_responsable_de', 'es_docente_de')
  )
) as diagnostico;
