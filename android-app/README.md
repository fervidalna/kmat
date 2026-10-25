# K-Mat IA para Android

Módulo Android del proyecto [K-Mat IA](../README.md). Está creado con Kotlin y Jetpack Compose y contiene la experiencia de práctica, autenticación, persistencia local y sincronización diferida.

## Funcionalidad disponible

- Inicio de sesión, registro de responsables, recuperación de contraseña y cierre de sesión mediante Supabase Auth.
- Redirección por rol: responsable, docente o administrador.
- Ejercicio local `1 + 1`: escritura en un lienzo con dedo o lápiz, reconocimiento del número y corrección inmediata.
- Reconocimiento con Google ML Kit Digital Ink, con modelo español descargable para uso sin conexión.
- Persistencia con Room de ejercicios, sesiones, intentos, trazos, métricas y eventos pendientes de sincronización.
- Sincronización mediante WorkManager de sesiones e intentos hacia la Edge Function `sync-learning-data` al registrar resultados, cerrar la sesión y recuperar conectividad.
- Interfaz adaptable para teléfonos y tabletas.

Las pantallas de diagnóstico, progreso, gestión de perfiles, área docente y administración ya están incorporadas a la navegación, pero aún son vistas iniciales sin gestión completa de datos.

## Estructura

```text
app/src/main/java/cl/kmat/ia/
├── data/           Autenticación, Room, ML Kit, sincronización y repositorios
├── domain/         Modelos, contratos y casos de uso
├── presentation/   Pantallas, navegación, ViewModels y diseño Compose
├── KMatApplication.kt
└── MainActivity.kt
```

## Requisitos

- Android Studio con su JDK integrado.
- Android SDK Platform 35.
- Dispositivo o emulador con Android 8.0 (API 26) o superior.

Abre esta carpeta (`android-app`) en Android Studio, sincroniza Gradle y ejecuta la configuración `app`.

## Configurar Supabase para desarrollo

Sin estas propiedades no estarán disponibles el inicio de sesión ni la sincronización remota. Una práctica ya iniciada conserva y corrige sus resultados localmente cuando no hay red, tras descargar el modelo de escritura.

Agrega las propiedades en `%USERPROFILE%\.gradle\gradle.properties` para conservarlas fuera del repositorio. Gradle también acepta estas propiedades como variables de entorno:

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_ANON_KEY=tu_clave_anon_publica
SUPABASE_EXERCISE_VERSION_ID=uuid_de_la_version_publicada
```

Prepara primero el proyecto de Supabase y despliega la Edge Function siguiendo la [documentación de base de datos](../supabase/DOCUMENTACION_BASE_DE_DATOS.md). La guía de funcionamiento local está en [OFFLINE_FIRST.md](docs/OFFLINE_FIRST.md).

### Compilar desde GitHub Actions

El workflow `.github/workflows/android.yml` compila un APK cuando haces push a
`main` o `master`, o cuando lo ejecutas manualmente. En GitHub, agrega
`SUPABASE_URL` y `SUPABASE_ANON_KEY` en **Settings > Secrets and variables >
Actions > New repository secret**. Agrega también `SUPABASE_EXERCISE_VERSION_ID`
si necesitas sincronización remota. Los estudiantes deben estar vinculados a
la cuenta responsable en `responsable_estudiante`; la app sincroniza el progreso
en los dispositivos donde se inicie sesión con esa misma cuenta. Esta versión
requiere exactamente un estudiante vinculado a la cuenta. Descarga el
APK desde **Actions > ejecución > Artifacts**. Los secretos de GitHub solo están
disponibles durante esa compilación; para ejecutar desde Android Studio en otra
computadora, configura sus propiedades locales de Gradle.

## Comandos útiles

Desde esta carpeta:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/k-mat-1.0.apk`.

### Generar un APK release firmado

Para compartir la app fuera de Android Studio, genera un APK release firmado con
una clave propia. Crea y respalda la clave una sola vez; no la subas al
repositorio ni la pierdas, porque las actualizaciones deben firmarse con esa
misma clave. Por ejemplo, desde `android-app`:

```powershell
keytool -genkeypair -v -keystore release-key.jks -alias k-mat -keyalg RSA -keysize 2048 -validity 10000
```

Configura estas propiedades en `%USERPROFILE%\.gradle\gradle.properties` (usa
la ruta absoluta al archivo de clave):

```properties
K_MAT_SIGNING_STORE_FILE=C:/ruta/segura/release-key.jks
K_MAT_SIGNING_STORE_PASSWORD=...
K_MAT_SIGNING_KEY_ALIAS=k-mat
K_MAT_SIGNING_KEY_PASSWORD=...
```

Luego ejecuta ` .\gradlew.bat assembleRelease`. El APK firmado queda en
`app/build/outputs/apk/release/k-mat-1.0.apk`. En GitHub Actions, carga el
keystore codificado en Base64 como `K_MAT_SIGNING_KEYSTORE_BASE64` y los otros
tres valores como secretos `K_MAT_SIGNING_STORE_PASSWORD`,
`K_MAT_SIGNING_KEY_ALIAS` y `K_MAT_SIGNING_KEY_PASSWORD`. El workflow publica
el APK release firmado como artifact.

La firma permite que Android verifique el APK y que las actualizaciones
conserven la identidad del publicador. Al instalar un APK descargado fuera de
Google Play, Android todavía puede pedir autorización para instalar desde esa
fuente; la firma no quita ese control del sistema.

## Pruebas incluidas

Las pruebas instrumentadas en `app/src/androidTest` cubren el reconocimiento de escritura y el flujo de práctica: escribir un número, reconocerlo, limpiar el lienzo y validar `1 + 1 = 2`.

## Más documentación

- [Resumen del proyecto](../README.md)
- [Arquitectura](docs/ARQUITECTURA.md)
- [Autenticación](docs/AUTENTICACION.md)
- [Navegación](docs/NAVEGACION.md)
- [Modo offline y sincronización](docs/OFFLINE_FIRST.md)
