# Autenticación segura

## Flujo

La persona adulta crea una cuenta con correo y contraseña. Supabase Auth envía
el enlace de confirmación cuando la confirmación de correo está habilitada;
la app permite ingresar solo después de que Auth emite una sesión y se valida
el rol `RESPONSABLE`. Después del primer ingreso, la vista de responsable permite
crear varios perfiles infantiles y elegir cuál usar en ese dispositivo.

Al entrar se consulta `listar_mis_estudiantes` con el JWT de la cuenta actual.
La app muestra los perfiles recuperados y permite actualizar la lista. Crear
un perfil siempre requiere pulsar **Agregar**; una lista vacía o un error no
abren automáticamente el formulario. Los errores de permisos se distinguen de
una sesión vencida y permiten reintentar sin crear registros duplicados.

En **¿Quién va a aprender?**, cada tarjeta tiene **Eliminar** y una confirmación
con el nombre del estudiante. Desde `20261024005000_borrado_definitivo_estudiante.sql`,
la función `eliminar_mi_estudiante` exige que el responsable tenga el vínculo
activo y sea el único responsable activo. En una sola transacción borra el perfil,
sus vínculos, consentimiento, datos de aprendizaje y auditoría asociada. La app
limpia también sesiones, intentos, trazos y sincronizaciones pendientes del
estudiante en el dispositivo actual. Los perfiles compartidos requieren resolver
primero los otros vínculos. La migración también borra los perfiles que la versión
anterior del botón había dejado inactivos.

Cada perfil se crea mediante `crear_mi_estudiante`, que obtiene al responsable
del JWT, valida el rol y crea en una sola transacción al estudiante, el vínculo
principal y el consentimiento `USO_PLATAFORMA`. La versión vigente del texto
de consentimiento que usa Android es `kmat-uso-plataforma-v1`; debe cambiarse
si se modifica el texto legal presentado.

## Configuración

Agrega solo los valores públicos de tu proyecto en las propiedades locales de
Gradle. Nunca agregues una clave `service_role`, una contraseña o un refresh
token al proyecto Android.

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_ANON_KEY=tu_clave_anon_o_publishable
```

Usa las mismas propiedades indicadas en [OFFLINE_FIRST.md](OFFLINE_FIRST.md)
para la sincronización. En Supabase, habilita el proveedor de correo y configura
el correo de confirmación. Aplica las migraciones en orden, incluida
`20261020001000_gestion_perfiles_responsable.sql` y `20261020002000_niveles_basica.sql`, antes de gestionar perfiles.

Aplica también `20261024003000_permisos_lectura_perfiles.sql`: la consulta con
`SECURITY INVOKER` necesita permiso `SELECT` para `authenticated` sobre
`estudiante` y `responsable_estudiante`, además de las políticas RLS. Sin ese
permiso, Supabase devuelve HTTP 403 / SQLSTATE 42501 aunque los estudiantes estén
guardados y vinculados correctamente. Las políticas RLS continúan limitando las
filas visibles a cada responsable o docente autorizado.
El archivo `supabase/diagnostico_lectura_perfiles.sql` permite revisar ambos
niveles de autorización sin modificar datos.

## Restablecimiento de contraseña

La pantalla **Cuentas de usuario** está restringida a una sesión con rol
`ADMINISTRADOR`. Desde allí se pueden borrar cuentas y enviar el enlace de
recuperación a la dirección registrada. La migración
`20261024006000_gestion_cuentas_admin.sql` borra la cuenta, sus vínculos y datos
asociados en una transacción; conserva perfiles de estudiantes compartidos con
otro responsable y bloquea la eliminación de la propia o de la última cuenta
administradora.

## Área docente

La pantalla docente permite crear salas con código de unión, consultar sus
estudiantes, revisar el historial de ejercicios, asignar refuerzos y exportar
reportes CSV individuales o grupales. Las familias vinculan cada perfil desde
la pantalla de selección de estudiante usando el código compartido por el
docente. Estas operaciones requieren la migración
`20261025001000_herramientas_docente.sql`; sus funciones verifican el rol y el
vínculo con la sala o el estudiante en Supabase.

El botón **¿Olvidaste tu contraseña?** solicita el correo de recuperación a
Supabase Auth. Configura en Supabase Auth la URL de redirección o `Site URL`
que recibirá el enlace de recuperación y presentará el cambio de contraseña.
