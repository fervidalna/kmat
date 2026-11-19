begin;

-- Every explicit request can reserve the next generation once the previous one ends.
create function public.reservar_sugerencia_docente(p_estudiante uuid, p_eje text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare r public.sugerencia_refuerzo_ia%rowtype;
begin
  insert into public.sugerencia_refuerzo_ia(id_estudiante, eje) values (p_estudiante, p_eje)
    on conflict (id_estudiante, eje) do nothing;
  update public.sugerencia_refuerzo_ia
    set reserva=gen_random_uuid(), reserva_hasta=now()+interval '90 seconds'
    where id_estudiante=p_estudiante and eje=p_eje
      and (reserva is null or reserva_hasta <= now())
    returning * into r;
  if not found then return jsonb_build_object('status','busy'); end if;
  return jsonb_build_object('status','reserved','lease',r.reserva,'topic',r.tema,'activity',r.propuesta);
end;
$$;
revoke all on function public.reservar_sugerencia_docente(uuid,text) from public, anon, authenticated;
grant execute on function public.reservar_sugerencia_docente(uuid,text) to service_role;
commit;
