# Operación offline-first y sincronización

## Datos que permanecen en el dispositivo

Room crea la base `kmat-offline.db`. Guarda:

- Ejercicio disponible para la práctica sin conexión.
- Sesión diaria local por estudiante.
- Cada intento, respuesta, corrección y tiempo empleado.
- Trazos de escritura: coordenadas y momento de cada punto.
- Métricas de tiempo, número de intento y acierto.
- Cola de eventos pendientes de sincronización.

La pantalla de práctica escribe primero en Room. Ningún resultado depende de
internet para ser aceptado o conservado.

## Sincronización

`LearningSyncScheduler` programa:

1. Un trabajo inmediato con la restricción `NetworkType.CONNECTED` al guardar
   sesión o intento.
2. Una revisión periódica cada 15 minutos para recuperar eventos que no se
   pudieron enviar antes.

`LearningSyncWorker` elimina un evento de la cola solamente después de recibir
una respuesta HTTP exitosa del backend. Los errores dejan los datos locales
intactos para un reintento posterior.

## Configuración local de Supabase

Agrega estas propiedades a `~/.gradle/gradle.properties` o al archivo
`android-app/gradle.properties` local, sin subir secretos al repositorio:

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_ANON_KEY=tu_clave_anon_publica
SUPABASE_STUDENT_ID=uuid_del_estudiante_en_supabase
SUPABASE_EXERCISE_VERSION_ID=uuid_de_la_version_publicada_del_ejercicio
```

El trabajador envía los lotes a
`/functions/v1/sync-learning-data`. La función se encuentra en
`supabase/functions/sync-learning-data/index.ts` y se despliega con:

```powershell
supabase functions deploy sync-learning-data
```

La función exige un `access_token` de Supabase Auth de un responsable vinculado
al estudiante. El inicio de sesión lo guarda junto al refresh token cifrado con
Android Keystore; el worker obtiene un token vigente sin intervención del
estudiante. Las preferencias nunca contienen la contraseña ni tokens legibles.

No se incluye una clave `service_role` en Android. Esa clave solo se utiliza
dentro de la Edge Function para escribir en las tablas protegidas.
