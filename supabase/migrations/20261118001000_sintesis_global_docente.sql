begin;
alter table public.sugerencia_refuerzo_ia
  drop constraint sugerencia_refuerzo_ia_evidencia_check,
  drop constraint sugerencia_refuerzo_ia_propuesta_check,
  add constraint sugerencia_refuerzo_ia_evidencia_check
    check (char_length(evidencia) <= case when eje='GLOBAL' then 1600 else 240 end),
  add constraint sugerencia_refuerzo_ia_propuesta_check
    check (char_length(propuesta) <= case when eje='GLOBAL' then 3600 else 280 end);

create or replace function public.finalizar_sugerencia_v3(p_estudiante uuid, p_eje text, p_reserva uuid,
  p_total integer, p_tema text, p_evidencia text, p_propuesta text, p_firma text)
returns boolean language plpgsql security definer set search_path = '' as $$
begin
  if p_total is null or p_total < 5 or char_length(trim(coalesce(p_tema,''))) not between 1 and 100
     or char_length(trim(coalesce(p_evidencia,''))) not between 1 and (case when p_eje='GLOBAL' then 1600 else 240 end)
     or char_length(trim(coalesce(p_propuesta,''))) not between 1 and (case when p_eje='GLOBAL' then 3600 else 280 end) then
    raise exception 'Sugerencia inválida' using errcode='22023'; end if;
  if p_firma is null or p_firma !~ '^[0-9a-f]{64}$' then raise exception 'Firma inválida'; end if;
  update public.sugerencia_refuerzo_ia set intentos_al_generar=p_total, tema=trim(p_tema),
    evidencia=trim(p_evidencia), propuesta=trim(p_propuesta), generada_en=now(),
    version_enfoque=3, firma_decision=p_firma, reserva=null, reserva_hasta=null
    where id_estudiante=p_estudiante and eje=p_eje and reserva=p_reserva and reserva_hasta > now();
  return found;
end;
$$;
commit;
