# Base de datos K-MAT IA

La fuente ejecutable es [la migracion inicial](migrations/20260930160000_kmat_schema.sql). Esta documentacion describe el modelo normalizado para PostgreSQL/Supabase y sus decisiones de operacion.

## Principios de diseno

- Los identificadores son UUID para que Room pueda crear registros offline sin colisiones.
- Las tablas de catalogo, historial y relaciones N:M estan separadas. No se repiten responsables en `estudiante`, docentes en `grupo` ni contenidos entre NT1 y NT2.
- Los intentos son el historial fuente. El progreso se calcula mediante `v_progreso_contenido`, por lo que no duplica aciertos, errores ni tiempos.
- Una asignacion usa una version inmutable de ejercicio. El historial no cambia al editar el banco de ejercicios.
- Las claves foraneas compuestas conectan sesion, refuerzo, intento, deteccion e intervencion con el mismo estudiante y asignacion.

## Modelo ER

El modelo editable y exportable esta en [modelo_relacional_kmat.dbml](modelo_relacional_kmat.dbml). Se puede abrir en [dbdiagram.io](https://dbdiagram.io) con **Import DBML**.

Flujo principal:

```text
ESTUDIANTE -> SESION_APRENDIZAJE -> ASIGNACION_EJERCICIO
                                      -> INTENTO_EJERCICIO
                                      -> DETECCION_DIFICULTAD
                                      -> INTERVENCION_APOYO
```

## Diccionario de datos

| Tabla | Clave | Finalidad |
|---|---|---|
| `perfil` | `id_usuario` = `auth.users.id` | Perfil mínimo del adulto autenticado. |
| `rol` / `usuario_rol` | `codigo` / clave compuesta | Roles escalables; un usuario puede ser docente y responsable. |
| `nivel_educativo` | `codigo` | Catálogo NT1 y NT2. |
| `nivel_aprendizaje` | `id_nivel` | Secuencia pedagógica usada por diagnóstico y adaptación. |
| `estudiante` | `id_estudiante` | Perfil infantil, sin cuenta ni contraseña propia. |
| `responsable_estudiante` | `(id_estudiante, id_responsable)` | Relación N:M entre estudiante y responsables; uno puede ser principal. |
| `grupo` | `id_grupo` | Sala de seguimiento. |
| `docente_grupo` | `(id_docente, id_grupo)` | Docentes de un grupo e historial de participación. |
| `inscripcion_grupo` | `id_inscripcion` | Matrícula histórica estudiante-grupo. |
| `consentimiento` | `id_consentimiento` | Consentimiento versionado, ligado a un responsable válido del estudiante. |
| `contenido` | `id_contenido` | Contenido matemático reutilizable. |
| `contenido_nivel_educativo` | clave compuesta | Relación N:M entre contenido y NT1/NT2. |
| `ejercicio` | `id_ejercicio` | Identidad estable de un ejercicio. |
| `ejercicio_version` | `id_ejercicio_version` | Enunciado, solución y configuración inmutables de cada versión. |
| `recurso_apoyo` | `id_recurso` | Pausas, pistas, juegos, videos y materiales. |
| `sesion_aprendizaje` | `id_sesion` | Período de diagnóstico, práctica o refuerzo. |
| `resultado_diagnostico` | `id_sesion` | Nivel asignado al completar un diagnóstico. |
| `refuerzo_docente` | `id_refuerzo` | Intervención solicitada por un docente autorizado. |
| `asignacion_ejercicio` | `id_asignacion` | Ejercicio concreto entregado al estudiante. |
| `intento_ejercicio` | `id_intento` | Respuesta, reconocimiento, corrección, tiempo e intento. |
| `deteccion_dificultad` | `id_deteccion` | Señal pedagógica de dificultad, sin diagnóstico clínico. |
| `intervencion_apoyo` | `id_intervencion` | Apoyo mostrado y resultado, anclado a una asignación. |
| `auditoria` | `id_auditoria` | Bitácora append-only de consentimiento, grupos, refuerzos y asignaciones. |

## Integridad que aplica la base

- Solo un perfil con rol `RESPONSABLE` puede vincularse como responsable de un estudiante.
- Solo un perfil con rol `DOCENTE` puede pertenecer a `docente_grupo` o crear un refuerzo.
- Un docente solo puede reforzar estudiantes inscritos en uno de sus grupos activos.
- Un consentimiento solo puede apuntar a una relación responsable-estudiante existente.
- Una asignación solo puede usar ejercicios publicados.
- La sesión y el refuerzo de una asignación deben pertenecer al mismo estudiante.
- Un intento o detección usados en una intervención deben pertenecer a la misma asignación.
- Una versión de ejercicio no permite modificar contenido, solución ni configuración: se crea otra versión.
- Fechas, estados, cantidades y porcentajes tienen restricciones `CHECK`.

## Seguridad

La migración activa RLS para todas las tablas públicas.

- El responsable puede ver los estudiantes a los que está vinculado y sus datos de aprendizaje.
- El docente puede ver estudiantes inscritos en sus grupos activos y crear refuerzos solo para ellos.
- Los catálogos de ejercicios y recursos son solo de lectura para usuarios autenticados.
- Las operaciones administrativas, creación inicial de perfiles y cambios de roles deben ejecutarse mediante una Edge Function o backend con `service_role`; esa clave nunca se expone en Android.
- Las vistas de progreso usan `security_invoker`, por lo que respetan las mismas políticas RLS.

Antes de producción se debe probar cada política con una cuenta de responsable, una de docente y una sesión no autenticada usando `supabase test db`.

## Rendimiento y escalabilidad

Los índices cubren las consultas del MVP: ejercicios por contenido/nivel, cola de asignaciones, sesiones por estudiante, intentos, detecciones, intervenciones, grupos y auditoría.

No se crea una tabla de reportes ni un resumen de progreso duplicado. Los reportes se obtienen de la vista de progreso y del historial. Si el volumen futuro lo exige, se puede añadir una vista materializada o una tabla de resumen actualizada por proceso de servidor, sin cambiar las relaciones originales.

## Respaldo y recuperación

El DDL no puede configurar las copias de seguridad de una cuenta Supabase. Antes del despliegue se debe completar este procedimiento operativo:

1. Elegir un plan de Supabase con las retenciones y recuperación puntual requeridas por el proyecto.
2. Guardar todas las migraciones en control de versiones y ejecutar restauraciones de prueba en un proyecto Supabase separado.
3. Programar una exportación externa cifrada de datos necesarios para continuidad, con acceso limitado al equipo autorizado.
4. Documentar responsable, frecuencia, retención, ubicación y resultado de cada prueba de restauración.

La migración evita pérdidas accidentales usando `ON DELETE RESTRICT` en el historial pedagógico. Las bajas funcionales se realizan con estados `INACTIVO`, `CANCELADO` o `RETIRADA`, no borrando historial.

## Despliegue

1. Crear el proyecto Supabase y configurar sus variables en la app, sin exponer la clave `service_role`.
2. Ejecutar la migración mediante Supabase CLI o SQL Editor.
3. Crear los perfiles y roles después de cada alta en Supabase Auth desde una Edge Function segura.
4. Cargar niveles, contenidos, ejercicios publicados y recursos de apoyo.
5. Ejecutar pruebas de integridad, RLS, sincronización offline y restauración antes de incorporar estudiantes reales.
