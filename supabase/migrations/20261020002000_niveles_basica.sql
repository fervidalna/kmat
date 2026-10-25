-- Sustituye los niveles transicionales incorporados por error.

insert into public.nivel_educativo (codigo, nombre, orden) values
  -- Usar órdenes libres hasta borrar NT1/NT2, que aún ocupan 1 y 2.
  ('BASICO_1', '1° Básico', 3),
  ('BASICO_2', '2° Básico', 4)
on conflict (codigo) do update
set nombre = excluded.nombre,
    orden = excluded.orden;

insert into public.contenido_nivel_educativo (id_contenido, codigo_nivel_educativo)
select id_contenido,
       case codigo_nivel_educativo when 'NT1' then 'BASICO_1' else 'BASICO_2' end
from public.contenido_nivel_educativo
where codigo_nivel_educativo in ('NT1', 'NT2')
on conflict do nothing;

update public.estudiante
set codigo_nivel_educativo = case codigo_nivel_educativo
  when 'NT1' then 'BASICO_1'
  when 'NT2' then 'BASICO_2'
  else codigo_nivel_educativo
end
where codigo_nivel_educativo in ('NT1', 'NT2');

delete from public.contenido_nivel_educativo
where codigo_nivel_educativo in ('NT1', 'NT2');
delete from public.nivel_educativo
where codigo in ('NT1', 'NT2');

update public.nivel_educativo set orden = 1 where codigo = 'BASICO_1';
update public.nivel_educativo set orden = 2 where codigo = 'BASICO_2';
