# Autenticación segura

## Flujo implementado

La pantalla **Ingresar** autentica a los adultos con correo y contraseña en
Supabase Auth. No existe registro público: el administrador crea las cuentas
de responsables y docentes antes de que puedan entrar a la aplicación.

- La contraseña se valida en memoria y se descarta al terminar la solicitud.
- La solicitud se realiza únicamente a una URL `https://` de Supabase.
- La aplicación no permite tráfico HTTP sin cifrar (`usesCleartextTraffic=false`).
- Supabase devuelve tokens de acceso y renovación; ambos se cifran con AES-GCM
  mediante una clave no exportable de Android Keystore.
- Antes de sincronizar, el token se renueva si faltan menos de 60 segundos para
  expirar. Si no existe sesión, los datos offline permanecen en la cola local.
- Los mensajes de acceso son genéricos. El restablecimiento informa que enviará
  instrucciones *si existe una cuenta*, sin revelar si un correo está registrado.

## Configuración

Agrega solo los valores públicos de tu proyecto en las propiedades locales de
Gradle. Nunca agregues una clave `service_role`, una contraseña o un refresh
token al proyecto Android.

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_ANON_KEY=tu_clave_anon_o_publishable
```

Usa las mismas propiedades indicadas en [OFFLINE_FIRST.md](OFFLINE_FIRST.md)
para la sincronización. En Supabase, habilita el proveedor de correo y confirma
las direcciones de los tres adultos antes de permitir su acceso.

## Restablecimiento de contraseña

El botón **¿Olvidaste tu contraseña?** solicita el correo de recuperación a
Supabase Auth. Configura en Supabase Auth la URL de redirección o `Site URL`
que recibirá el enlace de recuperación y presentará el cambio de contraseña.
