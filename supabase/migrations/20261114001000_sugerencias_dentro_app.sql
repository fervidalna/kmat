begin;

-- Previous free-text suggestions may mention activities outside the application.
-- Keep them in storage but make them eligible for replacement on explicit request.
alter table public.sugerencia_refuerzo_ia add column version_enfoque integer not null default 1 check (version_enfoque in (1, 2));

create or replace function public.reservar_sugerencia_ia(p_estudiante uuid, p_eje text, p_total integer)
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
  if r.version_enfoque = 2 and r.generada_en is not null and p_total >= r.intentos_al_generar
    and p_total < r.intentos_al_generar + 5 then return jsonb_build_object('status','cached'); end if;
  if r.reserva is not null and r.reserva_hasta > now() then return jsonb_build_object('status','busy'); end if;
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
  if p_total is null or p_total < 5 or char_length(trim(coalesce(p_tema,''))) not between 1 and 100
     or char_length(trim(coalesce(p_evidencia,''))) not between 1 and 240
     or char_length(trim(coalesce(p_propuesta,''))) not between 1 and 280 then
    raise exception 'Sugerencia inválida' using errcode='22023'; end if;
  if trim(p_propuesta) not in (
    'Asignar ejercicios disponibles de ' || trim(p_tema) || ' desde la app y revisar sus resultados.',
    'Revisar el ejemplo con el botón ? y asignar ejercicios disponibles de ' || trim(p_tema) || ' dentro de la app.'
  ) then raise exception 'La sugerencia debe usar actividades de la app' using errcode='22023'; end if;
  update public.sugerencia_refuerzo_ia set intentos_al_generar=p_total, tema=trim(p_tema),
    evidencia=trim(p_evidencia), propuesta=trim(p_propuesta), generada_en=now(),
    version_enfoque=2, reserva=null, reserva_hasta=null
    where id_estudiante=p_estudiante and eje=p_eje and reserva=p_reserva and reserva_hasta > now();
  return found;
end;
$$;
commit;
