-- K-MAT IA · Población inicial del MVP
--
-- Antes de ejecutar este script, crea estos tres usuarios adultos en
-- Supabase Dashboard > Authentication > Users:
--   administrador@kmat.local
--   padre.tomas@kmat.local
--   docente.nt2a@kmat.local
--
-- El estudiante es un perfil infantil en public.estudiante y no requiere
-- una cuenta en auth.users. Este archivo no modifica la estructura de datos
-- ni crea usuarios de Authentication: solo registra la población inicial.

begin;

do $$
declare
  correo text;
begin
  foreach correo in array array[
    'administrador@kmat.local',
    'padre.tomas@kmat.local',
    'docente.nt2a@kmat.local'
  ] loop
    if not exists (select 1 from auth.users where email = correo) then
      raise exception 'Cree primero el usuario % en Supabase Authentication.', correo;
    end if;
  end loop;
end $$;

-- Roles y perfiles adultos.
insert into public.rol (codigo, descripcion) values
  ('ADMINISTRADOR', 'Gestiona la configuración y los catálogos del sistema.'),
  ('DOCENTE', 'Puede supervisar grupos y asignar refuerzos.'),
  ('RESPONSABLE', 'Puede administrar y revisar a sus estudiantes vinculados.')
on conflict (codigo) do nothing;

insert into public.perfil (id_usuario, nombre_mostrado)
select id, case email
  when 'administrador@kmat.local' then 'Administradora K-Mat'
  when 'padre.tomas@kmat.local' then 'Carlos Pérez'
  when 'docente.nt2a@kmat.local' then 'Profesora Daniela Rojas'
end
from auth.users
where email in ('administrador@kmat.local', 'padre.tomas@kmat.local', 'docente.nt2a@kmat.local')
on conflict (id_usuario) do update
set nombre_mostrado = excluded.nombre_mostrado,
    actualizado_en = now(),
    version_registro = public.perfil.version_registro + 1;

insert into public.usuario_rol (id_usuario, codigo_rol)
select id, case email
  when 'administrador@kmat.local' then 'ADMINISTRADOR'
  when 'padre.tomas@kmat.local' then 'RESPONSABLE'
  when 'docente.nt2a@kmat.local' then 'DOCENTE'
end
from auth.users
where email in ('administrador@kmat.local', 'padre.tomas@kmat.local', 'docente.nt2a@kmat.local')
on conflict (id_usuario, codigo_rol) do nothing;

-- Catálogos pedagógicos iniciales del MVP.
insert into public.nivel_educativo (codigo, nombre, orden) values
  ('NT1', 'Nivel de Transicion 1', 1),
  ('NT2', 'Nivel de Transicion 2', 2)
on conflict (codigo) do nothing;

insert into public.nivel_aprendizaje (id_nivel, codigo, nombre, descripcion, orden) values
  ('00000000-0000-0000-0000-000000000101', 'NIVEL_1', 'Números hasta 10', 'Cuenta y reconoce cantidades pequeñas.', 1),
  ('00000000-0000-0000-0000-000000000102', 'NIVEL_2', 'Sumas simples', 'Combina cantidades hasta 10.', 2)
on conflict (id_nivel) do nothing;

insert into public.contenido (id_contenido, codigo, nombre, descripcion, orden) values
  ('00000000-0000-0000-0000-000000000201', 'CONTEO_1_10', 'Conteo del 1 al 10', 'Cuenta objetos y reconoce cantidades.', 1),
  ('00000000-0000-0000-0000-000000000202', 'SUMA_1_10', 'Sumas simples', 'Resuelve sumas con apoyo visual.', 2)
on conflict (id_contenido) do nothing;

insert into public.contenido_nivel_educativo (id_contenido, codigo_nivel_educativo) values
  ('00000000-0000-0000-0000-000000000201', 'NT1'),
  ('00000000-0000-0000-0000-000000000201', 'NT2'),
  ('00000000-0000-0000-0000-000000000202', 'NT2')
on conflict do nothing;

-- Recursos disponibles en el dispositivo para acompañar el primer contenido.
insert into public.recurso_apoyo (
  id_recurso, id_contenido, tipo_recurso, titulo, descripcion, recurso_uri,
  etapa_apoyo, duracion_segundos, disponible_offline, activo
) values
  ('00000000-0000-0000-0000-000000000251',
   '00000000-0000-0000-0000-000000000202',
   'PISTA', 'Cuenta las manzanas',
   'Cuenta una manzana y luego otra antes de responder.',
   'app://recursos/pista-contar-manzanas', 1, 15, true, true),
  ('00000000-0000-0000-0000-000000000252',
   '00000000-0000-0000-0000-000000000202',
   'PAUSA_ACTIVA', 'Pausa activa: tres saltos',
   'Levántate y salta tres veces antes de continuar.',
   'app://recursos/pausa-activa-tres-saltos', 1, 30, true, true)
on conflict (id_recurso) do update
set titulo = excluded.titulo,
    descripcion = excluded.descripcion,
    recurso_uri = excluded.recurso_uri,
    etapa_apoyo = excluded.etapa_apoyo,
    duracion_segundos = excluded.duracion_segundos,
    disponible_offline = excluded.disponible_offline,
    activo = excluded.activo,
    actualizado_en = now(),
    version_registro = public.recurso_apoyo.version_registro + 1;

-- Niño, responsable, sala y docente.
insert into public.estudiante (id_estudiante, nombre_mostrado, codigo_nivel_educativo) values
  ('00000000-0000-0000-0000-000000000301', 'Tomás Pérez', 'NT2')
on conflict (id_estudiante) do update
set nombre_mostrado = excluded.nombre_mostrado,
    codigo_nivel_educativo = excluded.codigo_nivel_educativo,
    actualizado_en = now(),
    version_registro = public.estudiante.version_registro + 1;

insert into public.responsable_estudiante (id_estudiante, id_responsable, tipo_vinculo, es_principal)
select '00000000-0000-0000-0000-000000000301', id, 'PADRE', true
from auth.users where email = 'padre.tomas@kmat.local'
on conflict (id_estudiante, id_responsable) do update
set tipo_vinculo = excluded.tipo_vinculo,
    es_principal = excluded.es_principal,
    activo = true;

insert into public.consentimiento (id_consentimiento, id_estudiante, id_responsable, proposito, version_documento, estado)
select '00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000301', id,
       'USO_PLATAFORMA', 'mvp-1.0', 'OTORGADO'
from auth.users where email = 'padre.tomas@kmat.local'
on conflict (id_consentimiento) do nothing;

insert into public.grupo (id_grupo, nombre, codigo_union) values
  ('00000000-0000-0000-0000-000000000501', 'Sala NT2 A', 'KMTNT2A')
on conflict (id_grupo) do update
set nombre = excluded.nombre,
    estado = 'ACTIVO',
    actualizado_en = now(),
    version_registro = public.grupo.version_registro + 1;

insert into public.docente_grupo (id_docente, id_grupo, tipo_participacion)
select id, '00000000-0000-0000-0000-000000000501', 'TITULAR'
from auth.users where email = 'docente.nt2a@kmat.local'
on conflict (id_docente, id_grupo) do update
set tipo_participacion = excluded.tipo_participacion,
    activo = true,
    fecha_fin = null;

insert into public.inscripcion_grupo (id_inscripcion, id_grupo, id_estudiante, estado) values
  ('00000000-0000-0000-0000-000000000601',
   '00000000-0000-0000-0000-000000000501',
   '00000000-0000-0000-0000-000000000301',
   'ACTIVA')
on conflict (id_inscripcion) do update
set estado = 'ACTIVA', fecha_fin = null;

-- Ejercicio disponible offline: 1 + 1 = 2.
insert into public.ejercicio (id_ejercicio, codigo) values
  ('00000000-0000-0000-0000-000000000701', 'SUMA_MANZANAS_1_MAS_1')
on conflict (id_ejercicio) do nothing;

insert into public.ejercicio_version (
  id_ejercicio_version, id_ejercicio, numero_version, id_contenido, id_nivel,
  tipo_ejercicio, tipo_respuesta, enunciado, solucion, configuracion,
  dificultad, disponible_offline, estado, publicado_en
) values (
  '00000000-0000-0000-0000-000000000702',
  '00000000-0000-0000-0000-000000000701',
  1,
  '00000000-0000-0000-0000-000000000202',
  '00000000-0000-0000-0000-000000000102',
  'SUMA', 'MANUSCRITA', 'Cuenta las manzanas: 1 + 1 = ?',
  '2'::jsonb,
  '{"objetos":"MANZANA","sumandos":[1,1]}'::jsonb,
  1, true, 'PUBLICADO', now()
)
on conflict (id_ejercicio_version) do nothing;

-- Diagnóstico y sesión de práctica que permiten ver avance en las vistas.
insert into public.sesion_aprendizaje (id_sesion, id_estudiante, tipo_sesion, estado, inicio_en, fin_en) values
  ('00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-000000000301', 'DIAGNOSTICO', 'COMPLETADA', now() - interval '2 days', now() - interval '2 days' + interval '8 minutes'),
  ('00000000-0000-0000-0000-000000000802', '00000000-0000-0000-0000-000000000301', 'PRACTICA', 'COMPLETADA', now() - interval '1 day', now() - interval '1 day' + interval '4 minutes'),
  ('00000000-0000-0000-0000-000000000803', '00000000-0000-0000-0000-000000000301', 'REFUERZO', 'EN_CURSO', now(), null)
on conflict (id_sesion) do nothing;

insert into public.resultado_diagnostico (id_sesion, id_estudiante, id_nivel_asignado, regla_version) values
  ('00000000-0000-0000-0000-000000000801',
   '00000000-0000-0000-0000-000000000301',
   '00000000-0000-0000-0000-000000000102',
   'mvp-1.0')
on conflict (id_sesion) do nothing;

insert into public.asignacion_ejercicio (
  id_asignacion, id_estudiante, id_ejercicio_version, id_sesion,
  origen, estado, asignado_en, iniciado_en, completado_en
) values (
  '00000000-0000-0000-0000-000000000901',
  '00000000-0000-0000-0000-000000000301',
  '00000000-0000-0000-0000-000000000702',
  '00000000-0000-0000-0000-000000000802',
  'MOTOR', 'COMPLETADA',
  now() - interval '1 day', now() - interval '1 day', now() - interval '1 day' + interval '2 minutes'
)
on conflict (id_asignacion) do nothing;

insert into public.intento_ejercicio (
  id_intento, id_asignacion, numero_intento, respuesta_reconocida,
  respuesta_final, es_correcta, tiempo_respuesta_ms, respondido_en
) values
  ('00000000-0000-0000-0000-000000000a01',
   '00000000-0000-0000-0000-000000000901', 1, '1',
   '{"respuesta":"1","trazos":[]}'::jsonb, false, 12000,
   now() - interval '1 day' + interval '1 minute'),
  ('00000000-0000-0000-0000-000000000a02',
   '00000000-0000-0000-0000-000000000901', 2, '2',
   '{"respuesta":"2","trazos":["120,80,0;125,85,30"]}'::jsonb, true, 8500,
   now() - interval '1 day' + interval '2 minutes')
on conflict (id_intento) do nothing;

insert into public.refuerzo_docente (
  id_refuerzo, id_docente, id_estudiante, id_contenido, id_recurso, tipo_refuerzo,
  cantidad_ejercicios, estado, observacion
)
select '00000000-0000-0000-0000-000000000b01', id,
       '00000000-0000-0000-0000-000000000301',
       '00000000-0000-0000-0000-000000000202',
       '00000000-0000-0000-0000-000000000251',
       'MAS_EJERCICIOS', 3, 'ACTIVO', 'Practicar sumas con apoyo visual.'
from auth.users where email = 'docente.nt2a@kmat.local'
on conflict (id_refuerzo) do update
set id_recurso = excluded.id_recurso,
    tipo_refuerzo = excluded.tipo_refuerzo,
    cantidad_ejercicios = excluded.cantidad_ejercicios,
    estado = excluded.estado,
    observacion = excluded.observacion,
    actualizado_en = now(),
    version_registro = public.refuerzo_docente.version_registro + 1;

-- Cola pendiente de la docente para la sesión de refuerzo actual.
insert into public.asignacion_ejercicio (
  id_asignacion, id_estudiante, id_ejercicio_version, id_sesion, id_refuerzo,
  origen, prioridad, orden_cola, estado, asignado_en
) values (
  '00000000-0000-0000-0000-000000000902',
  '00000000-0000-0000-0000-000000000301',
  '00000000-0000-0000-0000-000000000702',
  '00000000-0000-0000-0000-000000000803',
  '00000000-0000-0000-0000-000000000b01',
  'DOCENTE', 1, 1, 'PENDIENTE', now()
)
on conflict (id_asignacion) do update
set estado = excluded.estado,
    prioridad = excluded.prioridad,
    orden_cola = excluded.orden_cola,
    actualizado_en = now(),
    version_registro = public.asignacion_ejercicio.version_registro + 1;

-- La primera respuesta incorrecta gatilla una pausa de apoyo registrada.
insert into public.deteccion_dificultad (
  id_deteccion, id_asignacion, tipo_deteccion, errores_consecutivos,
  intentos_recientes, tiempo_promedio_ms, nivel_severidad, estado, motivo,
  detectado_en, resuelto_en
) values (
  '00000000-0000-0000-0000-000000000c01',
  '00000000-0000-0000-0000-000000000901',
  'COMBINADA', 1, 1, 12000, 'BAJA', 'RESUELTA',
  'La primera respuesta fue incorrecta y el tiempo superó el esperado.',
  now() - interval '1 day' + interval '1 minute',
  now() - interval '1 day' + interval '1 minute 30 seconds'
)
on conflict (id_deteccion) do update
set estado = excluded.estado,
    motivo = excluded.motivo,
    resuelto_en = excluded.resuelto_en;

insert into public.intervencion_apoyo (
  id_intervencion, id_asignacion, id_intento, id_deteccion, id_recurso,
  origen, resultado, mostrado_en, observacion
) values (
  '00000000-0000-0000-0000-000000000d01',
  '00000000-0000-0000-0000-000000000901',
  '00000000-0000-0000-0000-000000000a01',
  '00000000-0000-0000-0000-000000000c01',
  '00000000-0000-0000-0000-000000000252',
  'MOTOR', 'COMPLETADO', now() - interval '1 day' + interval '1 minute 30 seconds',
  'Pausa activa realizada antes del segundo intento correcto.'
)
on conflict (id_intervencion) do update
set resultado = excluded.resultado,
    observacion = excluded.observacion;

commit;
