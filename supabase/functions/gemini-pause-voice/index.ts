import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const MODEL = "gemini-3.8-flash-lite-tts";
const SPOKEN_PHRASE = "¡Vamos, ahora a saltar!";

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ error: "Método no permitido" }, 405);

  const authorization = request.headers.get("Authorization");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  if (!authorization || !supabaseUrl || !anonKey) return json({ error: "Sesión autenticada requerida" }, 401);
  if (!geminiKey) return json({ error: "La voz Gemini todavía no está configurada en Supabase." }, 503);

  const supabase = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const { data: { user }, error: authError } = await supabase.auth.getUser();
  if (authError || !user) return json({ error: "Sesión inválida" }, 401);

  try {
    const response = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": geminiKey },
        body: JSON.stringify({
          model: MODEL,
          input: [{
            type: "user_input",
            content: [{
              type: "text",
              text: SPOKEN_PHRASE,
              annotations: [{
                type: "speech_metadata",
                style: "alegre, cercana y enérgica; español latinoamericano",
              }],
            }],
          }],
          response_format: { type: "audio" },
          generation_config: {
            speech_config: [{ voice: "Puck", language: "es-US" }],
          },
        }),
      });
    if (!response.ok) {
      console.error("Gemini TTS request failed", response.status, await response.text());
      return json({ error: "Gemini no pudo generar la voz. Revisa la clave y el acceso al modelo." }, 502);
    }

    const result = await response.json();
    const audioBlock = result.steps
      ?.filter((step: { type?: string }) => step.type === "model_output")
      .flatMap((step: { content?: Array<{ type?: string; data?: string }> }) => step.content ?? [])
      .find((item: { type?: string; data?: string }) => item.type === "audio" && item.data)
      ?? result.outputs?.find((item: { type?: string; data?: string }) => item.type === "audio" && item.data)
      ?? result.output_audio;
    const audioBase64 = audioBlock?.data;
    if (typeof audioBase64 !== "string" || !audioBase64) {
      console.error("Gemini TTS response had no audio block", JSON.stringify({
        keys: Object.keys(result),
        steps: result.steps?.map((step: { type?: string; content?: Array<{ type?: string }> }) => ({
          type: step.type,
          contentTypes: step.content?.map((item) => item.type),
        })),
        outputTypes: result.outputs?.map((item: { type?: string }) => item.type),
      }));
      return json({ error: "Gemini no devolvió audio." }, 502);
    }
    const wav = Uint8Array.from(atob(audioBase64), (character) => character.charCodeAt(0));
    if (String.fromCharCode(...wav.subarray(0, 4)) !== "RIFF") {
      return json({ error: "Gemini devolvió un audio WAV inválido." }, 502);
    }
    return json({ audio_base64: bytesToBase64(wav), mime_type: "audio/wav" }, 200);
  } catch (error) {
    console.error("Gemini TTS function error", error);
    return json({ error: "No se pudo conectar con Gemini. Inténtalo otra vez." }, 502);
  }
});

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary);
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
