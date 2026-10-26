# Grabación de cierre de sesión

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
