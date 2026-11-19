# Grabación de cierre de sesión

La pantalla de explicación de sumas reproduce `android-app/app/src/main/res/raw/explicacion_sumar_intro.wav`.
La presentación se generó una vez con Gemini, voz Puck, y queda incluida en la app.
La reproducción no consulta ninguna API.
Los archivos `sumar_*.wav` contienen los pasos, números y resultados de la animación;
también se generaron una vez con Gemini y se reproducen localmente.
Los archivos `ejemplo_*.wav` contienen las introducciones de conteo, patrones, geometría,
medición y datos, y la frase de cierre. Los ejemplos reutilizan los números de
`sumar_*.wav`, sin consultas a Gemini durante la reproducción.
Los archivos `serie_*.wav` contienen narraciones guardadas de ejemplos de series
de conteo de uno, dos, cinco o diez en diez y de conteo hacia atrás.

Entre ejercicios de práctica se reproduce `android-app/app/src/main/res/raw/practica_animo_gemini.wav`,
`practica_animo_sigue.wav` o `practica_animo_excelente.wav`, con las frases
«¡Vas muy bien, felicitaciones!», «¡Muy bien, sigue adelante!» y «¡Excelente, tú puedes!».
Las grabaciones se generaron una vez con Gemini, usando la voz Puck de la pausa activa,
y quedan incluidas en la app. Se elige una al azar sin repetir la anterior. Su reproducción
no consulta ninguna API y no se usa durante el diagnóstico. El archivo generado por
`generate_between_exercises.py` es una versión anterior y no se usa en la app.

La sesión de ejercicios usa `android-app/app/src/main/res/raw/session_lo_lograste.wav`,
con la frase «¡Lo lograste! ¡Muy buen trabajo!». Se genera una vez con
`generate_session_encouragement.py` y se incluye en la app. El resumen del
diagnóstico conserva la grabación de felicitaciones descrita abajo.

- Archivo incluido en la app: `android-app/app/src/main/res/raw/session_felicitaciones.wav`.
- Texto: «¡Felicitaciones!».
- Generación local con [Kokoro-82M v1.0](https://huggingface.co/hexgrad/Kokoro-82M), voz masculina en español `em_santa`, velocidad 0.95 y seseo.
- Modelo publicado bajo Apache-2.0. [Listado original de voces](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md).
- Motor de generación: [kokoro-onnx](https://github.com/thewh1teagle/kokoro-onnx), versión 0.6.1.
- Formato: WAV PCM de 16 bits, mono, 24 kHz.

La app solo distribuye y reproduce la grabación. No incluye el modelo, las voces
del sintetizador ni las dependencias de Python; tampoco utiliza un servicio externo
ni necesita conexión para reproducirla. No se ha usado ElevenLabs.

Para regenerarla, seguir las instrucciones de `generate_congratulations.py`.
Los modelos y el entorno de Python se guardan en `build/`, fuera de Git.

La grabación se reproduce una vez al entrar al resumen de práctica o diagnóstico.
Respeta el volumen multimedia y la disponibilidad de foco de audio. Se libera al
terminar, abandonar la pantalla o pasar la app a segundo plano. Girar el dispositivo
o volver del segundo plano no repite la felicitación.

## Gemini TTS para el ejemplo de trazado

La narración del trazado se generó una vez con Gemini TTS, voz Puck, a través de una función temporal de Supabase. El WAV queda en `android-app/app/src/main/res/raw/trazado_ejemplo_gemini.wav`; la app reproduce ese recurso sin volver a llamar a Gemini. El procedimiento reproducible, la autenticación y los pasos de limpieza están en [GEMINI_VOCES_SUPABASE.md](GEMINI_VOCES_SUPABASE.md).

`generate_number_trace_gemini.py` es una alternativa de llamada directa que requiere una clave local; no fue el procedimiento utilizado para obtener el WAV actual. Para este proyecto se prefiere conservar el secreto en Supabase.
