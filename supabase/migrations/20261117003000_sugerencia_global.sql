begin;
alter table public.sugerencia_refuerzo_ia
  drop constraint sugerencia_refuerzo_ia_eje_check,
  add constraint sugerencia_refuerzo_ia_eje_check check (eje in (
    'NUMEROS_OPERACIONES','PATRONES_ALGEBRA','GEOMETRIA','MEDICION','DATOS_PROBABILIDAD','GLOBAL'
  ));
commit;
