-- ============================================================================
-- K-MAT IA
-- MODELO RELACIONAL SIMPLIFICADO PARA EL MVP
-- Compatible con Oracle SQL Developer Data Modeler.
--
-- Objetivo:
--   - Cubrir los requisitos funcionales y no funcionales del proyecto.
--   - Mantener el modelo normalizado y entendible.
--   - Evitar tablas que representen lógica de aplicación innecesariamente.
--   - Preparar una futura implementación en PostgreSQL / Supabase.
--
-- Criterio de diseño:
--   La BD guarda hechos y estado persistente.
--   El motor adaptativo, ML Kit, corrección inmediata, pausas y selección de
--   apoyos se ejecutan principalmente en la aplicación y consultan estos datos.
--
-- IMPORTANTE:
--   En Oracle/Data Modeler se usa VARCHAR2(36) para UUID.
--   En Supabase/PostgreSQL estos campos se convertirán a UUID.
-- ============================================================================


-- ============================================================================
-- 1. USUARIO
-- Adultos autenticados: DOCENTE o RESPONSABLE.
-- AUTH_USER_ID permite vincular el perfil con Supabase Auth.
-- RF-01 / RNF-06 / RNF-07
-- ============================================================================
CREATE TABLE USUARIO (
    ID_USUARIO          VARCHAR2(36)    NOT NULL,
    AUTH_USER_ID        VARCHAR2(36)    NOT NULL,
    NOMBRE              VARCHAR2(100)   NOT NULL,
    CORREO              VARCHAR2(150)   NOT NULL,
    ROL                 VARCHAR2(20)    NOT NULL,
    ESTADO              CHAR(1)         DEFAULT 'A' NOT NULL,
    FECHA_CREACION      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FECHA_ACTUALIZACION TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_USUARIO PRIMARY KEY (ID_USUARIO),
    CONSTRAINT UQ_USUARIO_AUTH UNIQUE (AUTH_USER_ID),
    CONSTRAINT UQ_USUARIO_CORREO UNIQUE (CORREO),
    CONSTRAINT CK_USUARIO_ROL CHECK (ROL IN ('DOCENTE', 'RESPONSABLE')),
    CONSTRAINT CK_USUARIO_ESTADO CHECK (ESTADO IN ('A', 'I'))
);


-- ============================================================================
-- 2. NIVEL_APRENDIZAJE
-- Pasos internos de progresión usados por el diagnóstico y motor adaptativo.
-- RF-04 / RF-05 / RF-10 / RF-14
-- ============================================================================
CREATE TABLE NIVEL_APRENDIZAJE (
    ID_NIVEL            VARCHAR2(36)    NOT NULL,
    NOMBRE_NIVEL        VARCHAR2(100)   NOT NULL,
    DESCRIPCION         VARCHAR2(500),
    ORDEN_NIVEL         NUMBER          NOT NULL,
    ESTADO              CHAR(1)         DEFAULT 'A' NOT NULL,

    CONSTRAINT PK_NIVEL_APRENDIZAJE PRIMARY KEY (ID_NIVEL),
    CONSTRAINT UQ_NIVEL_ORDEN UNIQUE (ORDEN_NIVEL),
    CONSTRAINT CK_NIVEL_ORDEN CHECK (ORDEN_NIVEL > 0),
    CONSTRAINT CK_NIVEL_ESTADO CHECK (ESTADO IN ('A', 'I'))
);


-- ============================================================================
-- 3. GRUPO
-- Sala/grupo de seguimiento de un docente.
-- No representa videollamada ni clase virtual.
-- RF-16 / RF-17
-- ============================================================================
CREATE TABLE GRUPO (
    ID_GRUPO            VARCHAR2(36)    NOT NULL,
    ID_DOCENTE          VARCHAR2(36)    NOT NULL,
    NOMBRE_GRUPO        VARCHAR2(100)   NOT NULL,
    CODIGO_UNION        VARCHAR2(20)    NOT NULL,
    ESTADO              CHAR(1)         DEFAULT 'A' NOT NULL,
    FECHA_CREACION      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_GRUPO PRIMARY KEY (ID_GRUPO),
    CONSTRAINT UQ_GRUPO_CODIGO UNIQUE (CODIGO_UNION),
    CONSTRAINT FK_GRUPO_DOCENTE
        FOREIGN KEY (ID_DOCENTE) REFERENCES USUARIO(ID_USUARIO),
    CONSTRAINT CK_GRUPO_ESTADO CHECK (ESTADO IN ('A', 'I'))
);


-- ============================================================================
-- 4. ESTUDIANTE
-- Perfil infantil. Para el MVP cada estudiante puede estar en un grupo activo.
-- No almacena contraseña infantil.
-- RF-02 / RF-03 / RF-14 / RF-16 / RNF-07
-- ============================================================================
CREATE TABLE ESTUDIANTE (
    ID_ESTUDIANTE       VARCHAR2(36)    NOT NULL,
    ID_RESPONSABLE      VARCHAR2(36)    NOT NULL,
    ID_GRUPO            VARCHAR2(36),
    ID_NIVEL_ACTUAL     VARCHAR2(36),
    NOMBRE              VARCHAR2(100)   NOT NULL,
    AVATAR              VARCHAR2(255),
    NIVEL_EDUCATIVO     VARCHAR2(20)    NOT NULL,
    ESTADO              CHAR(1)         DEFAULT 'A' NOT NULL,
    FECHA_CREACION      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FECHA_ACTUALIZACION TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_ESTUDIANTE PRIMARY KEY (ID_ESTUDIANTE),
    CONSTRAINT FK_ESTUDIANTE_RESPONSABLE
        FOREIGN KEY (ID_RESPONSABLE) REFERENCES USUARIO(ID_USUARIO),
    CONSTRAINT FK_ESTUDIANTE_GRUPO
        FOREIGN KEY (ID_GRUPO) REFERENCES GRUPO(ID_GRUPO),
    CONSTRAINT FK_ESTUDIANTE_NIVEL
        FOREIGN KEY (ID_NIVEL_ACTUAL) REFERENCES NIVEL_APRENDIZAJE(ID_NIVEL),
    CONSTRAINT CK_ESTUDIANTE_NIVEL_EDU
        CHECK (NIVEL_EDUCATIVO IN ('NT1', 'NT2')),
    CONSTRAINT CK_ESTUDIANTE_ESTADO CHECK (ESTADO IN ('A', 'I'))
);


-- ============================================================================
-- 5. CONSENTIMIENTO
-- Registro del consentimiento asociado al perfil del menor.
-- El responsable se obtiene mediante ESTUDIANTE.ID_RESPONSABLE.
-- RNF-07
-- ============================================================================
CREATE TABLE CONSENTIMIENTO (
    ID_CONSENTIMIENTO   VARCHAR2(36)    NOT NULL,
    ID_ESTUDIANTE       VARCHAR2(36)    NOT NULL,
    VERSION_DOCUMENTO   VARCHAR2(30)    NOT NULL,
    ESTADO              VARCHAR2(20)    NOT NULL,
    FECHA_OTORGAMIENTO  TIMESTAMP,
    FECHA_REVOCACION    TIMESTAMP,

    CONSTRAINT PK_CONSENTIMIENTO PRIMARY KEY (ID_CONSENTIMIENTO),
    CONSTRAINT FK_CONSENT_ESTUDIANTE
        FOREIGN KEY (ID_ESTUDIANTE) REFERENCES ESTUDIANTE(ID_ESTUDIANTE),
    CONSTRAINT CK_CONSENT_ESTADO
        CHECK (ESTADO IN ('PENDIENTE', 'OTORGADO', 'REVOCADO')),
    CONSTRAINT CK_CONSENT_FECHAS
        CHECK (
            FECHA_REVOCACION IS NULL
            OR FECHA_OTORGAMIENTO IS NULL
            OR FECHA_REVOCACION >= FECHA_OTORGAMIENTO
        )
);


-- ============================================================================
-- 6. CONTENIDO
-- Habilidad o contenido matemático del MVP.
-- Ej.: conteo, comparación, patrones, suma o resta.
-- RF-04 / RF-05 / RF-09 / RF-14 / RF-17 / RF-18
-- ============================================================================
CREATE TABLE CONTENIDO (
    ID_CONTENIDO        VARCHAR2(36)    NOT NULL,
    NOMBRE_CONTENIDO    VARCHAR2(100)   NOT NULL,
    DESCRIPCION         VARCHAR2(500),
    ORDEN_CONTENIDO     NUMBER          NOT NULL,
    ESTADO              CHAR(1)         DEFAULT 'A' NOT NULL,

    CONSTRAINT PK_CONTENIDO PRIMARY KEY (ID_CONTENIDO),
    CONSTRAINT UQ_CONTENIDO_ORDEN UNIQUE (ORDEN_CONTENIDO),
    CONSTRAINT CK_CONTENIDO_ORDEN CHECK (ORDEN_CONTENIDO > 0),
    CONSTRAINT CK_CONTENIDO_ESTADO CHECK (ESTADO IN ('A', 'I'))
);


-- ============================================================================
-- 7. EJERCICIO
-- Banco de ejercicios. ML Kit reconoce la escritura en la app; la BD conserva
-- la respuesta esperada y metadatos del ejercicio.
-- RF-04 / RF-05 / RF-06 / RF-07 / RF-08 / RF-15
-- ============================================================================
CREATE TABLE EJERCICIO (
    ID_EJERCICIO        VARCHAR2(36)    NOT NULL,
    ID_CONTENIDO        VARCHAR2(36)    NOT NULL,
    ID_NIVEL            VARCHAR2(36)    NOT NULL,
    TIPO_EJERCICIO      VARCHAR2(30)    NOT NULL,
    TIPO_RESPUESTA      VARCHAR2(30)    NOT NULL,
    ENUNCIADO           VARCHAR2(1000)  NOT NULL,
    RESPUESTA_CORRECTA  VARCHAR2(255),
    DIFICULTAD          NUMBER          NOT NULL,
    RECURSO_URI         VARCHAR2(500),
    DISPONIBLE_OFFLINE  CHAR(1)         DEFAULT 'S' NOT NULL,
    ESTADO              CHAR(1)         DEFAULT 'A' NOT NULL,
    FECHA_ACTUALIZACION TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_EJERCICIO PRIMARY KEY (ID_EJERCICIO),
    CONSTRAINT FK_EJERCICIO_CONTENIDO
        FOREIGN KEY (ID_CONTENIDO) REFERENCES CONTENIDO(ID_CONTENIDO),
    CONSTRAINT FK_EJERCICIO_NIVEL
        FOREIGN KEY (ID_NIVEL) REFERENCES NIVEL_APRENDIZAJE(ID_NIVEL),
    CONSTRAINT CK_EJERCICIO_TIPO
        CHECK (TIPO_EJERCICIO IN (
            'CONTEO', 'SECUENCIA', 'CONTEO_SALTOS', 'COMPARACION',
            'CLASIFICACION', 'PATRON', 'SUMA', 'RESTA', 'PROBLEMA', 'ORDENAR'
        )),
    CONSTRAINT CK_EJERCICIO_RESPUESTA
        CHECK (TIPO_RESPUESTA IN ('MANUSCRITA', 'SELECCION', 'ORDENAMIENTO')),
    CONSTRAINT CK_EJERCICIO_DIFICULTAD CHECK (DIFICULTAD BETWEEN 1 AND 5),
    CONSTRAINT CK_EJERCICIO_OFFLINE CHECK (DISPONIBLE_OFFLINE IN ('S', 'N')),
    CONSTRAINT CK_EJERCICIO_ESTADO CHECK (ESTADO IN ('A', 'I'))
);


-- ============================================================================
-- 8. RECURSO_APOYO
-- Catálogo reutilizable de apoyos. ETAPA_APOYO permite aplicar una progresión
-- sin crear una tabla para cada decisión del motor.
-- Ejemplo: pausa -> pista -> actividad/juego -> video/material.
-- RF-12 / RF-13 / RF-18 / RNF-08
-- ============================================================================
CREATE TABLE RECURSO_APOYO (
    ID_RECURSO          VARCHAR2(36)    NOT NULL,
    ID_CONTENIDO        VARCHAR2(36),
    TIPO_RECURSO        VARCHAR2(30)    NOT NULL,
    TITULO              VARCHAR2(150)   NOT NULL,
    DESCRIPCION         VARCHAR2(1000),
    RECURSO_URI         VARCHAR2(500),
    ETAPA_APOYO         NUMBER          DEFAULT 1 NOT NULL,
    DURACION_SEGUNDOS   NUMBER,
    DISPONIBLE_OFFLINE  CHAR(1)         DEFAULT 'S' NOT NULL,
    ESTADO              CHAR(1)         DEFAULT 'A' NOT NULL,

    CONSTRAINT PK_RECURSO_APOYO PRIMARY KEY (ID_RECURSO),
    CONSTRAINT FK_RECURSO_CONTENIDO
        FOREIGN KEY (ID_CONTENIDO) REFERENCES CONTENIDO(ID_CONTENIDO),
    CONSTRAINT CK_RECURSO_TIPO
        CHECK (TIPO_RECURSO IN (
            'PAUSA_ACTIVA', 'PISTA', 'ACTIVIDAD_ALTERNATIVA',
            'JUEGO', 'VIDEO', 'MATERIAL_APOYO'
        )),
    CONSTRAINT CK_RECURSO_ETAPA CHECK (ETAPA_APOYO BETWEEN 1 AND 5),
    CONSTRAINT CK_RECURSO_DURACION
        CHECK (DURACION_SEGUNDOS IS NULL OR DURACION_SEGUNDOS >= 0),
    CONSTRAINT CK_RECURSO_OFFLINE CHECK (DISPONIBLE_OFFLINE IN ('S', 'N')),
    CONSTRAINT CK_RECURSO_ESTADO CHECK (ESTADO IN ('A', 'I'))
);


-- ============================================================================
-- 9. SESION_APRENDIZAJE
-- Periodo de trabajo del estudiante. El diagnóstico es un tipo de sesión.
-- RF-04 / RF-09 / RF-14 / RF-15
-- ============================================================================
CREATE TABLE SESION_APRENDIZAJE (
    ID_SESION           VARCHAR2(36)    NOT NULL,
    ID_ESTUDIANTE       VARCHAR2(36)    NOT NULL,
    TIPO_SESION         VARCHAR2(20)    NOT NULL,
    FECHA_INICIO        TIMESTAMP       NOT NULL,
    FECHA_FIN           TIMESTAMP,
    ESTADO              VARCHAR2(20)    NOT NULL,
    FECHA_ACTUALIZACION TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_SESION_APRENDIZAJE PRIMARY KEY (ID_SESION),
    CONSTRAINT FK_SESION_ESTUDIANTE
        FOREIGN KEY (ID_ESTUDIANTE) REFERENCES ESTUDIANTE(ID_ESTUDIANTE),
    CONSTRAINT CK_SESION_TIPO
        CHECK (TIPO_SESION IN ('DIAGNOSTICO', 'PRACTICA', 'REFUERZO')),
    CONSTRAINT CK_SESION_ESTADO
        CHECK (ESTADO IN ('EN_CURSO', 'COMPLETADA', 'INTERRUMPIDA')),
    CONSTRAINT CK_SESION_FECHAS
        CHECK (FECHA_FIN IS NULL OR FECHA_FIN >= FECHA_INICIO)
);


-- ============================================================================
-- 10. REFUERZO_DOCENTE
-- Solicitud manual del profesor. Solo persiste aquello que realmente debe
-- quedar registrado; la app decide cómo presentarlo al niño.
-- RF-17 / RF-18
-- ============================================================================
CREATE TABLE REFUERZO_DOCENTE (
    ID_REFUERZO         VARCHAR2(36)    NOT NULL,
    ID_DOCENTE          VARCHAR2(36)    NOT NULL,
    ID_ESTUDIANTE       VARCHAR2(36)    NOT NULL,
    ID_CONTENIDO        VARCHAR2(36)    NOT NULL,
    ID_RECURSO          VARCHAR2(36),
    TIPO_REFUERZO       VARCHAR2(30)    NOT NULL,
    CANTIDAD_EJERCICIOS NUMBER,
    ESTADO              VARCHAR2(20)    DEFAULT 'PENDIENTE' NOT NULL,
    OBSERVACION         VARCHAR2(500),
    FECHA_ASIGNACION    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FECHA_COMPLETADO    TIMESTAMP,

    CONSTRAINT PK_REFUERZO_DOCENTE PRIMARY KEY (ID_REFUERZO),
    CONSTRAINT FK_REFUERZO_DOCENTE
        FOREIGN KEY (ID_DOCENTE) REFERENCES USUARIO(ID_USUARIO),
    CONSTRAINT FK_REFUERZO_ESTUDIANTE
        FOREIGN KEY (ID_ESTUDIANTE) REFERENCES ESTUDIANTE(ID_ESTUDIANTE),
    CONSTRAINT FK_REFUERZO_CONTENIDO
        FOREIGN KEY (ID_CONTENIDO) REFERENCES CONTENIDO(ID_CONTENIDO),
    CONSTRAINT FK_REFUERZO_RECURSO
        FOREIGN KEY (ID_RECURSO) REFERENCES RECURSO_APOYO(ID_RECURSO),
    CONSTRAINT CK_REFUERZO_TIPO
        CHECK (TIPO_REFUERZO IN (
            'MAS_EJERCICIOS', 'ACTIVIDAD_ALTERNATIVA',
            'JUEGO', 'VIDEO', 'MATERIAL_APOYO'
        )),
    CONSTRAINT CK_REFUERZO_CANTIDAD
        CHECK (CANTIDAD_EJERCICIOS IS NULL OR CANTIDAD_EJERCICIOS > 0),
    CONSTRAINT CK_REFUERZO_ESTADO
        CHECK (ESTADO IN ('PENDIENTE', 'ACTIVO', 'COMPLETADO', 'CANCELADO')),
    CONSTRAINT CK_REFUERZO_FECHAS
        CHECK (FECHA_COMPLETADO IS NULL OR FECHA_COMPLETADO >= FECHA_ASIGNACION)
);


-- ============================================================================
-- 11. ASIGNACION_EJERCICIO
-- Trayectoria concreta del estudiante. Puede estar asociada a una sesión o
-- quedar pendiente para ejecutarse después/offline.
-- El motor adaptativo crea nuevas asignaciones en vez de guardar cada decisión
-- en una tabla separada.
-- RF-04 / RF-05 / RF-10 / RF-14 / RF-15 / RF-18
-- ============================================================================
CREATE TABLE ASIGNACION_EJERCICIO (
    ID_ASIGNACION       VARCHAR2(36)    NOT NULL,
    ID_ESTUDIANTE       VARCHAR2(36)    NOT NULL,
    ID_EJERCICIO        VARCHAR2(36)    NOT NULL,
    ID_SESION           VARCHAR2(36),
    ID_REFUERZO         VARCHAR2(36),
    ORIGEN_ASIGNACION   VARCHAR2(20)    NOT NULL,
    PRIORIDAD           NUMBER          DEFAULT 1 NOT NULL,
    ORDEN_COLA          NUMBER,
    ESTADO              VARCHAR2(20)    DEFAULT 'PENDIENTE' NOT NULL,
    FECHA_ASIGNACION    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FECHA_INICIO        TIMESTAMP,
    FECHA_COMPLETADO    TIMESTAMP,
    FECHA_ACTUALIZACION TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_ASIGNACION_EJERCICIO PRIMARY KEY (ID_ASIGNACION),
    CONSTRAINT FK_ASIG_ESTUDIANTE
        FOREIGN KEY (ID_ESTUDIANTE) REFERENCES ESTUDIANTE(ID_ESTUDIANTE),
    CONSTRAINT FK_ASIG_EJERCICIO
        FOREIGN KEY (ID_EJERCICIO) REFERENCES EJERCICIO(ID_EJERCICIO),
    CONSTRAINT FK_ASIG_SESION
        FOREIGN KEY (ID_SESION) REFERENCES SESION_APRENDIZAJE(ID_SESION),
    CONSTRAINT FK_ASIG_REFUERZO
        FOREIGN KEY (ID_REFUERZO) REFERENCES REFUERZO_DOCENTE(ID_REFUERZO),
    CONSTRAINT CK_ASIG_ORIGEN
        CHECK (ORIGEN_ASIGNACION IN ('DIAGNOSTICO', 'MOTOR', 'DOCENTE')),
    CONSTRAINT CK_ASIG_PRIORIDAD CHECK (PRIORIDAD > 0),
    CONSTRAINT CK_ASIG_ORDEN CHECK (ORDEN_COLA IS NULL OR ORDEN_COLA > 0),
    CONSTRAINT CK_ASIG_ESTADO
        CHECK (ESTADO IN ('PENDIENTE', 'EN_CURSO', 'COMPLETADA', 'CANCELADA')),
    CONSTRAINT CK_ASIG_FECHAS
        CHECK (
            (FECHA_INICIO IS NULL OR FECHA_INICIO >= FECHA_ASIGNACION)
            AND
            (FECHA_COMPLETADO IS NULL OR FECHA_COMPLETADO >= FECHA_ASIGNACION)
        )
);


-- ============================================================================
-- 12. INTENTO_EJERCICIO
-- Historial real del desempeño. El ejercicio, estudiante y sesión se obtienen
-- desde ASIGNACION_EJERCICIO, evitando relaciones duplicadas.
-- RF-06 / RF-07 / RF-08 / RF-09 / RF-10 / RF-11 / RF-14
-- ============================================================================
CREATE TABLE INTENTO_EJERCICIO (
    ID_INTENTO             VARCHAR2(36)    NOT NULL,
    ID_ASIGNACION          VARCHAR2(36)    NOT NULL,
    RESPUESTA_RECONOCIDA   VARCHAR2(500),
    RESPUESTA_FINAL        VARCHAR2(500),
    CONFIANZA_RECONOCIMIENTO NUMBER(5,4),
    REQUIRIO_CONFIRMACION  CHAR(1)         DEFAULT 'N' NOT NULL,
    ES_CORRECTA            CHAR(1),
    NUMERO_INTENTO         NUMBER          NOT NULL,
    TIEMPO_RESPUESTA_MS    NUMBER,
    FECHA_HORA             TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FECHA_ACTUALIZACION    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_INTENTO_EJERCICIO PRIMARY KEY (ID_INTENTO),
    CONSTRAINT FK_INTENTO_ASIGNACION
        FOREIGN KEY (ID_ASIGNACION) REFERENCES ASIGNACION_EJERCICIO(ID_ASIGNACION),
    CONSTRAINT UQ_INTENTO_ORDEN
        UNIQUE (ID_ASIGNACION, NUMERO_INTENTO),
    CONSTRAINT CK_INTENTO_CONFIANZA
        CHECK (
            CONFIANZA_RECONOCIMIENTO IS NULL
            OR (CONFIANZA_RECONOCIMIENTO BETWEEN 0 AND 1)
        ),
    CONSTRAINT CK_INTENTO_CONFIRMACION
        CHECK (REQUIRIO_CONFIRMACION IN ('S', 'N')),
    CONSTRAINT CK_INTENTO_CORRECTA
        CHECK (ES_CORRECTA IS NULL OR ES_CORRECTA IN ('S', 'N')),
    CONSTRAINT CK_INTENTO_NUMERO CHECK (NUMERO_INTENTO > 0),
    CONSTRAINT CK_INTENTO_TIEMPO
        CHECK (TIEMPO_RESPUESTA_MS IS NULL OR TIEMPO_RESPUESTA_MS >= 0)
);


-- ============================================================================
-- 13. PROGRESO_CONTENIDO
-- Resumen actual por estudiante/contenido.
-- Los aciertos, errores, intentos y tiempos históricos se calculan desde
-- INTENTO_EJERCICIO; no se duplican aquí.
-- RF-04 / RF-05 / RF-09 / RF-10 / RF-14 / RF-17 / RF-19
-- ============================================================================
CREATE TABLE PROGRESO_CONTENIDO (
    ID_ESTUDIANTE       VARCHAR2(36)    NOT NULL,
    ID_CONTENIDO        VARCHAR2(36)    NOT NULL,
    NIVEL_DOMINIO       VARCHAR2(20)    NOT NULL,
    FECHA_ACTUALIZACION TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_PROGRESO_CONTENIDO
        PRIMARY KEY (ID_ESTUDIANTE, ID_CONTENIDO),
    CONSTRAINT FK_PROGRESO_ESTUDIANTE
        FOREIGN KEY (ID_ESTUDIANTE) REFERENCES ESTUDIANTE(ID_ESTUDIANTE),
    CONSTRAINT FK_PROGRESO_CONTENIDO
        FOREIGN KEY (ID_CONTENIDO) REFERENCES CONTENIDO(ID_CONTENIDO),
    CONSTRAINT CK_PROGRESO_DOMINIO
        CHECK (NIVEL_DOMINIO IN ('NO_INICIADO', 'INICIAL', 'EN_PROCESO', 'DOMINADO'))
);


-- ============================================================================
-- 14. DETECCION_DIFICULTAD
-- Guarda señales relevantes de dificultad/estancamiento.
-- El contenido, estudiante y sesión se pueden obtener desde la asignación,
-- evitando relaciones adicionales en el diagrama.
-- RF-11 / RF-12 / RF-13 / RF-17
-- ============================================================================
CREATE TABLE DETECCION_DIFICULTAD (
    ID_DETECCION          VARCHAR2(36)    NOT NULL,
    ID_ASIGNACION         VARCHAR2(36)    NOT NULL,
    TIPO_DETECCION        VARCHAR2(30)    NOT NULL,
    ERRORES_CONSECUTIVOS  NUMBER,
    INTENTOS_RECIENTES    NUMBER,
    TIEMPO_PROMEDIO_MS    NUMBER,
    NIVEL_SEVERIDAD       VARCHAR2(10)    NOT NULL,
    ESTADO                VARCHAR2(20)    DEFAULT 'ABIERTA' NOT NULL,
    MOTIVO                VARCHAR2(500),
    FECHA_HORA            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT PK_DETECCION_DIFICULTAD PRIMARY KEY (ID_DETECCION),
    CONSTRAINT FK_DETECCION_ASIGNACION
        FOREIGN KEY (ID_ASIGNACION) REFERENCES ASIGNACION_EJERCICIO(ID_ASIGNACION),
    CONSTRAINT CK_DETECCION_TIPO
        CHECK (TIPO_DETECCION IN (
            'TIEMPO_ELEVADO', 'ERRORES_CONSECUTIVOS',
            'MULTIPLES_INTENTOS', 'DESEMPENO_RECIENTE', 'COMBINADA'
        )),
    CONSTRAINT CK_DETECCION_ERRORES
        CHECK (ERRORES_CONSECUTIVOS IS NULL OR ERRORES_CONSECUTIVOS >= 0),
    CONSTRAINT CK_DETECCION_INTENTOS
        CHECK (INTENTOS_RECIENTES IS NULL OR INTENTOS_RECIENTES >= 0),
    CONSTRAINT CK_DETECCION_TIEMPO
        CHECK (TIEMPO_PROMEDIO_MS IS NULL OR TIEMPO_PROMEDIO_MS >= 0),
    CONSTRAINT CK_DETECCION_SEVERIDAD
        CHECK (NIVEL_SEVERIDAD IN ('BAJA', 'MEDIA', 'ALTA')),
    CONSTRAINT CK_DETECCION_ESTADO
        CHECK (ESTADO IN ('ABIERTA', 'RESUELTA'))
);


-- ============================================================================
-- ÍNDICES PRINCIPALES
-- Solo se incluyen los que ayudan a consultas frecuentes del MVP.
-- ============================================================================
CREATE INDEX IDX_ESTUDIANTE_RESPONSABLE
    ON ESTUDIANTE(ID_RESPONSABLE);

CREATE INDEX IDX_ESTUDIANTE_GRUPO
    ON ESTUDIANTE(ID_GRUPO);

CREATE INDEX IDX_EJERCICIO_CONT_NIVEL
    ON EJERCICIO(ID_CONTENIDO, ID_NIVEL, ESTADO);

CREATE INDEX IDX_RECURSO_CONTENIDO
    ON RECURSO_APOYO(ID_CONTENIDO, TIPO_RECURSO, ETAPA_APOYO);

CREATE INDEX IDX_SESION_ESTUDIANTE
    ON SESION_APRENDIZAJE(ID_ESTUDIANTE, FECHA_INICIO);

CREATE INDEX IDX_REFUERZO_ESTUDIANTE
    ON REFUERZO_DOCENTE(ID_ESTUDIANTE, ESTADO, FECHA_ASIGNACION);

CREATE INDEX IDX_ASIG_ESTUDIANTE
    ON ASIGNACION_EJERCICIO(ID_ESTUDIANTE, ESTADO, PRIORIDAD);

CREATE INDEX IDX_ASIG_SESION
    ON ASIGNACION_EJERCICIO(ID_SESION);

CREATE INDEX IDX_INTENTO_ASIGNACION
    ON INTENTO_EJERCICIO(ID_ASIGNACION, NUMERO_INTENTO);

CREATE INDEX IDX_PROGRESO_ESTUDIANTE
    ON PROGRESO_CONTENIDO(ID_ESTUDIANTE, FECHA_ACTUALIZACION);

CREATE INDEX IDX_DETECCION_ASIGNACION
    ON DETECCION_DIFICULTAD(ID_ASIGNACION, ESTADO, FECHA_HORA);


-- ============================================================================
-- TRAZABILIDAD RESUMIDA DE REQUISITOS FUNCIONALES
-- ============================================================================
-- RF-01 Usuarios y acceso
--   -> USUARIO + AUTH_USER_ID (Supabase Auth en implementación real).
-- RF-02 Perfil estudiante
--   -> ESTUDIANTE.
-- RF-03 Acceso infantil simplificado
--   -> ESTUDIANTE.AVATAR; sin contraseña infantil.
-- RF-04 Diagnóstico inicial
--   -> SESION_APRENDIZAJE(DIAGNOSTICO) + ASIGNACION + INTENTO + NIVEL.
-- RF-05 Ejercicios personalizados
--   -> NIVEL_APRENDIZAJE + PROGRESO_CONTENIDO + ASIGNACION_EJERCICIO.
-- RF-06 Escritura dedo/lápiz
--   -> Kotlin/Compose; resultado persistido en INTENTO_EJERCICIO.
-- RF-07 Reconocimiento manuscrito
--   -> ML Kit + RESPUESTA_RECONOCIDA / CONFIANZA / RESPUESTA_FINAL.
-- RF-08 Corrección inmediata
--   -> EJERCICIO.RESPUESTA_CORRECTA + INTENTO.ES_CORRECTA.
-- RF-09 Registro de desempeño
--   -> INTENTO_EJERCICIO + SESION_APRENDIZAJE + PROGRESO_CONTENIDO.
-- RF-10 Motor adaptativo
--   -> lógica de aplicación que lee INTENTOS/PROGRESO y genera ASIGNACIONES.
-- RF-11 Detección de dificultad
--   -> DETECCION_DIFICULTAD basada en INTENTO_EJERCICIO.
-- RF-12 Pausas activas
--   -> RECURSO_APOYO(TIPO=PAUSA_ACTIVA), seleccionada por el motor.
-- RF-13 Apoyo progresivo
--   -> RECURSO_APOYO.ETAPA_APOYO + tipos PISTA/JUEGO/VIDEO/MATERIAL.
-- RF-14 Continuidad del progreso
--   -> ESTUDIANTE.ID_NIVEL_ACTUAL + PROGRESO + ASIGNACIONES pendientes.
-- RF-15 Offline/sincronización
--   -> UUID + FECHA_ACTUALIZACION + DISPONIBLE_OFFLINE; Room/WorkManager.
-- RF-16 Grupo de seguimiento
--   -> GRUPO + ESTUDIANTE.ID_GRUPO + CODIGO_UNION.
-- RF-17 Seguimiento del profesor
--   -> GRUPO + SESIONES + INTENTOS + PROGRESO + DETECCIONES.
-- RF-18 Intervención docente
--   -> REFUERZO_DOCENTE + RECURSO_APOYO + asignaciones ORIGEN=DOCENTE.
-- RF-19 PDF/Excel
--   -> reportes generados consultando sesiones, intentos y progreso.


-- ============================================================================
-- TRAZABILIDAD RESUMIDA DE REQUISITOS NO FUNCIONALES
-- ============================================================================
-- RNF-01 Rendimiento
--   -> índices principales; reconocimiento local en dispositivo.
-- RNF-02 Interfaz infantil
--   -> Jetpack Compose; no requiere tabla.
-- RNF-03 Funcionamiento offline
--   -> UUID + recursos/ejercicios offline + Room.
-- RNF-04 Integridad/recuperación
--   -> PK, FK, UNIQUE y CHECK.
-- RNF-05 Sincronización no bloqueante
--   -> UUID + FECHA_ACTUALIZACION + WorkManager.
-- RNF-06 Seguridad
--   -> USUARIO.ROL + Supabase Auth/RLS en implementación.
-- RNF-07 Privacidad infantil
--   -> datos mínimos + CONSENTIMIENTO.
-- RNF-08 Retroalimentación no punitiva
--   -> RECURSO_APOYO y lógica de interfaz/motor adaptativo.


-- ============================================================================
-- RELACIONES PRINCIPALES PARA ORDENAR EL DIAGRAMA
-- ============================================================================
-- USUARIO 1:N GRUPO
-- USUARIO 1:N ESTUDIANTE (responsable)
-- GRUPO   1:N ESTUDIANTE
-- NIVEL_APRENDIZAJE 1:N ESTUDIANTE
-- ESTUDIANTE 1:N CONSENTIMIENTO
-- CONTENIDO 1:N EJERCICIO
-- NIVEL_APRENDIZAJE 1:N EJERCICIO
-- CONTENIDO 1:N RECURSO_APOYO
-- ESTUDIANTE 1:N SESION_APRENDIZAJE
-- DOCENTE/ESTUDIANTE/CONTENIDO -> REFUERZO_DOCENTE
-- ESTUDIANTE/EJERCICIO/SESION -> ASIGNACION_EJERCICIO
-- ASIGNACION_EJERCICIO 1:N INTENTO_EJERCICIO
-- ESTUDIANTE N:M CONTENIDO mediante PROGRESO_CONTENIDO
-- ASIGNACION_EJERCICIO 1:N DETECCION_DIFICULTAD


-- ============================================================================
-- NOTAS PARA POSTGRESQL / SUPABASE
-- ============================================================================
-- Al implementar en Supabase:
--   VARCHAR2(36) -> UUID
--   VARCHAR2(n)  -> VARCHAR(n)
--   TIMESTAMP    -> TIMESTAMPTZ recomendado
--   AUTH_USER_ID -> UUID UNIQUE REFERENCES auth.users(id)
--   CHAR(1) S/N  -> BOOLEAN cuando corresponda
--   agregar políticas Row Level Security según RESPONSABLE/DOCENTE.
-- ============================================================================
