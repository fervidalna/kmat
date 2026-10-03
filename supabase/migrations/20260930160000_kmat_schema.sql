-- K-MAT IA - Esquema PostgreSQL / Supabase
-- Migracion inicial. Ejecutar mediante Supabase CLI o SQL Editor.
-- IDs generados localmente (UUID) para permitir sincronizacion offline-first.

create extension if not exists pgcrypto;

-- ============================================================================
-- IDENTIDAD, ROLES Y PRIVACIDAD
-- ============================================================================

create table public.perfil (
  id_usuario uuid primary key references auth.users(id) on delete cascade,
  nombre_mostrado text not null check (char_length(nombre_mostrado) between 1 and 100),
  estado text not null default 'ACTIVO' check (estado in ('ACTIVO', 'INACTIVO')),
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0)
);

create table public.rol (
  codigo text primary key,
  descripcion text not null
);

insert into public.rol (codigo, descripcion) values
  ('DOCENTE', 'Puede supervisar grupos y asignar refuerzos.'),
  ('RESPONSABLE', 'Puede administrar y revisar a sus estudiantes vinculados.')
on conflict (codigo) do nothing;

create table public.usuario_rol (
  id_usuario uuid not null references public.perfil(id_usuario) on delete cascade,
  codigo_rol text not null references public.rol(codigo),
  asignado_en timestamptz not null default now(),
  primary key (id_usuario, codigo_rol)
);

create table public.nivel_educativo (
  codigo text primary key,
  nombre text not null unique,
  orden smallint not null unique check (orden > 0)
);

insert into public.nivel_educativo (codigo, nombre, orden) values
  ('NT1', 'Nivel de Transicion 1', 1),
  ('NT2', 'Nivel de Transicion 2', 2)
on conflict (codigo) do nothing;

create table public.nivel_aprendizaje (
  id_nivel uuid primary key default gen_random_uuid(),
  codigo text not null unique,
  nombre text not null,
  descripcion text,
  orden integer not null unique check (orden > 0),
  activo boolean not null default true,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0)
);

create table public.estudiante (
  id_estudiante uuid primary key default gen_random_uuid(),
  nombre_mostrado text not null check (char_length(nombre_mostrado) between 1 and 100),
  avatar_url text,
  codigo_nivel_educativo text not null references public.nivel_educativo(codigo),
  estado text not null default 'ACTIVO' check (estado in ('ACTIVO', 'INACTIVO')),
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0)
);

-- Un estudiante puede tener varios responsables. El indice parcial asegura
-- solamente uno principal, sin eliminar el historial ni otros responsables.
create table public.responsable_estudiante (
  id_estudiante uuid not null references public.estudiante(id_estudiante) on delete cascade,
  id_responsable uuid not null references public.perfil(id_usuario) on delete restrict,
  tipo_vinculo text not null default 'RESPONSABLE' check (tipo_vinculo in ('MADRE', 'PADRE', 'TUTOR', 'RESPONSABLE')),
  es_principal boolean not null default false,
  activo boolean not null default true,
  creado_en timestamptz not null default now(),
  primary key (id_estudiante, id_responsable)
);

create unique index ux_responsable_principal_por_estudiante
  on public.responsable_estudiante (id_estudiante)
  where es_principal and activo;

-- La relacion docente-grupo evita que GRUPO contenga un docente fijo y permite
-- co-docencia e historial de membresias.
create table public.grupo (
  id_grupo uuid primary key default gen_random_uuid(),
  nombre text not null check (char_length(nombre) between 1 and 100),
  codigo_union text not null unique check (char_length(codigo_union) between 6 and 20),
  estado text not null default 'ACTIVO' check (estado in ('ACTIVO', 'INACTIVO')),
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0)
);

create table public.docente_grupo (
  id_docente uuid not null references public.perfil(id_usuario) on delete restrict,
  id_grupo uuid not null references public.grupo(id_grupo) on delete cascade,
  tipo_participacion text not null default 'TITULAR' check (tipo_participacion in ('TITULAR', 'APOYO')),
  activo boolean not null default true,
  fecha_inicio timestamptz not null default now(),
  fecha_fin timestamptz,
  primary key (id_docente, id_grupo),
  check (fecha_fin is null or fecha_fin >= fecha_inicio)
);

create unique index ux_docente_titular_activo_por_grupo
  on public.docente_grupo (id_grupo)
  where tipo_participacion = 'TITULAR' and activo;

create table public.inscripcion_grupo (
  id_inscripcion uuid primary key default gen_random_uuid(),
  id_grupo uuid not null references public.grupo(id_grupo) on delete restrict,
  id_estudiante uuid not null references public.estudiante(id_estudiante) on delete restrict,
  estado text not null default 'ACTIVA' check (estado in ('ACTIVA', 'FINALIZADA', 'RETIRADA')),
  fecha_inicio timestamptz not null default now(),
  fecha_fin timestamptz,
  creado_en timestamptz not null default now(),
  check (fecha_fin is null or fecha_fin >= fecha_inicio)
);

create unique index ux_inscripcion_activa_por_grupo_estudiante
  on public.inscripcion_grupo (id_grupo, id_estudiante)
  where estado = 'ACTIVA';

create table public.consentimiento (
  id_consentimiento uuid primary key default gen_random_uuid(),
  id_estudiante uuid not null,
  id_responsable uuid not null,
  proposito text not null check (proposito in ('USO_PLATAFORMA', 'ANALITICA', 'RECURSOS_EXTERNOS')),
  version_documento text not null,
  estado text not null check (estado in ('OTORGADO', 'REVOCADO')),
  otorgado_en timestamptz not null default now(),
  revocado_en timestamptz,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0),
  foreign key (id_estudiante, id_responsable)
    references public.responsable_estudiante(id_estudiante, id_responsable),
  check ((estado = 'OTORGADO' and revocado_en is null) or
         (estado = 'REVOCADO' and revocado_en is not null and revocado_en >= otorgado_en))
);

create unique index ux_consentimiento_vigente
  on public.consentimiento (id_estudiante, id_responsable, proposito)
  where estado = 'OTORGADO';

-- ============================================================================
-- CURRICULO, EJERCICIOS Y RECURSOS
-- ============================================================================

create table public.contenido (
  id_contenido uuid primary key default gen_random_uuid(),
  codigo text not null unique,
  nombre text not null,
  descripcion text,
  orden integer not null check (orden > 0),
  activo boolean not null default true,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0)
);

-- Un contenido puede trabajarse en NT1 y NT2 sin duplicar su definicion.
create table public.contenido_nivel_educativo (
  id_contenido uuid not null references public.contenido(id_contenido) on delete cascade,
  codigo_nivel_educativo text not null references public.nivel_educativo(codigo),
  primary key (id_contenido, codigo_nivel_educativo)
);

create table public.ejercicio (
  id_ejercicio uuid primary key default gen_random_uuid(),
  codigo text not null unique,
  creado_en timestamptz not null default now(),
  retirado_en timestamptz
);

-- Las versiones son inmutables. Una correccion genera una nueva fila, por lo
-- que una asignacion siempre conserva exactamente el ejercicio que se resolvio.
create table public.ejercicio_version (
  id_ejercicio_version uuid primary key default gen_random_uuid(),
  id_ejercicio uuid not null references public.ejercicio(id_ejercicio) on delete restrict,
  numero_version integer not null check (numero_version > 0),
  id_contenido uuid not null references public.contenido(id_contenido) on delete restrict,
  id_nivel uuid not null references public.nivel_aprendizaje(id_nivel) on delete restrict,
  tipo_ejercicio text not null check (tipo_ejercicio in ('CONTEO', 'SECUENCIA', 'CONTEO_SALTOS', 'COMPARACION', 'CLASIFICACION', 'PATRON', 'SUMA', 'RESTA', 'PROBLEMA', 'ORDENAR')),
  tipo_respuesta text not null check (tipo_respuesta in ('MANUSCRITA', 'SELECCION', 'ORDENAMIENTO')),
  enunciado text not null,
  solucion jsonb not null check (jsonb_typeof(solucion) in ('string', 'number', 'array', 'object')),
  configuracion jsonb not null default '{}'::jsonb check (jsonb_typeof(configuracion) = 'object'),
  dificultad smallint not null check (dificultad between 1 and 5),
  recurso_uri text,
  disponible_offline boolean not null default true,
  estado text not null default 'BORRADOR' check (estado in ('BORRADOR', 'PUBLICADO', 'INACTIVO')),
  publicado_en timestamptz,
  creado_en timestamptz not null default now(),
  unique (id_ejercicio, numero_version)
);

create table public.recurso_apoyo (
  id_recurso uuid primary key default gen_random_uuid(),
  id_contenido uuid references public.contenido(id_contenido) on delete restrict,
  tipo_recurso text not null check (tipo_recurso in ('PAUSA_ACTIVA', 'PISTA', 'ACTIVIDAD_ALTERNATIVA', 'JUEGO', 'VIDEO', 'MATERIAL_APOYO')),
  titulo text not null,
  descripcion text,
  recurso_uri text,
  etapa_apoyo smallint not null default 1 check (etapa_apoyo between 1 and 5),
  duracion_segundos integer check (duracion_segundos is null or duracion_segundos >= 0),
  disponible_offline boolean not null default true,
  activo boolean not null default true,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0)
);

-- ============================================================================
-- APRENDIZAJE, DIAGNOSTICO Y ADAPTACION
-- ============================================================================

create table public.sesion_aprendizaje (
  id_sesion uuid primary key default gen_random_uuid(),
  id_estudiante uuid not null references public.estudiante(id_estudiante) on delete restrict,
  tipo_sesion text not null check (tipo_sesion in ('DIAGNOSTICO', 'PRACTICA', 'REFUERZO')),
  estado text not null default 'EN_CURSO' check (estado in ('EN_CURSO', 'COMPLETADA', 'INTERRUMPIDA')),
  inicio_en timestamptz not null default now(),
  fin_en timestamptz,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0),
  unique (id_sesion, id_estudiante),
  check (fin_en is null or fin_en >= inicio_en)
);

-- Resultado de diagnostico separado de la sesion para no repetir atributos de
-- sesiones de practica. El nivel vigente se obtiene desde el ultimo resultado.
create table public.resultado_diagnostico (
  id_sesion uuid primary key,
  id_estudiante uuid not null,
  id_nivel_asignado uuid not null references public.nivel_aprendizaje(id_nivel) on delete restrict,
  regla_version text not null,
  calculado_en timestamptz not null default now(),
  foreign key (id_sesion, id_estudiante)
    references public.sesion_aprendizaje(id_sesion, id_estudiante)
);

create table public.refuerzo_docente (
  id_refuerzo uuid primary key default gen_random_uuid(),
  id_docente uuid not null references public.perfil(id_usuario) on delete restrict,
  id_estudiante uuid not null references public.estudiante(id_estudiante) on delete restrict,
  id_contenido uuid not null references public.contenido(id_contenido) on delete restrict,
  id_recurso uuid references public.recurso_apoyo(id_recurso) on delete restrict,
  tipo_refuerzo text not null check (tipo_refuerzo in ('MAS_EJERCICIOS', 'ACTIVIDAD_ALTERNATIVA', 'JUEGO', 'VIDEO', 'MATERIAL_APOYO')),
  cantidad_ejercicios integer check (cantidad_ejercicios is null or cantidad_ejercicios > 0),
  estado text not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'ACTIVO', 'COMPLETADO', 'CANCELADO')),
  observacion text,
  asignado_en timestamptz not null default now(),
  completado_en timestamptz,
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0),
  unique (id_refuerzo, id_estudiante),
  check (completado_en is null or completado_en >= asignado_en)
);

-- Las FK compuestas obligan que sesion y refuerzo pertenezcan al mismo alumno.
create table public.asignacion_ejercicio (
  id_asignacion uuid primary key default gen_random_uuid(),
  id_estudiante uuid not null references public.estudiante(id_estudiante) on delete restrict,
  id_ejercicio_version uuid not null references public.ejercicio_version(id_ejercicio_version) on delete restrict,
  id_sesion uuid,
  id_refuerzo uuid,
  origen text not null check (origen in ('DIAGNOSTICO', 'MOTOR', 'DOCENTE')),
  prioridad smallint not null default 1 check (prioridad > 0),
  orden_cola integer check (orden_cola is null or orden_cola > 0),
  estado text not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'EN_CURSO', 'COMPLETADA', 'CANCELADA')),
  asignado_en timestamptz not null default now(),
  iniciado_en timestamptz,
  completado_en timestamptz,
  actualizado_en timestamptz not null default now(),
  version_registro integer not null default 1 check (version_registro > 0),
  unique (id_asignacion, id_estudiante),
  foreign key (id_sesion, id_estudiante)
    references public.sesion_aprendizaje(id_sesion, id_estudiante),
  foreign key (id_refuerzo, id_estudiante)
    references public.refuerzo_docente(id_refuerzo, id_estudiante),
  check ((origen = 'DOCENTE') = (id_refuerzo is not null)),
  check (iniciado_en is null or iniciado_en >= asignado_en),
  check (completado_en is null or completado_en >= coalesce(iniciado_en, asignado_en))
);

create table public.intento_ejercicio (
  id_intento uuid primary key default gen_random_uuid(),
  id_asignacion uuid not null references public.asignacion_ejercicio(id_asignacion) on delete restrict,
  numero_intento integer not null check (numero_intento > 0),
  respuesta_reconocida text,
  respuesta_final jsonb,
  confianza_reconocimiento numeric(5,4) check (confianza_reconocimiento between 0 and 1),
  requirio_confirmacion boolean not null default false,
  es_correcta boolean,
  tiempo_respuesta_ms integer check (tiempo_respuesta_ms is null or tiempo_respuesta_ms >= 0),
  respondido_en timestamptz not null default now(),
  creado_en timestamptz not null default now(),
  unique (id_asignacion, numero_intento),
  unique (id_intento, id_asignacion),
  check (respuesta_final is null or jsonb_typeof(respuesta_final) in ('string', 'number', 'array', 'object'))
);

create table public.deteccion_dificultad (
  id_deteccion uuid primary key default gen_random_uuid(),
  id_asignacion uuid not null references public.asignacion_ejercicio(id_asignacion) on delete restrict,
  tipo_deteccion text not null check (tipo_deteccion in ('TIEMPO_ELEVADO', 'ERRORES_CONSECUTIVOS', 'MULTIPLES_INTENTOS', 'DESEMPENO_RECIENTE', 'COMBINADA')),
  errores_consecutivos integer check (errores_consecutivos is null or errores_consecutivos >= 0),
  intentos_recientes integer check (intentos_recientes is null or intentos_recientes >= 0),
  tiempo_promedio_ms integer check (tiempo_promedio_ms is null or tiempo_promedio_ms >= 0),
  nivel_severidad text not null check (nivel_severidad in ('BAJA', 'MEDIA', 'ALTA')),
  estado text not null default 'ABIERTA' check (estado in ('ABIERTA', 'RESUELTA')),
  motivo text,
  detectado_en timestamptz not null default now(),
  resuelto_en timestamptz,
  unique (id_deteccion, id_asignacion),
  check (resuelto_en is null or resuelto_en >= detectado_en)
);

-- Toda intervencion queda anclada a una asignacion. Las FK compuestas impiden
-- que se mezcle el intento o la deteccion de otro estudiante/asignacion.
create table public.intervencion_apoyo (
  id_intervencion uuid primary key default gen_random_uuid(),
  id_asignacion uuid not null references public.asignacion_ejercicio(id_asignacion) on delete restrict,
  id_intento uuid,
  id_deteccion uuid,
  id_recurso uuid not null references public.recurso_apoyo(id_recurso) on delete restrict,
  origen text not null check (origen in ('MOTOR', 'DOCENTE', 'ESTUDIANTE')),
  resultado text not null default 'MOSTRADO' check (resultado in ('MOSTRADO', 'COMPLETADO', 'OMITIDO', 'ABANDONADO')),
  mostrado_en timestamptz not null default now(),
  observacion text,
  foreign key (id_intento, id_asignacion)
    references public.intento_ejercicio(id_intento, id_asignacion),
  foreign key (id_deteccion, id_asignacion)
    references public.deteccion_dificultad(id_deteccion, id_asignacion)
);

-- Bitacora append-only. El objeto afectado es polimorfico y por ello se guarda
-- como tipo + UUID, sin fingir una FK que PostgreSQL no puede garantizar.
create table public.auditoria (
  id_auditoria bigint generated always as identity primary key,
  id_actor uuid references public.perfil(id_usuario) on delete set null,
  entidad text not null,
  id_entidad uuid,
  accion text not null,
  datos_anteriores jsonb,
  datos_nuevos jsonb,
  creado_en timestamptz not null default now()
);

-- ============================================================================
-- INTEGRIDAD ADICIONAL Y TRAZABILIDAD
-- ============================================================================

create or replace function public.tocar_registro()
returns trigger
language plpgsql
as $$
begin
  new.actualizado_en := now();
  new.version_registro := old.version_registro + 1;
  return new;
end;
$$;

create or replace function public.exigir_rol_responsable()
returns trigger
language plpgsql
as $$
begin
  if not exists (
    select 1 from public.usuario_rol
    where id_usuario = new.id_responsable and codigo_rol = 'RESPONSABLE'
  ) then
    raise exception 'El usuario % no tiene rol RESPONSABLE', new.id_responsable;
  end if;
  return new;
end;
$$;

create or replace function public.exigir_rol_docente()
returns trigger
language plpgsql
as $$
begin
  if not exists (
    select 1 from public.usuario_rol
    where id_usuario = new.id_docente and codigo_rol = 'DOCENTE'
  ) then
    raise exception 'El usuario % no tiene rol DOCENTE', new.id_docente;
  end if;
  return new;
end;
$$;

create or replace function public.validar_refuerzo_docente()
returns trigger
language plpgsql
as $$
begin
  if not exists (
    select 1
    from public.docente_grupo dg
    join public.inscripcion_grupo ig on ig.id_grupo = dg.id_grupo
    where dg.id_docente = new.id_docente
      and dg.activo
      and ig.id_estudiante = new.id_estudiante
      and ig.estado = 'ACTIVA'
  ) then
    raise exception 'El docente debe tener una relacion activa con el estudiante reforzado';
  end if;
  return new;
end;
$$;

create or replace function public.validar_resultado_diagnostico()
returns trigger
language plpgsql
as $$
begin
  if not exists (
    select 1 from public.sesion_aprendizaje
    where id_sesion = new.id_sesion
      and id_estudiante = new.id_estudiante
      and tipo_sesion = 'DIAGNOSTICO'
      and estado = 'COMPLETADA'
  ) then
    raise exception 'El resultado requiere una sesion diagnostica completada del mismo estudiante';
  end if;
  return new;
end;
$$;

create or replace function public.validar_asignacion_ejercicio()
returns trigger
language plpgsql
as $$
declare
  v_tipo_sesion text;
  v_estado_version text;
begin
  select estado into v_estado_version
  from public.ejercicio_version
  where id_ejercicio_version = new.id_ejercicio_version;

  if v_estado_version <> 'PUBLICADO' then
    raise exception 'Solo se pueden asignar versiones de ejercicio PUBLICADAS';
  end if;

  if new.id_sesion is not null then
    select tipo_sesion into v_tipo_sesion
    from public.sesion_aprendizaje
    where id_sesion = new.id_sesion and id_estudiante = new.id_estudiante;
  end if;

  if new.origen = 'DIAGNOSTICO' and (new.id_sesion is null or v_tipo_sesion <> 'DIAGNOSTICO') then
    raise exception 'Una asignacion diagnostica requiere una sesion DIAGNOSTICO del mismo estudiante';
  end if;

  return new;
end;
$$;

create or replace function public.registrar_auditoria()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nuevo jsonb;
  v_anterior jsonb;
  v_id uuid;
begin
  if tg_op <> 'DELETE' then
    v_nuevo := to_jsonb(new);
    v_id := nullif(v_nuevo ->> tg_argv[0], '')::uuid;
  end if;
  if tg_op <> 'INSERT' then
    v_anterior := to_jsonb(old);
    v_id := coalesce(v_id, nullif(v_anterior ->> tg_argv[0], '')::uuid);
  end if;

  insert into public.auditoria (id_actor, entidad, id_entidad, accion, datos_anteriores, datos_nuevos)
  values (auth.uid(), tg_table_name, v_id, tg_op, v_anterior, v_nuevo);

  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

create or replace function public.proteger_version_ejercicio()
returns trigger
language plpgsql
as $$
begin
  if new.id_ejercicio <> old.id_ejercicio
     or new.numero_version <> old.numero_version
     or new.id_contenido <> old.id_contenido
     or new.id_nivel <> old.id_nivel
     or new.tipo_ejercicio <> old.tipo_ejercicio
     or new.tipo_respuesta <> old.tipo_respuesta
     or new.enunciado <> old.enunciado
     or new.solucion <> old.solucion
     or new.configuracion <> old.configuracion
     or new.dificultad <> old.dificultad
     or new.recurso_uri is distinct from old.recurso_uri
     or new.disponible_offline <> old.disponible_offline then
    raise exception 'Una version de ejercicio es inmutable; cree una nueva version';
  end if;
  return new;
end;
$$;

create trigger trg_responsable_rol
before insert or update of id_responsable on public.responsable_estudiante
for each row execute function public.exigir_rol_responsable();

create trigger trg_docente_grupo_rol
before insert or update of id_docente on public.docente_grupo
for each row execute function public.exigir_rol_docente();

create trigger trg_refuerzo_docente_rol
before insert or update of id_docente on public.refuerzo_docente
for each row execute function public.exigir_rol_docente();

create trigger trg_refuerzo_docente_acceso
before insert or update of id_docente, id_estudiante on public.refuerzo_docente
for each row execute function public.validar_refuerzo_docente();

create trigger trg_resultado_diagnostico
before insert or update on public.resultado_diagnostico
for each row execute function public.validar_resultado_diagnostico();

create trigger trg_validar_asignacion
before insert or update of id_estudiante, id_ejercicio_version, id_sesion, id_refuerzo, origen
on public.asignacion_ejercicio
for each row execute function public.validar_asignacion_ejercicio();

create trigger trg_proteger_version_ejercicio
before update on public.ejercicio_version
for each row execute function public.proteger_version_ejercicio();

create trigger trg_perfil_actualizado before update on public.perfil
for each row execute function public.tocar_registro();
create trigger trg_nivel_actualizado before update on public.nivel_aprendizaje
for each row execute function public.tocar_registro();
create trigger trg_estudiante_actualizado before update on public.estudiante
for each row execute function public.tocar_registro();
create trigger trg_grupo_actualizado before update on public.grupo
for each row execute function public.tocar_registro();
create trigger trg_consentimiento_actualizado before update on public.consentimiento
for each row execute function public.tocar_registro();
create trigger trg_contenido_actualizado before update on public.contenido
for each row execute function public.tocar_registro();
create trigger trg_recurso_actualizado before update on public.recurso_apoyo
for each row execute function public.tocar_registro();
create trigger trg_sesion_actualizado before update on public.sesion_aprendizaje
for each row execute function public.tocar_registro();
create trigger trg_refuerzo_actualizado before update on public.refuerzo_docente
for each row execute function public.tocar_registro();
create trigger trg_asignacion_actualizado before update on public.asignacion_ejercicio
for each row execute function public.tocar_registro();

create trigger trg_auditar_consentimiento
after insert or update or delete on public.consentimiento
for each row execute function public.registrar_auditoria('id_consentimiento');
create trigger trg_auditar_grupo
after insert or update or delete on public.grupo
for each row execute function public.registrar_auditoria('id_grupo');
create trigger trg_auditar_refuerzo
after insert or update or delete on public.refuerzo_docente
for each row execute function public.registrar_auditoria('id_refuerzo');
create trigger trg_auditar_asignacion
after insert or update or delete on public.asignacion_ejercicio
for each row execute function public.registrar_auditoria('id_asignacion');

-- ============================================================================
-- INDICES Y VISTAS DE LECTURA
-- ============================================================================

create index ix_responsable_estudiante_responsable_activo
  on public.responsable_estudiante (id_responsable, id_estudiante) where activo;
create index ix_docente_grupo_docente_activo
  on public.docente_grupo (id_docente, id_grupo) where activo;
create index ix_inscripcion_estudiante_activa
  on public.inscripcion_grupo (id_estudiante, id_grupo) where estado = 'ACTIVA';
create index ix_ejercicio_version_busqueda
  on public.ejercicio_version (id_contenido, id_nivel, estado, dificultad);
create index ix_recurso_apoyo_busqueda
  on public.recurso_apoyo (id_contenido, tipo_recurso, etapa_apoyo) where activo;
create index ix_sesion_estudiante_inicio
  on public.sesion_aprendizaje (id_estudiante, inicio_en desc);
create index ix_refuerzo_estudiante_estado
  on public.refuerzo_docente (id_estudiante, estado, asignado_en desc);
create index ix_asignacion_cola
  on public.asignacion_ejercicio (id_estudiante, estado, prioridad, orden_cola);
create index ix_intento_asignacion_numero
  on public.intento_ejercicio (id_asignacion, numero_intento);
create index ix_deteccion_asignacion_estado
  on public.deteccion_dificultad (id_asignacion, estado, detectado_en desc);
create index ix_intervencion_asignacion_fecha
  on public.intervencion_apoyo (id_asignacion, mostrado_en desc);
create index ix_auditoria_entidad_fecha
  on public.auditoria (entidad, id_entidad, creado_en desc);

-- Progreso calculado: evita duplicar aciertos, errores e intentos historicos.
create or replace view public.v_progreso_contenido with (security_invoker = true) as
select
  a.id_estudiante,
  ev.id_contenido,
  count(i.id_intento) as intentos_totales,
  count(*) filter (where i.es_correcta) as aciertos,
  count(*) filter (where i.es_correcta = false) as errores,
  round(avg(i.tiempo_respuesta_ms) filter (where i.tiempo_respuesta_ms is not null), 0) as tiempo_promedio_ms,
  max(i.respondido_en) as ultimo_intento_en
from public.asignacion_ejercicio a
join public.ejercicio_version ev on ev.id_ejercicio_version = a.id_ejercicio_version
join public.intento_ejercicio i on i.id_asignacion = a.id_asignacion
group by a.id_estudiante, ev.id_contenido;

create or replace view public.v_nivel_actual_estudiante with (security_invoker = true) as
select distinct on (rd.id_estudiante)
  rd.id_estudiante,
  rd.id_nivel_asignado,
  rd.calculado_en
from public.resultado_diagnostico rd
order by rd.id_estudiante, rd.calculado_en desc;

-- ============================================================================
-- SEGURIDAD: RLS Y FUNCIONES DE AUTORIZACION
-- ============================================================================

create or replace function public.es_responsable_de(p_estudiante uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.responsable_estudiante re
    where re.id_estudiante = p_estudiante
      and re.id_responsable = auth.uid()
      and re.activo
  );
$$;

create or replace function public.es_docente_de(p_estudiante uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.docente_grupo dg
    join public.inscripcion_grupo ig on ig.id_grupo = dg.id_grupo
    where dg.id_docente = auth.uid()
      and dg.activo
      and ig.id_estudiante = p_estudiante
      and ig.estado = 'ACTIVA'
  );
$$;

create or replace function public.es_docente()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.usuario_rol ur
    where ur.id_usuario = auth.uid() and ur.codigo_rol = 'DOCENTE'
  );
$$;

revoke all on function public.es_responsable_de(uuid) from public;
revoke all on function public.es_docente_de(uuid) from public;
revoke all on function public.es_docente() from public;
grant execute on function public.es_responsable_de(uuid) to authenticated;
grant execute on function public.es_docente_de(uuid) to authenticated;
grant execute on function public.es_docente() to authenticated;

alter table public.perfil enable row level security;
alter table public.rol enable row level security;
alter table public.usuario_rol enable row level security;
alter table public.estudiante enable row level security;
alter table public.responsable_estudiante enable row level security;
alter table public.grupo enable row level security;
alter table public.docente_grupo enable row level security;
alter table public.inscripcion_grupo enable row level security;
alter table public.consentimiento enable row level security;
alter table public.sesion_aprendizaje enable row level security;
alter table public.resultado_diagnostico enable row level security;
alter table public.refuerzo_docente enable row level security;
alter table public.asignacion_ejercicio enable row level security;
alter table public.intento_ejercicio enable row level security;
alter table public.deteccion_dificultad enable row level security;
alter table public.intervencion_apoyo enable row level security;
alter table public.auditoria enable row level security;
alter table public.nivel_educativo enable row level security;
alter table public.nivel_aprendizaje enable row level security;
alter table public.contenido enable row level security;
alter table public.contenido_nivel_educativo enable row level security;
alter table public.ejercicio enable row level security;
alter table public.ejercicio_version enable row level security;
alter table public.recurso_apoyo enable row level security;

-- Catalogos: lectura para usuarios autenticados. Escrituras solo mediante
-- backend con service_role o una migracion administrada.
create policy "roles_lectura_autenticada" on public.rol for select to authenticated using (true);
create policy "catalogos_lectura_autenticada" on public.nivel_educativo for select to authenticated using (true);
create policy "niveles_lectura_autenticada" on public.nivel_aprendizaje for select to authenticated using (true);
create policy "contenidos_lectura_autenticada" on public.contenido for select to authenticated using (true);
create policy "contenido_nivel_lectura_autenticada" on public.contenido_nivel_educativo for select to authenticated using (true);
create policy "ejercicios_lectura_autenticada" on public.ejercicio for select to authenticated using (true);
create policy "versiones_publicadas_lectura" on public.ejercicio_version for select to authenticated using (estado = 'PUBLICADO');
create policy "recursos_activos_lectura" on public.recurso_apoyo for select to authenticated using (activo);

create policy "perfil_propio_lectura" on public.perfil for select to authenticated using (id_usuario = auth.uid());
create policy "perfil_propio_actualizacion" on public.perfil for update to authenticated using (id_usuario = auth.uid()) with check (id_usuario = auth.uid());
create policy "roles_propios_lectura" on public.usuario_rol for select to authenticated using (id_usuario = auth.uid());

create policy "estudiante_lectura_autorizada" on public.estudiante for select to authenticated
  using (public.es_responsable_de(id_estudiante) or public.es_docente_de(id_estudiante));
create policy "estudiante_actualizacion_responsable" on public.estudiante for update to authenticated
  using (public.es_responsable_de(id_estudiante)) with check (public.es_responsable_de(id_estudiante));
create policy "responsables_lectura_autorizada" on public.responsable_estudiante for select to authenticated
  using (id_responsable = auth.uid() or public.es_docente_de(id_estudiante));
create policy "grupos_lectura_docente" on public.grupo for select to authenticated
  using (exists (select 1 from public.docente_grupo dg where dg.id_grupo = grupo.id_grupo and dg.id_docente = auth.uid() and dg.activo));
create policy "docente_grupo_lectura_propios" on public.docente_grupo for select to authenticated
  using (id_docente = auth.uid());
create policy "inscripciones_lectura_autorizada" on public.inscripcion_grupo for select to authenticated
  using (public.es_responsable_de(id_estudiante) or public.es_docente_de(id_estudiante));
create policy "consentimiento_lectura_responsable" on public.consentimiento for select to authenticated
  using (id_responsable = auth.uid());
create policy "consentimiento_actualizacion_responsable" on public.consentimiento for update to authenticated
  using (id_responsable = auth.uid()) with check (id_responsable = auth.uid());

create policy "sesiones_lectura_autorizada" on public.sesion_aprendizaje for select to authenticated
  using (public.es_responsable_de(id_estudiante) or public.es_docente_de(id_estudiante));
create policy "sesiones_escritura_responsable" on public.sesion_aprendizaje for insert to authenticated
  with check (public.es_responsable_de(id_estudiante));
create policy "sesiones_actualizacion_responsable" on public.sesion_aprendizaje for update to authenticated
  using (public.es_responsable_de(id_estudiante)) with check (public.es_responsable_de(id_estudiante));
create policy "diagnostico_lectura_autorizada" on public.resultado_diagnostico for select to authenticated
  using (public.es_responsable_de(id_estudiante) or public.es_docente_de(id_estudiante));
create policy "refuerzo_lectura_autorizada" on public.refuerzo_docente for select to authenticated
  using (public.es_responsable_de(id_estudiante) or public.es_docente_de(id_estudiante));
create policy "refuerzo_docente_insercion" on public.refuerzo_docente for insert to authenticated
  with check (id_docente = auth.uid() and public.es_docente_de(id_estudiante));
create policy "asignacion_lectura_autorizada" on public.asignacion_ejercicio for select to authenticated
  using (public.es_responsable_de(id_estudiante) or public.es_docente_de(id_estudiante));
create policy "asignacion_escritura_responsable" on public.asignacion_ejercicio for insert to authenticated
  with check (public.es_responsable_de(id_estudiante));
create policy "intento_lectura_autorizada" on public.intento_ejercicio for select to authenticated
  using (exists (select 1 from public.asignacion_ejercicio a where a.id_asignacion = intento_ejercicio.id_asignacion and (public.es_responsable_de(a.id_estudiante) or public.es_docente_de(a.id_estudiante))));
create policy "intento_escritura_responsable" on public.intento_ejercicio for insert to authenticated
  with check (exists (select 1 from public.asignacion_ejercicio a where a.id_asignacion = intento_ejercicio.id_asignacion and public.es_responsable_de(a.id_estudiante)));
create policy "deteccion_lectura_autorizada" on public.deteccion_dificultad for select to authenticated
  using (exists (select 1 from public.asignacion_ejercicio a where a.id_asignacion = deteccion_dificultad.id_asignacion and (public.es_responsable_de(a.id_estudiante) or public.es_docente_de(a.id_estudiante))));
create policy "intervencion_lectura_autorizada" on public.intervencion_apoyo for select to authenticated
  using (exists (select 1 from public.asignacion_ejercicio a where a.id_asignacion = intervencion_apoyo.id_asignacion and (public.es_responsable_de(a.id_estudiante) or public.es_docente_de(a.id_estudiante))));
create policy "auditoria_actor_lectura" on public.auditoria for select to authenticated using (id_actor = auth.uid());

-- Las vistas respetan las politicas de sus tablas subyacentes.
grant select on public.v_progreso_contenido, public.v_nivel_actual_estudiante to authenticated;
