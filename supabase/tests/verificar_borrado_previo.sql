select
  (select count(*) from public.estudiante e
   where e.estado = 'INACTIVO'
     and exists (select 1 from public.auditoria a
                 where a.id_entidad = e.id_estudiante
                   and a.datos_nuevos ->> 'motivo' = 'ELIMINADO_POR_RESPONSABLE'))
    as perfiles_borrados_anteriormente_que_persisten,
  (select count(*) from public.estudiante e
   join public.responsable_estudiante re on re.id_estudiante = e.id_estudiante
   join auth.users u on u.id = re.id_responsable
   where lower(u.email) = 'padre.tomas@kmat.local'
     and e.estado = 'ACTIVO' and re.activo)
    as perfiles_activos_del_responsable;
