begin;

-- These records are accessible only through the authenticated Edge Function.
create table public.sugerencia_refuerzo_ia (
  id_estudiante uuid not null references public.estudiante(id_estudiante) on delete cascade,
  eje text not null check (eje in ('NUMEROS_OPERACIONES','PATRONES_ALGEBRA','GEOMETRIA','MEDICION','DATOS_PROBABILIDAD')),
  intentos_al_generar integer check (intentos_al_generar >= 5),
  tema text check (char_length(tema) <= 100),
  evidencia text check (char_length(evidencia) <= 240),
  propuesta text check (char_length(propuesta) <= 280),
  generada_en timestamptz,
  reserva uuid,
  reserva_hasta timestamptz,
  primary key (id_estudiante, eje),
  check ((generada_en is null and intentos_al_generar is null and tema is null and evidencia is null and propuesta is null)
    or (generada_en is not null and intentos_al_generar is not null and tema is not null and evidencia is not null and propuesta is not null))
);
alter table public.sugerencia_refuerzo_ia enable row level security;
revoke all on public.sugerencia_refuerzo_ia from public, anon, authenticated;
grant select, insert, update, delete on public.sugerencia_refuerzo_ia to service_role;

-- Serializes requests across devices; never regenerates without five new answers.
create or replace function public.reservar_sugerencia_ia(p_estudiante uuid, p_eje text, p_total integer)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare r public.sugerencia_refuerzo_ia%rowtype; v_reserva uuid;
begin
  if p_total is null or p_total < 5 then return jsonb_build_object('status','insufficient'); end if;
  if p_eje is null or p_eje not in ('NUMEROS_OPERACIONES','PATRONES_ALGEBRA','GEOMETRIA','MEDICION','DATOS_PROBABILIDAD') then
    raise exception 'Materia inválida' using errcode = '22023';
  end if;
  insert into public.sugerencia_refuerzo_ia(id_estudiante,eje) values (p_estudiante,p_eje)
    on conflict (id_estudiante,eje) do nothing;
  select * into r from public.sugerencia_refuerzo_ia where id_estudiante=p_estudiante and eje=p_eje for update;
  if r.generada_en is not null and p_total >= r.intentos_al_generar and p_total < r.intentos_al_generar + 5 then
    return jsonb_build_object('status','cached');
  end if;
  if r.reserva is not null and r.reserva_hasta > now() then
    return jsonb_build_object('status','busy');
  end if;
  v_reserva := gen_random_uuid();
  update public.sugerencia_refuerzo_ia set reserva=v_reserva, reserva_hasta=now()+interval '90 seconds'
    where id_estudiante=p_estudiante and eje=p_eje;
  return jsonb_build_object('status','reserved','lease',v_reserva);
end;
$$;

create or replace function public.finalizar_sugerencia_ia(p_estudiante uuid, p_eje text, p_reserva uuid,
  p_total integer, p_tema text, p_evidencia text, p_propuesta text)
returns boolean language plpgsql security definer set search_path = '' as $$
begin
  if p_total < 5 or char_length(trim(coalesce(p_tema,''))) not between 1 and 100
     or char_length(trim(coalesce(p_evidencia,''))) not between 1 and 240
     or char_length(trim(coalesce(p_propuesta,''))) not between 1 and 280 then
    raise exception 'Sugerencia inválida' using errcode='22023';
  end if;
  update public.sugerencia_refuerzo_ia set intentos_al_generar=p_total, tema=trim(p_tema),
    evidencia=trim(p_evidencia), propuesta=trim(p_propuesta), generada_en=now(), reserva=null, reserva_hasta=null
    where id_estudiante=p_estudiante and eje=p_eje and reserva=p_reserva and reserva_hasta > now();
  return found;
end;
$$;

create or replace function public.cancelar_sugerencia_ia(p_estudiante uuid, p_eje text, p_reserva uuid)
returns void language sql security definer set search_path = '' as $$
  update public.sugerencia_refuerzo_ia set reserva=null, reserva_hasta=null
    where id_estudiante=p_estudiante and eje=p_eje and reserva=p_reserva;
$$;

revoke all on function public.reservar_sugerencia_ia(uuid,text,integer) from public, anon, authenticated;
revoke all on function public.finalizar_sugerencia_ia(uuid,text,uuid,integer,text,text,text) from public, anon, authenticated;
revoke all on function public.cancelar_sugerencia_ia(uuid,text,uuid) from public, anon, authenticated;
grant execute on function public.reservar_sugerencia_ia(uuid,text,integer) to service_role;
grant execute on function public.finalizar_sugerencia_ia(uuid,text,uuid,integer,text,text,text) to service_role;
grant execute on function public.cancelar_sugerencia_ia(uuid,text,uuid) to service_role;
commit;
