-- K-MAT IA - Monitoreo de uso en Supabase Free
-- Solo lectura. Ejecutar cada semana desde SQL Editor.

-- 1. Tamano de la base de datos. Mantener bajo 400 MB deja margen antes del
-- limite Free de 500 MB por proyecto.
select
  'base_de_datos' as recurso,
  round(pg_database_size(current_database()) / 1024.0 / 1024.0, 2) as megabytes,
  case
    when pg_database_size(current_database()) < 400 * 1024 * 1024 then 'OK'
    when pg_database_size(current_database()) < 475 * 1024 * 1024 then 'VIGILAR'
    else 'ACTUAR'
  end as estado;

-- 2. Tablas e indices que mas espacio ocupan.
select
  c.relname as tabla,
  round(pg_total_relation_size(c.oid) / 1024.0 / 1024.0, 2) as megabytes_totales,
  round(pg_relation_size(c.oid) / 1024.0 / 1024.0, 2) as megabytes_datos,
  round((pg_total_relation_size(c.oid) - pg_relation_size(c.oid)) / 1024.0 / 1024.0, 2) as megabytes_indices
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by pg_total_relation_size(c.oid) desc;

-- 3. Archivos en Supabase Storage. No guardar videos largos ni trazos de
-- escritura: conserva respuestas reconocidas en PostgreSQL y recursos pequeños.
select
  bucket_id,
  count(*) as archivos,
  round(coalesce(sum((metadata ->> 'size')::bigint), 0) / 1024.0 / 1024.0, 2) as megabytes
from storage.objects
group by bucket_id
order by megabytes desc;

-- 4. Historial que suele crecer. El resultado orienta una politica de archivo,
-- nunca eliminar datos de menores sin una regla de retencion aprobada.
select 'intento_ejercicio' as tabla, count(*) as registros, min(respondido_en) as mas_antiguo, max(respondido_en) as mas_reciente from public.intento_ejercicio
union all select 'auditoria', count(*), min(creado_en), max(creado_en) from public.auditoria
union all select 'intervencion_apoyo', count(*), min(mostrado_en), max(mostrado_en) from public.intervencion_apoyo
union all select 'deteccion_dificultad', count(*), min(detectado_en), max(detectado_en) from public.deteccion_dificultad
order by tabla;
