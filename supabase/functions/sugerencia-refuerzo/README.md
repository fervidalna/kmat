# Sugerencias de refuerzo para docentes

La función `sugerencia-refuerzo` complementa el motor adaptativo. El docente abre
el progreso individual de un estudiante de su sala y pulsa **Generar sugerencia**
al final de la pantalla. La pantalla también permite asignar ejercicios con el flujo docente
existente; la sugerencia no modifica niveles ni asigna trabajo automáticamente.

- Al abrir el progreso se muestra el botón Generar sugerencia. Cada pulsación solicita una generación que compara las cinco materias.
- La primera generación requiere cinco respuestas de práctica completadas entre todas las materias. Se comparan los aciertos históricos y hasta 20 respuestas recientes por materia.
  Desde entonces se puede pedir otra inmediatamente. Si hay alternativas igualmente prioritarias, se excluye la recomendación anterior.
- Una reserva temporal en la base evita que solicitudes simultáneas generen duplicados.
- Antes de leer o generar, la función verifica la vinculación docente-estudiante
  mediante `docente_descargar_aprendizaje`. La tabla de caché no concede acceso
  directo a los clientes y elimina sus registros al borrar el perfil del estudiante.
- Gemini recibe solo estadísticas recientes, nivel si está disponible y códigos de
  contenidos de una lista permitida; no recibe el nombre, identificadores, enunciados,
  respuestas escritas ni trazos. La evidencia numérica que se muestra se calcula
  en el servidor y no se inventa a partir de la respuesta del modelo.
- Gemini elige un contenido y una estrategia entre opciones permitidas. El servidor
  construye la propuesta usando únicamente acciones disponibles dentro de la app.
  Las sugerencias antiguas de texto libre se reemplazan solo al pulsar generar.
- Si hay error, límite de uso o falta conexión, la sugerencia anterior se conserva
  y el motor adaptativo sigue funcionando sin Gemini.

Requiere las migraciones hasta `20261117003000_sugerencia_global.sql`, la Edge Function
`sugerencia-refuerzo` y el secreto `GEMINI_API_KEY` en Supabase. El modelo de texto
utilizado es `gemini-3.5-flash-lite`; el acceso y la cuota dependen de la cuenta.
La clave y el rol de servicio permanecen en el servidor y no se incluyen en el APK.

Pruebas locales:

```powershell
deno test --config supabase/tests/deno.json --allow-read --allow-env supabase/tests/sugerencias_logic_test.ts supabase/tests/sugerencias_db_test.ts
```
