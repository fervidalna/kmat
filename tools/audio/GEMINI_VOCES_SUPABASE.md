# Generación de voces con Gemini mediante Supabase

Este documento describe el procedimiento utilizado el 17 de noviembre de 2026 para generar la narración del ejemplo de trazado. La clave de Gemini permanece en los secretos de Supabase. Se genera un WAV durante el desarrollo, se incorpora al proyecto Android y después se reproduce localmente en cada dispositivo.

## Qué se generó

- Recurso: `android-app/app/src/main/res/raw/trazado_ejemplo_gemini.wav`.
- Texto enviado: «Mira cómo trazamos este número. Empieza en el punto amarillo y sigue los puntos en orden. Si te saltas hasta dos puntos, continúa. Retoma al marcar tres puntos seguidos. Los puntos omitidos no cuentan.»
- Modelo configurado: `gemini-3.8-flash-lite-tts`.
- Voz: `Puck`; idioma solicitado: `es-US`; estilo: alegre, cercano y enérgico, en español latinoamericano.
- El WAV obtenido tiene 13,48 segundos. El modelo, la API y su disponibilidad pueden cambiar; si falla, revisar el mensaje de Google y la configuración vigente del proyecto.
- El ejemplo lo busca en `NumberTraceExampleScreen.kt` mediante `getIdentifier`. `ExplanationVoice` lo reproduce y `TalkingPracticePuppy` mueve la boca mientras el reproductor está activo. El trazo se anima en paralelo.

La generación consume cuota de Gemini. La reproducción del WAV incorporado no hace solicitudes de red ni consume tokens. Para distribuir una grabación nueva, debe estar en `res/raw` antes de compilar la aplicación que se vaya a instalar.

## Accesos y componentes

1. El propietario guarda el secreto `GEMINI_API_KEY` en **Supabase Dashboard → Project Settings → Edge Functions → Secrets**. Solo la función de Supabase lee su valor con `Deno.env.get("GEMINI_API_KEY")`; no hace falta mostrárselo al desarrollador ni incluirlo en el código o en el APK.
2. Para publicar y borrar funciones se necesita una **sesión de administración de Supabase CLI** autorizada para el proyecto. La URL del proyecto, su clave pública y el nombre del secreto por sí solos no proporcionan ese acceso. Un proyecto enlazado en `supabase/.temp` tampoco garantiza que la sesión administrativa siga activa.
3. Para invocar la función se necesita una **sesión de usuario válida de la app**. La función valida el JWT con `supabase.auth.getUser()`. Ese JWT y la clave pública de Supabase son credenciales distintas del secreto de Gemini. La app guarda la sesión cifrada mediante `SecureSessionStore`; no imprimir ni copiar los tokens a logs o al repositorio.
4. Para la captura descrita abajo se usó el emulador Android, la app con una cuenta iniciada, `adb`, Android Studio/Gradle y un APK de instrumentación firmado de forma compatible con la app instalada.

La función de referencia es `supabase/functions/gemini-pause-voice/index.ts`. Su frase está fijada a «¡Vamos, ahora a saltar!» y el cuerpo de la solicitud no selecciona otra frase. No reemplazar esa función de producción para generar un audio nuevo: preparar una función temporal con nombre distinto y texto fijo.

## 1. Preparar una función temporal

Desde la raíz del repositorio, crear `supabase/functions/gemini-trazado-voz/index.ts` a partir de `supabase/functions/gemini-pause-voice/index.ts`. Cambiar únicamente `SPOKEN_PHRASE` por la frase deseada. Para el trazado se usó el texto documentado arriba. Mantener la validación de usuario, el acceso a `GEMINI_API_KEY`, el modelo y la configuración de voz si se busca el mismo comportamiento. El texto debe ser fijo y apropiado para la aplicación; no publicar un generador abierto de texto arbitrario.

El flujo de la función es:

1. Comprueba el método `POST`, la sesión de Supabase y que el secreto esté configurado.
2. Envía una solicitud a `https://generativelanguage.googleapis.com/v1beta/interactions` con el modelo, la frase, metadatos de estilo, `response_format: { type: "audio" }` y `speech_config` para Puck y `es-US`.
3. Busca el bloque de audio en la respuesta, decodifica el contenido Base64 y comprueba el encabezado `RIFF`.
4. Devuelve JSON con `audio_base64` y `mime_type: "audio/wav"` al usuario autenticado. Nunca devuelve la clave de Gemini.

La estructura exacta que se utilizó está en la función de referencia. Si Google modifica la API o el modelo, revisar su documentación oficial antes de adaptar el código.

## 2. Publicar la función temporal

Comprobar primero que Supabase CLI puede listar el proyecto sin imprimir credenciales. Si no existe una sesión válida, el administrador debe autenticar la CLI por el procedimiento de Supabase. No solicitar al usuario que publique la clave de Gemini en el chat.

Ejemplo de Windows PowerShell desde la raíz; la ruta del ejecutable depende de la instalación:

```powershell
$supabaseCli = 'node_modules/@supabase/cli-windows-x64/bin/supabase.exe'
& $supabaseCli projects list
& $supabaseCli functions deploy gemini-trazado-voz --project-ref <PROJECT_REF>
```

En esta máquina, el ejecutable de Windows dentro de `node_modules` funcionó; el lanzador `node_modules/.bin/supabase.cmd` falló. Además, la CLI recibió `EPERM` al escribir su archivo de telemetría dentro del entorno restringido. Ejecutarla con el acceso que permita leer su sesión y escribir esa telemetría resolvió el problema. Verificar siempre el código de salida y que el proyecto listado sea el correcto antes de publicar.

## 3. Invocar desde la sesión de Android y conservar el archivo

El 17 de noviembre se creó temporalmente una prueba de instrumentación que se ejecutó dentro del contexto de la aplicación instalada. La prueba obtuvo el token mediante `app.container.authRepository.validAccessTokenOrNull()`, envió un `POST` a la función temporal con `Authorization: Bearer <token>` y la clave pública `apikey`, decodificó `audio_base64` y guardó el WAV en Descargas usando `MediaStore`. No se expuso el token en la salida.

Ejemplo de la parte central, dentro de una prueba Android instrumentada temporal. Utiliza los valores `BuildConfig.SUPABASE_URL` y `BuildConfig.SUPABASE_ANON_KEY` ya configurados para la app; imports omitidos:

```kotlin
val app = ApplicationProvider.getApplicationContext<KMatApplication>()
val token = runBlocking { app.container.authRepository.validAccessTokenOrNull() }
    ?: error("Inicia sesión en la app del emulador")
val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') +
    "/functions/v1/gemini-trazado-voz").openConnection() as HttpURLConnection
try {
    connection.requestMethod = "POST"
    connection.connectTimeout = 30_000
    connection.readTimeout = 120_000
    connection.setRequestProperty("Authorization", "Bearer $token")
    connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true
    connection.outputStream.use { it.write("{}".toByteArray()) }
    val status = connection.responseCode
    val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
        .bufferedReader().use { it.readText() }
    check(status in 200..299) { "La función respondió HTTP $status" }
    val wav = Base64.decode(JSONObject(body).getString("audio_base64"), Base64.DEFAULT)
    check(wav.size > 44 && String(wav.copyOfRange(0, 4)) == "RIFF")
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "trazado_ejemplo_gemini.wav")
        put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val uri = requireNotNull(app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
    requireNotNull(app.contentResolver.openOutputStream(uri)).use { it.write(wav) }
} finally {
    connection.disconnect()
}
```

Antes de ejecutar, confirmar que el nombre del archivo en Descargas no exista; MediaStore puede cambiar el nombre si hay una colisión. Usar un nombre único y recuperar exactamente el archivo creado. No imprimir el cuerpo de una respuesta exitosa: contiene todo el audio en Base64.

**No usar `connectedDebugAndroidTest` para esta captura sin verificar antes su comportamiento de instalación y limpieza.** En el primer intento, Gradle desinstaló la app al terminar y eliminó la sesión y el WAV que estaba en `cacheDir`. La captura tuvo que repetirse después de reinstalar la app e iniciar sesión de nuevo. Para evitarlo se compiló por separado el APK de instrumentación, se instaló solo ese APK y se ejecutó el runner manualmente:

```powershell
# Desde android-app, con JAVA_HOME apuntando al JBR de Android Studio.
.\gradlew.bat :app:assembleDebugAndroidTest

# Desde la raíz del repositorio. La app ya debe estar instalada y con sesión iniciada.
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb install -r 'android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
& $adb shell am instrument -w -e class cl.kmat.ia.practice.TemporaryTraceAudioCaptureTest cl.kmat.ia.test/androidx.test.runner.AndroidJUnitRunner
& $adb pull '/sdcard/Download/trazado_ejemplo_gemini.wav' 'android-app/app/src/main/res/raw/trazado_ejemplo_gemini.wav'
```

La clase `TemporaryTraceAudioCaptureTest` fue creada para esa captura y eliminada después; el fragmento anterior permite reconstruirla como prueba JUnit4 con `AndroidJUnit4`. Usar un emulador de desarrollo y comprobar el dispositivo seleccionado si hay varios. No reinstalar ni desinstalar la app de la persona sin comprender el impacto en su sesión y datos.

## 4. Verificar, integrar y limpiar

1. Verificar que el archivo copiado sea un WAV real y no un mensaje de error guardado como `.wav`. Por ejemplo, `python -c "import wave; w=wave.open('android-app/app/src/main/res/raw/trazado_ejemplo_gemini.wav'); print(w.getnframes()/w.getframerate())"` debe abrirlo y mostrar una duración razonable. Escucharlo en un dispositivo para comprobar voz, frase y calidad; comprobar que no hay información inesperada en el audio.
2. Referenciar el recurso desde la pantalla adecuada, coordinar la animación con la reproducción y compilar la app. Probar el ejemplo con sonido y también sin conexión. En la captura documentada se verificaron el encabezado, el WAV y la compilación; la validación audible en dispositivo debe realizarse al integrar o cambiar la grabación.
3. Eliminar de Descargas la copia temporal una vez verificado el WAV del repositorio y retirar la clase de instrumentación temporal. No eliminar la sesión de la app.
4. Eliminar la función temporal del proyecto correcto:

   ```powershell
   & $supabaseCli functions delete gemini-trazado-voz --project-ref <PROJECT_REF> --yes
   ```

5. Confirmar que la función temporal fue eliminada y que `gemini-pause-voice` sigue disponible. Retirar también el código temporal local si ya no es necesario. No borrar el secreto `GEMINI_API_KEY`.

## Diagnóstico

- **401:** confirmar que la sesión del usuario sigue activa, que el JWT corresponde al proyecto y que se enviaron `Authorization` y `apikey`. No exponer el JWT.
- **503 por clave no configurada:** el administrador revisa que `GEMINI_API_KEY` exista en los secretos de la misma instancia de Supabase. No necesita compartir su valor.
- **502 o cuota de Gemini:** revisar los logs de la función y el acceso al modelo; no repetir llamadas de forma automática e ilimitada. Comprobar la cuota y que el modelo siga disponible.
- **No se encuentra el WAV o solo pesa unos pocos bytes:** puede ser un error de `adb`, una ruta incorrecta, una colisión en Descargas o una app desinstalada. No incorporar el archivo hasta comprobar `RIFF` y abrirlo con un lector WAV.
- **El ejemplo está mudo:** comprobar que el WAV esté en `res/raw`, que se haya recompilado e instalado esa versión, que el volumen multimedia esté activo y que se haya concedido el foco de audio. La búsqueda por nombre en `NumberTraceExampleScreen.kt` no falla al compilar si falta el recurso: sin el WAV la animación funciona, pero no habrá voz.
- **La CLI no puede publicar:** diferenciar la sesión administrativa de Supabase de la sesión de usuario de la app. Revisar también permisos del entorno para los archivos locales de CLI y acceso de red.
