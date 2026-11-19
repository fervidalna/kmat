# Progreso compartido y uso sin conexión

Desde la versión 1.2, el progreso pertenece al perfil de estudiante vinculado a la cuenta autenticada. Tablet y celular descargan en Room las mismas sesiones, planes de ejercicios, respuestas y diagnóstico inicial. Cambiar de dispositivo o de día retoma la sesión pendiente y omite las preguntas ya respondidas.

## Funcionamiento

- El primer inicio de sesión y la primera carga de perfiles requieren conexión. Después, la lista se conserva en Room separada por cuenta, se presenta desde el dispositivo al reabrir y se actualiza desde Supabase en segundo plano. Sin conexión se pueden elegir perfiles existentes; crear, vincular o eliminar perfiles sigue requiriendo conexión.
- La práctica abre con el plan y el progreso locales. La sincronización y el intento de reservar una sesión remota no bloquean la apertura; los cambios pendientes se envían al recuperar conexión.
- El modelo de reconocimiento de escritura se descarga la primera vez. Luego se usa localmente; la práctica por selección/conteo no necesita ese modelo.
- Cada respuesta, plan y cierre de sesión se guarda primero en Room junto con su evento pendiente, dentro de una transacción. Se puede seguir sin internet.
- WorkManager envía la cola al recuperar conexión y reintenta si falla. También revisa cada 15 minutos, sujeto a la programación de Android.
- Al abrir práctica se envía y descarga el progreso antes de decidir si corresponde el diagnóstico. Mientras la app está visible se sincroniza cada 30 segundos.
- La pantalla observa el progreso importado y avanza si otro dispositivo ya respondió la pregunta o terminó la sesión.
- El cliente consulta primero los perfiles autorizados. La cola de otras cuentas se conserva, pero no se envía con las credenciales actuales.
- Se procesan todos los lotes pendientes, hasta 50 eventos por petición. La descarga recorre sesiones por páginas de 50 y combina el resultado con los datos locales, sin borrar trabajo pendiente.
- La app conserva el primer diagnóstico y su resultado por eje. El historial importado alimenta la selección adaptativa y prioriza preguntas todavía no realizadas. El banco finito puede volver a usar ejercicios como práctica.

## Consistencia entre dispositivos

El servidor reserva una sesión activa común cuando los equipos tienen conexión. El primer plan aceptado se conserva. La respuesta de un mismo ejercicio dentro de esa sesión tiene un identificador estable; reenviar un lote no la duplica. Si ambos equipos responden simultáneamente, la primera respuesta aceptada por el servidor es la que se comparte. Una sesión completada nunca vuelve a activa por una descarga atrasada.

Sin conexión, un equipo solo conoce el último avance que descargó. Si dos equipos inician sesiones independientes estando desconectados, se conservan ambas historias al reconectarse. No es posible evitar que resuelvan la misma pregunta antes de compartir sus cambios. Los trazos de una respuesta todavía no confirmada permanecen en la pantalla donde se están escribiendo; se sincronizan los ejercicios guardados.

El diagnóstico completado en versiones anteriores puede reconstruirse a partir de una sesión finalizada y sus respuestas. Las sesiones abandonadas no se consideran diagnósticos completados.

## Activación en Supabase

Aplicar `supabase/migrations/20261106001000_sincronizacion_multidispositivo.sql` y luego desplegar `supabase/functions/sync-learning-data/index.ts`. La migración añade el plan y el informe a `sesion_aprendizaje` y tres funciones autenticadas:

- `sincronizar_aprendizaje`: aplica el lote completo en una transacción.
- `descargar_aprendizaje`: devuelve las sesiones de un único perfil autorizado.
- `retomar_sesion_aprendizaje`: reserva o retoma la sesión activa compartida.

Si el historial remoto contiene migraciones aplicadas manualmente, revisar `supabase db push --linked --dry-run` antes de ejecutarlo. No volver a aplicar migraciones antiguas solo porque no estén registradas. Se puede aplicar exclusivamente el archivo nuevo con `supabase db query --linked --file <archivo>` y registrar esa versión con `supabase migration repair 20261106001000 --status applied --linked`.

Instalar la versión actualizada en todos los dispositivos y seleccionar el mismo perfil de estudiante. El APK sigue usando `SUPABASE_URL`, `SUPABASE_ANON_KEY` y `SUPABASE_EXERCISE_VERSION_ID`. Las claves administrativas permanecen en el servidor. Las migraciones Room 4→5 y 5→6 conservan los datos existentes; la última agrega la caché local de perfiles por cuenta.

## Verificación

`MultiDeviceSyncTest` usa dos bases Room independientes y comprueba transferencia del diagnóstico, continuación de práctica y diagnóstico incompleto, separación de perfiles/cuentas, colas mayores de 50 eventos, desconexión, pérdida de confirmación y protección ante una descarga atrasada. Se ejecuta junto con las pruebas del diagnóstico mediante `connectedDebugAndroidTest`.

La prueba del servidor ejecuta el esquema y la migración reales en PostgreSQL en memoria, sin usar datos del proyecto remoto:

```powershell
deno test --config supabase/tests/deno.json --allow-read --allow-env supabase/tests/multidispositivo_test.ts
```
