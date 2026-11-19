begin;
alter table public.sugerencia_refuerzo_ia
  drop constraint sugerencia_refuerzo_ia_version_enfoque_check,
  add constraint sugerencia_refuerzo_ia_version_enfoque_check check (version_enfoque in (1,2,3)),
  add column firma_decision text;

create function public.docente_catalogo_sugerencia()
returns table(codigo text, nombre text, eje text, dificultad integer, ejercicios_disponibles bigint)
language sql stable security definer set search_path = '' as $$
  select c.codigo, r.nombre, r.eje, r.dificultad, r.ejercicios_disponibles
  from public.docente_catalogo_refuerzos_nivel() r
  join public.contenido c on c.id_contenido=r.id_contenido
  order by r.eje, c.codigo, r.dificultad;
$$;
revoke all on function public.docente_catalogo_sugerencia() from public, anon;
grant execute on function public.docente_catalogo_sugerencia() to authenticated;
create or replace function public.reservar_sugerencia_v3(p_estudiante uuid, p_eje text, p_total integer, p_firma text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare r public.sugerencia_refuerzo_ia%rowtype; v_reserva uuid;
begin
  if p_total is null or p_total < 5 then return jsonb_build_object('status','insufficient'); end if;
  if p_eje is null or p_eje not in ('NUMEROS_OPERACIONES','PATRONES_ALGEBRA','GEOMETRIA','MEDICION','DATOS_PROBABILIDAD') then
    raise exception 'Materia inválida' using errcode='22023';
  end if;
  insert into public.sugerencia_refuerzo_ia(id_estudiante,eje) values (p_estudiante,p_eje)
    on conflict (id_estudiante,eje) do nothing;
  select * into r from public.sugerencia_refuerzo_ia where id_estudiante=p_estudiante and eje=p_eje for update;
  if r.version_enfoque = 3 and r.firma_decision = p_firma and r.generada_en is not null and p_total >= r.intentos_al_generar
    and p_total < r.intentos_al_generar + 5 then return jsonb_build_object('status','cached'); end if;
  if r.reserva is not null and r.reserva_hasta > now() then return jsonb_build_object('status','busy'); end if;
  v_reserva := gen_random_uuid();
  update public.sugerencia_refuerzo_ia set reserva=v_reserva, reserva_hasta=now()+interval '90 seconds'
    where id_estudiante=p_estudiante and eje=p_eje;
  return jsonb_build_object('status','reserved','lease',v_reserva);
end;
$$;

create or replace function public.finalizar_sugerencia_v3(p_estudiante uuid, p_eje text, p_reserva uuid,
  p_total integer, p_tema text, p_evidencia text, p_propuesta text, p_firma text)
returns boolean language plpgsql security definer set search_path = '' as $$
begin
  if p_total is null or p_total < 5 or char_length(trim(coalesce(p_tema,''))) not between 1 and 100
     or char_length(trim(coalesce(p_evidencia,''))) not between 1 and 240
     or char_length(trim(coalesce(p_propuesta,''))) not between 1 and 280 then
    raise exception 'Sugerencia inválida' using errcode='22023'; end if;
  if p_firma is null or p_firma !~ '^[0-9a-f]{64}$' then raise exception 'Firma inválida'; end if;
  update public.sugerencia_refuerzo_ia set intentos_al_generar=p_total, tema=trim(p_tema),
    evidencia=trim(p_evidencia), propuesta=trim(p_propuesta), generada_en=now(),
    version_enfoque=3, firma_decision=p_firma, reserva=null, reserva_hasta=null
    where id_estudiante=p_estudiante and eje=p_eje and reserva=p_reserva and reserva_hasta > now();
  return found;
end;
$$;
revoke all on function public.reservar_sugerencia_v3(uuid,text,integer,text) from public, anon, authenticated;
revoke all on function public.finalizar_sugerencia_v3(uuid,text,uuid,integer,text,text,text,text) from public, anon, authenticated;
grant execute on function public.reservar_sugerencia_v3(uuid,text,integer,text) to service_role;
grant execute on function public.finalizar_sugerencia_v3(uuid,text,uuid,integer,text,text,text,text) to service_role;
commit;
