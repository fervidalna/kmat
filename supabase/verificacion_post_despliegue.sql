-- K-MAT IA - Auditoria de solo lectura posterior al despliegue
-- Ejecutar completo en Supabase SQL Editor y compartir los resultados.
-- No inserta, modifica ni elimina datos.

-- 1. Tablas esperadas y tablas ausentes.
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
)
select
  '01_tablas_esperadas' as prueba,
  e.nombre as tabla,
  case when c.relname is null then 'FALTA' else 'OK' end as estado
from esperadas e
left join pg_class c on c.relname = e.nombre and c.relkind = 'r'
left join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
order by e.nombre;

-- 2. RLS debe estar activado en las tablas del esquema public.
select
  '02_rls' as prueba,
  c.relname as tabla,
  case when c.relrowsecurity then 'OK' else 'RLS_DESACTIVADO' end as estado
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by c.relname;

-- 3. Politicas RLS creadas por tabla.
select
  '03_politicas_rls' as prueba,
  tablename as tabla,
  count(*) as cantidad_politicas,
  string_agg(policyname, ', ' order by policyname) as politicas
from pg_policies
where schemaname = 'public'
group by tablename
order by tablename;

-- 4. Claves foraneas y relaciones efectivamente instaladas.
select
  '04_claves_foraneas' as prueba,
  conrelid::regclass::text as tabla_origen,
  conname as restriccion,
  pg_get_constraintdef(oid) as definicion
from pg_constraint
where contype = 'f'
  and connamespace = 'public'::regnamespace
order by tabla_origen, conname;

-- 5. Restricciones UNIQUE y CHECK, necesarias para evitar inconsistencias.
select
  '05_integridad' as prueba,
  conrelid::regclass::text as tabla,
  conname as restriccion,
  case contype when 'u' then 'UNIQUE' when 'c' then 'CHECK' else contype::text end as tipo,
  pg_get_constraintdef(oid) as definicion
from pg_constraint
where contype in ('u', 'c')
  and connamespace = 'public'::regnamespace
order by tabla, tipo, restriccion;

-- 6. Indices disponibles para las consultas frecuentes.
select
  '06_indices' as prueba,
  tablename as tabla,
  indexname as indice,
  indexdef as definicion
from pg_indexes
where schemaname = 'public'
order by tablename, indexname;

-- 7. Triggers de validacion, auditoria y control de cambios.
select
  '07_triggers' as prueba,
  event_object_table as tabla,
  trigger_name,
  action_timing,
  event_manipulation
from information_schema.triggers
where trigger_schema = 'public'
order by event_object_table, trigger_name;

-- 8. Vistas que calculan progreso y nivel actual sin duplicar datos.
select
  '08_vistas' as prueba,
  table_name as vista,
  view_definition
from information_schema.views
where table_schema = 'public'
  and table_name in ('v_progreso_contenido', 'v_nivel_actual_estudiante')
order by table_name;

-- 9. Inconsistencias de datos que nunca deben devolver filas.
select
  '09a_asignacion_sesion_otro_estudiante' as prueba,
  a.id_asignacion::text as id_registro
from public.asignacion_ejercicio a
join public.sesion_aprendizaje s on s.id_sesion = a.id_sesion
where s.id_estudiante <> a.id_estudiante;

select
  '09b_asignacion_refuerzo_otro_estudiante' as prueba,
  a.id_asignacion::text as id_registro
from public.asignacion_ejercicio a
join public.refuerzo_docente r on r.id_refuerzo = a.id_refuerzo
where r.id_estudiante <> a.id_estudiante;

select
  '09c_intervencion_intento_otra_asignacion' as prueba,
  ia.id_intervencion::text as id_registro
from public.intervencion_apoyo ia
join public.intento_ejercicio i on i.id_intento = ia.id_intento
where i.id_asignacion <> ia.id_asignacion;

select
  '09d_intervencion_deteccion_otra_asignacion' as prueba,
  ia.id_intervencion::text as id_registro
from public.intervencion_apoyo ia
join public.deteccion_dificultad d on d.id_deteccion = ia.id_deteccion
where d.id_asignacion <> ia.id_asignacion;

select
  '09e_asignacion_version_no_publicada' as prueba,
  a.id_asignacion::text as id_registro
from public.asignacion_ejercicio a
join public.ejercicio_version ev on ev.id_ejercicio_version = a.id_ejercicio_version
where ev.estado <> 'PUBLICADO';

select
  '09f_resultado_fuera_de_diagnostico' as prueba,
  rd.id_sesion::text as id_registro
from public.resultado_diagnostico rd
join public.sesion_aprendizaje s on s.id_sesion = rd.id_sesion
where s.tipo_sesion <> 'DIAGNOSTICO' or s.estado <> 'COMPLETADA';

-- 10. Resumen de registros por tabla. Sirve para confirmar carga inicial.
select '10_resumen_registros' as prueba, 'perfil' as tabla, count(*) as registros from public.perfil
union all select '10_resumen_registros', 'estudiante', count(*) from public.estudiante
union all select '10_resumen_registros', 'contenido', count(*) from public.contenido
union all select '10_resumen_registros', 'ejercicio', count(*) from public.ejercicio
union all select '10_resumen_registros', 'ejercicio_version', count(*) from public.ejercicio_version
union all select '10_resumen_registros', 'sesion_aprendizaje', count(*) from public.sesion_aprendizaje
union all select '10_resumen_registros', 'asignacion_ejercicio', count(*) from public.asignacion_ejercicio
union all select '10_resumen_registros', 'intento_ejercicio', count(*) from public.intento_ejercicio
union all select '10_resumen_registros', 'auditoria', count(*) from public.auditoria
order by tabla;
