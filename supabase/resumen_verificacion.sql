-- K-MAT IA - Resumen unico de verificacion posterior al despliegue
-- Solo lectura. Devuelve una unica tabla para facilitar captura de pantalla.

with esperadas(nombre) as (
  values
    ('perfil'), ('rol'), ('usuario_rol'), ('nivel_educativo'),
    ('nivel_aprendizaje'), ('estudiante'), ('responsable_estudiante'),
    ('grupo'), ('docente_grupo'), ('inscripcion_grupo'), ('consentimiento'),
    ('contenido'), ('contenido_nivel_educativo'), ('ejercicio'),
    ('ejercicio_version'), ('recurso_apoyo'), ('sesion_aprendizaje'),
    ('resultado_diagnostico'), ('refuerzo_docente'), ('asignacion_ejercicio'),
    ('intento_ejercicio'), ('deteccion_dificultad'), ('intervencion_apoyo'),
    ('auditoria')
),
conteo as (
  select
    (select count(*) from esperadas) as tablas_esperadas,
    (select count(*) from esperadas e join pg_class c on c.relname = e.nombre and c.relkind = 'r' join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public') as tablas_creadas,
    (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public' and c.relkind = 'r' and c.relrowsecurity) as tablas_con_rls,
    (select count(*) from pg_policies where schemaname = 'public') as politicas_rls,
    (select count(*) from pg_constraint where contype = 'f' and connamespace = 'public'::regnamespace) as claves_foraneas,
    (select count(*) from pg_indexes where schemaname = 'public') as indices,
    (select count(distinct trigger_name) from information_schema.triggers where trigger_schema = 'public') as triggers,
    (select count(*) from information_schema.views where table_schema = 'public' and table_name in ('v_progreso_contenido', 'v_nivel_actual_estudiante')) as vistas
),
inconsistencias as (
  select 'Asignacion con sesion de otro estudiante' as regla, count(*)::bigint as errores
  from public.asignacion_ejercicio a join public.sesion_aprendizaje s on s.id_sesion = a.id_sesion
  where s.id_estudiante <> a.id_estudiante
  union all
  select 'Asignacion con refuerzo de otro estudiante', count(*)
  from public.asignacion_ejercicio a join public.refuerzo_docente r on r.id_refuerzo = a.id_refuerzo
  where r.id_estudiante <> a.id_estudiante
  union all
  select 'Intervencion con intento de otra asignacion', count(*)
  from public.intervencion_apoyo ia join public.intento_ejercicio i on i.id_intento = ia.id_intento
  where i.id_asignacion <> ia.id_asignacion
  union all
  select 'Intervencion con deteccion de otra asignacion', count(*)
  from public.intervencion_apoyo ia join public.deteccion_dificultad d on d.id_deteccion = ia.id_deteccion
  where d.id_asignacion <> ia.id_asignacion
  union all
  select 'Asignacion con ejercicio no publicado', count(*)
  from public.asignacion_ejercicio a join public.ejercicio_version ev on ev.id_ejercicio_version = a.id_ejercicio_version
  where ev.estado <> 'PUBLICADO'
)
select 'Tablas esperadas' as verificacion, tablas_creadas::text || ' de ' || tablas_esperadas as resultado,
  case when tablas_creadas = tablas_esperadas then 'OK' else 'REVISAR' end as estado from conteo
union all select 'RLS activo', tablas_con_rls::text || ' tablas', case when tablas_con_rls >= tablas_esperadas then 'OK' else 'REVISAR' end from conteo
union all select 'Politicas RLS', politicas_rls::text || ' politicas', case when politicas_rls >= 32 then 'OK' else 'REVISAR' end from conteo
union all select 'Claves foraneas', claves_foraneas::text || ' creadas', case when claves_foraneas > 0 then 'OK' else 'REVISAR' end from conteo
union all select 'Indices', indices::text || ' creados', case when indices >= 16 then 'OK' else 'REVISAR' end from conteo
union all select 'Triggers', triggers::text || ' creados', case when triggers >= 21 then 'OK' else 'REVISAR' end from conteo
union all select 'Vistas de progreso/nivel', vistas::text || ' de 2', case when vistas = 2 then 'OK' else 'REVISAR' end from conteo
union all select regla, errores::text || ' inconsistencias', case when errores = 0 then 'OK' else 'REVISAR' end from inconsistencias
order by verificacion;
