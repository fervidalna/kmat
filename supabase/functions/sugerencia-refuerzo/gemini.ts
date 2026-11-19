import type { Summary } from "./logic.ts";

import { choices, type prepare } from "./decision.ts";
import { composeGlobal, type globalBrief, type Selection } from "./global.ts";

export class GeminiError extends Error {
  constructor(readonly status: number, reason?: string) {
    super(`Gemini devolvió HTTP ${status}${reason ? " (" + reason + ")" : ""}.`);
  }
}

export async function generateSuggestion(key: string, summary: Summary, decisions: ReturnType<typeof prepare>, allowed = choices(decisions)): Promise<unknown> {
  const prompt = [
    "¿Qué contenido debe priorizar el docente ahora y conviene bajar, mantener, subir la dificultad, observar o dejar de asignar refuerzo?",
    "Selecciona una de las combinaciones permitidas. Compara los bloques reciente y anterior a igual dificultad, su tamaño y el catálogo publicado.",
    "Prioriza errores persistentes y acciones disponibles. El tiempo del eje es contextual. No cambies el nivel del alumno.",
    "option identifica la opción dentro del contenido; 0 indica que no hay asignación disponible o necesaria.",
    JSON.stringify({ summary, allowed }),
  ].join("\n");
  return askGemini(key, prompt, { type: "OBJECT", properties: {
          content_code: { type: "STRING", enum: [...new Set(allowed.map(c => c.content_code))] },
          action: { type: "STRING", enum: [...new Set(allowed.map(c => c.action))] },
          option: { type: "INTEGER" },
        }, required: ["content_code", "action", "option"] });
}

export async function generateGlobalSuggestion(key: string, brief: ReturnType<typeof globalBrief>, previous?: string) {
  const prompt = [
    "Prepara una sola orientación docente para distribuir la práctica entre todos los contenidos del estudiante.",
    "La síntesis incluirá todos los grupos. Ordena los contenidos dentro de cada grupo según sus resultados recientes, la tendencia a igual dificultad y las opciones publicadas.",
    "Cuando haya necesidades equivalentes, procura ilustrar materias distintas. Elige un ejercicio y una pregunta para explorar la estrategia del alumno donde corresponda.",
    "Devuelve una selección por contenido: topic es su índice; option y question son índices desde cero en sus listas (cero para listas vacías).",
    "Si se solicita otra sugerencia, varía los ejemplos o la pregunta cuando exista una alternativa igualmente adecuada. Respeta la prioridad que indican los resultados.",
    JSON.stringify({ axes: brief.summaries, contents: brief.topics.map((d, topic) => ({
      topic, axis: d.axis, content: d.label, group: d.group, reason: d.reason,
      options: d.options.map(o => o.nombre), questions: d.questions,
    })), previous }),
  ].join("\n");
  const index = { type: "INTEGER", minimum: 0 };
  const answer = await askGemini(key, prompt, { type: "OBJECT", properties: {
    selections: { type: "ARRAY", items: { type: "OBJECT", properties: { topic: index, option: index, question: index },
      required: ["topic", "option", "question"] } },
  }, required: ["selections"] }) as { selections?: Selection[] } | null;
  if (!Array.isArray(answer?.selections)) throw new Error("Plan general inválido");
  const selections = answer.selections.filter(s => s && Number.isInteger(s.topic) && brief.topics[s.topic] &&
    Number.isInteger(s.option) && Number.isInteger(s.question));
  const suggestion = composeGlobal(brief, selections);
  if (suggestion.activity !== previous) return suggestion;
  // Keep the conclusions when regenerating. Vary an available example or teaching question.
  for (const [topic, item] of brief.topics.entries()) {
    const selected = selections.find(s => s.topic === topic);
    const alternative = { topic, option: ((selected?.option ?? 0) + 1) % Math.max(1, item.options.length),
      question: ((selected?.question ?? 0) + 1) % Math.max(1, item.questions.length) };
    const reordered = selections.some(s => s.topic === topic)
      ? selections.map(s => s.topic === topic ? alternative : s) : [...selections, alternative];
    const variant = composeGlobal(brief, reordered);
    if (variant.activity !== previous) return variant;
  }
  return suggestion;
}

async function askGemini(key: string, prompt: string, schema: object): Promise<unknown> {
  const response = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent", {
    method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    signal: AbortSignal.timeout(45_000),
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      generationConfig: { responseMimeType: "application/json", maxOutputTokens: 1024,
        responseSchema: schema },
    }),
  });
  if (!response.ok) {
    const detail = await response.json().catch(() => null);
    const reason = detail?.error?.status;
    throw new GeminiError(response.status, typeof reason === "string" && /^[A-Z_]{2,50}$/.test(reason) ? reason : undefined);
  }
  const data = await response.json();
  const text = data.candidates?.[0]?.content?.parts?.filter((part: { text?: string; thought?: boolean }) => !part.thought && typeof part.text === "string").map((part: { text: string }) => part.text).join("");
  if (!text) throw new Error("Gemini no devolvió una sugerencia");
  return JSON.parse(text);
}
