-- El listado autenticado fallaba con SQLSTATE 42501: faltaba SELECT sobre
-- estudiante y responsable_estudiante. Las politicas RLS ya limitan las filas
-- a los responsables vinculados y docentes autorizados.
-- Conceder lectura no concede altas, modificaciones ni bajas.
begin;

alter table public.estudiante enable row level security;
alter table public.responsable_estudiante enable row level security;

grant select on public.estudiante, public.responsable_estudiante to authenticated;

commit;
