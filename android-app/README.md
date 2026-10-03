# K-Mat IA para Android

Módulo Android del proyecto [K-Mat IA](../README.md). Está creado con Kotlin y Jetpack Compose y contiene la experiencia de práctica, autenticación, persistencia local y sincronización diferida.

## Funcionalidad disponible

- Inicio de sesión, registro de responsables, recuperación de contraseña y cierre de sesión mediante Supabase Auth.
- Redirección por rol: responsable, docente o administrador.
- Ejercicio local `1 + 1`: escritura en un lienzo con dedo o lápiz, reconocimiento del número y corrección inmediata.
- Reconocimiento con Google ML Kit Digital Ink, con modelo español descargable para uso sin conexión.
- Persistencia con Room de ejercicios, sesiones, intentos, trazos, métricas y eventos pendientes de sincronización.
- Sincronización mediante WorkManager hacia la Edge Function `sync-learning-data` cuando existe conectividad y configuración remota.
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

Agrega las propiedades en `%USERPROFILE%\.gradle\gradle.properties` para conservarlas fuera del repositorio:

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_ANON_KEY=tu_clave_anon_publica
SUPABASE_STUDENT_ID=uuid_del_estudiante_en_supabase
SUPABASE_EXERCISE_VERSION_ID=uuid_de_la_version_publicada
```

Prepara primero el proyecto de Supabase y despliega la Edge Function siguiendo la [documentación de base de datos](../supabase/DOCUMENTACION_BASE_DE_DATOS.md). La guía de funcionamiento local está en [OFFLINE_FIRST.md](docs/OFFLINE_FIRST.md).

## Comandos útiles

Desde esta carpeta:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Pruebas incluidas

Las pruebas instrumentadas en `app/src/androidTest` cubren el reconocimiento de escritura y el flujo de práctica: escribir un número, reconocerlo, limpiar el lienzo y validar `1 + 1 = 2`.

## Más documentación

- [Resumen del proyecto](../README.md)
- [Arquitectura](docs/ARQUITECTURA.md)
- [Autenticación](docs/AUTENTICACION.md)
- [Navegación](docs/NAVEGACION.md)
- [Modo offline y sincronización](docs/OFFLINE_FIRST.md)
