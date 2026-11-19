import { strict as assert } from "node:assert";
import { summarize, type LearningSession } from "../functions/sugerencia-refuerzo/logic.ts";
import { prepare, choices, displayEvidence, requestChoices, resolve } from "../functions/sugerencia-refuerzo/decision.ts";
import { generateGlobalSuggestion, generateSuggestion, GeminiError } from "../functions/sugerencia-refuerzo/gemini.ts";
import { composeGlobal, globalBrief } from "../functions/sugerencia-refuerzo/global.ts";
import { AXES } from "../functions/sugerencia-refuerzo/logic.ts";

function results(axis: string, contentCode: string, scores: number[]) {
  return scores.map(v => ({ exercise: { axis, contentCode, difficulty: 1 }, isCorrect: !!v }));
}

Deno.test("una generación cubre dificultades de dos materias, un logro del mismo eje y la falta de evidencia", async () => {
  const attempts = [
    ...results("NUMEROS_OPERACIONES", "ADICION", Array(10).fill(1)),
    ...results("NUMEROS_OPERACIONES", "SUSTRACCION", [1,0,0,1,0]),
    ...results("GEOMETRIA", "FIGURAS_2D", [0,0,1,0,0]),
    ...results("PATRONES_ALGEBRA", "PATRONES_NUMERICOS", [0,0,0,0,1,1,0,1,0,1]),
    ...results("MEDICION", "DURACION", [0,1]),
  ];
  const summaries = AXES.map(axis => summarize([{ sessionType: "PRACTICA", status: "COMPLETED", attempts }], axis));
  const brief = globalBrief(summaries, [
    { eje: "GEOMETRIA", codigo: "FIGURAS_2D", dificultad: 0, nombre: "Contar lados", ejercicios_disponibles: 2 },
    { eje: "NUMEROS_OPERACIONES", codigo: "RESTAS", dificultad: 0, nombre: "Quitar elementos", ejercicios_disponibles: 1 },
  ]);
  const original = globalThis.fetch;
  try {
    globalThis.fetch = (_url, init) => {
      const prompt = JSON.parse(String((init as { body?: unknown })?.body)).contents[0].parts[0].text;
      assert.ok(AXES.every(axis => prompt.includes(axis)));
      assert.ok(prompt.includes("Contar lados") && prompt.includes("Quitar elementos"));
      // Even a partial model result must not narrow the recommendation to its first choice.
      const selections = [{ topic: brief.topics.findIndex(t => t.axis === "GEOMETRIA"), option: 42, question: 1 }];
      return Promise.resolve(Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify({ selections }) }] } }] }));
    };
    const plan = await generateGlobalSuggestion("test", brief);
    assert.match(plan.activity, /mayor parte.*figuras geométricas y restas/);
    assert.match(plan.activity, /2 ejercicios de «Contar lados»/);
    assert.match(plan.activity, /1 ejercicio de «Quitar elementos»/);
    assert.match(plan.activity, /¿Cómo describirías esta figura/);
    assert.match(plan.activity, /también un momento para patrones numéricos/);
    assert.match(plan.activity, /resolviendo bien sumas/);
    assert.match(plan.activity, /duración, observa/);
    assert.match(plan.evidence, /Números y operaciones \(12\/15\)/);
    assert.match(plan.evidence, /no hay práctica registrada en Datos y probabilidades/);
    assert.doesNotMatch(plan.activity, /porcentajes|resolviendo bien restas/);
    assert.ok(plan.activity.length > 280 && plan.activity.length <= 3600);
    const another = await generateGlobalSuggestion("test", brief, plan.activity);
    assert.notEqual(another.activity, plan.activity);
    assert.match(another.activity, /resolviendo bien sumas/);
  } finally { globalThis.fetch = original; }
});

Deno.test("11 de 14 en patrones con dos bloques dominados se mantiene en la práctica habitual", () => {
  const attempts = results("PATRONES_ALGEBRA", "PATRONES_NUMERICOS", [...Array(3).fill(0), ...Array(11).fill(1)]);
  const summaries = AXES.map(axis => summarize([{ sessionType: "PRACTICA", status: "COMPLETED", attempts }], axis));
  const plan = composeGlobal(globalBrief(summaries, []), []);
  assert.match(plan.activity, /resolviendo bien patrones numéricos/);
  assert.doesNotMatch(plan.activity, /mayor parte|asigna |nivel alto/);
  assert.match(plan.evidence, /11\/14.*5\/5 aciertos en cada uno/);
  const advanced = composeGlobal(globalBrief(summaries, [{ eje: "PATRONES_ALGEBRA", codigo: "PATRONES_NUMERICOS",
    dificultad: 2, nombre: "Continuar una serie", ejercicios_disponibles: 2 }]), []);
  assert.match(advanced.activity, /sesión habitual.*«Continuar una serie» del nivel alto/);
});

Deno.test("distingue una mejora de una dificultad persistente y respeta la disponibilidad", () => {
  const attempts = results("NUMEROS_OPERACIONES", "SUSTRACCION", [0,0,0,0,0,0,1,0,1,0]);
  const summaries = AXES.map(axis => summarize([{ sessionType: "PRACTICA", status: "COMPLETED", attempts }], axis));
  const plan = composeGlobal(globalBrief(summaries, []), []);
  assert.match(plan.activity, /trabajando restas en la dificultad actual/);
  assert.match(plan.activity, /no hay ejercicios publicados/);
  assert.match(plan.evidence, /pasó de 0\/5 a 2\/5/);
  assert.doesNotMatch(plan.activity, /asigna |baja al nivel/);
});

Deno.test("interpreta el caso de patrones y conserva los resultados guardados", () => {
  const saved = "En las últimas 14 respuestas de este contenido: 11 correctas y 3 incorrectas.";
  assert.equal(displayEvidence(saved), "El estudiante va bien en este contenido. " + saved);
  const scores = Array.from({ length: 14 }, (_, i) => ![5, 9, 13].includes(i));
  const summary = summarize([{ sessionType: "PRACTICA", status: "COMPLETED", attempts: scores.map(isCorrect => ({
    exercise: { axis: "PATRONES_ALGEBRA", contentCode: "PATRONES_NUMERICOS", difficulty: 1 }, isCorrect
  })) }], "PATRONES_ALGEBRA");
  const plans = prepare(summary, []);
  const result = resolve(choices(plans)[0], plans);
  assert.match(result.evidence, /va bien.*11 de 14/);
  assert.equal(displayEvidence(result.evidence), result.evidence);
  assert.ok(result.evidence.length <= 240);
  assert.match(result.activity, /Mantener la dificultad/);
  assert.match(displayEvidence("En las últimas 8 respuestas de este contenido: 2 correctas y 6 incorrectas."), /todavía le cuesta/);
  assert.match(displayEvidence("En las últimas 2 respuestas de este contenido: 2 correctas y 0 incorrectas."), /pocas respuestas/);
});

Deno.test("solo se agregan prácticas completas de la materia y no se envían datos identificables", () => {
  const axis = "NUMEROS_OPERACIONES";
  const exercise = { id: "private-id", axis, contentCode: "CONTEO", difficulty: 0, expectedTimeMillis: 20_000, statement: "TOMAS PRIVATE" };
  const make = (i: number): LearningSession => ({ sessionType: "PRACTICA", status: "COMPLETED", startedAt: 2000 + i,
    endedAt: 2100 + i, attempts: [{ exercise, isCorrect: i % 5 !== 0, elapsedMillis: 30_000 }] });
  const diagnostic: LearningSession = { sessionType: "DIAGNOSTICO", status: "COMPLETED", startedAt: 1000, endedAt: 1100,
    report: { completedAt: 1100, axes: [{ axis, level: 1 }] }, attempts: [{ exercise, isCorrect: true }] };
  const active = { ...make(99), status: "ACTIVE" };
  const summary = summarize([diagnostic, active, ...Array.from({ length: 21 }, (_, i) => make(i))], axis);
  assert.equal(summary.totalPractice, 21);
  assert.equal(summary.recentTotal, 20);
  assert.equal(summary.recentCorrect, 16);
  assert.equal(summary.level, 2);
  assert.equal(summary.averageSeconds, 30);
  assert.equal(summary.expectedSeconds, 20);
  assert.equal(summary.topics[0].code, "CONTEO");
  assert.equal(JSON.stringify(summary).includes("TOMAS"), false);
  assert.equal(JSON.stringify(summary).includes("private-id"), false);
  assert.deepEqual(summary.topics[0].recent, { total: 5, correct: 4 });
  const catalog = [{ codigo: "CONTEO_1_10", nombre: "Conteo del 1 al 10", eje: axis, dificultad: 1, ejercicios_disponibles: 2 }];
  const plans = prepare(summary, catalog);
  const choice = choices(plans)[0];
  assert.equal(choice.action, "SUBIR");
  assert.match(resolve(choice, plans).activity, /Medio.*2 ejercicios de «Conteo del 1 al 10»/);
  const previous = resolve(choice, plans);
  assert.deepEqual(requestChoices(plans, previous), [choice]);
  const alternatives = prepare(summary, [...catalog, { ...catalog[0], codigo: "CONTEO_1_20", nombre: "Conteo del 1 al 20" }]);
  assert.deepEqual(requestChoices(alternatives, previous).map(c => c.catalog_code), ["CONTEO_1_20"]);
  assert.throws(() => resolve({ ...choice, option: 2 }, plans));
  assert.throws(() => resolve({ ...choice, action: "BAJAR" }, plans));
});

Deno.test("contenido y nivel desconocidos no se inventan", () => {
  const summary = summarize([{ sessionType: "PRACTICA", status: "COMPLETED", attempts: [{ isCorrect: false,
    exercise: { axis: "GEOMETRIA", contentCode: "TEXTO PRIVADO", expectedTimeMillis: 1000 } }] }], "GEOMETRIA");
  assert.equal(summary.level, null);
  assert.equal(summary.topics[0].code, "GENERAL");
  assert.equal(JSON.stringify(summary).includes("TEXTO PRIVADO"), false);
});

Deno.test("no ofrece ejemplos no disponibles y siempre usa acciones dentro de la app", () => {
  const summary = summarize([{ sessionType: "PRACTICA", status: "COMPLETED", attempts: [{ isCorrect: false,
    exercise: { axis: "NUMEROS_OPERACIONES", contentCode: "GENERAL" } }] }], "NUMEROS_OPERACIONES");
  const plans = prepare(summary, []);
  assert.equal(choices(plans)[0].action, "OBSERVAR");
  assert.match(resolve(choices(plans)[0], plans).activity, /Observar/);
});

Deno.test("el cliente de Gemini envía solo el resumen y valida errores de cuota", async () => {
  const summary = summarize([{ sessionType: "PRACTICA", status: "COMPLETED", attempts: [{ isCorrect: false,
    exercise: { axis: "GEOMETRIA", contentCode: "FIGURAS_2D", id: "no-enviar" } }] }], "GEOMETRIA");
  const original = globalThis.fetch;
  try {
    globalThis.fetch = (_input, init) => {
      const body = String((init as { body?: unknown } | undefined)?.body);
      assert.equal(body.includes("no-enviar"), false);
      assert.equal(body.includes("FIGURAS_2D"), true);
      return Promise.resolve(new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ content_code: "FIGURAS_2D", action: "OBSERVAR", option: 0 }) }] } }] }), { status: 200 }));
    };
    assert.equal(resolve(await generateSuggestion("fake-key", summary, prepare(summary, [])), prepare(summary, [])).topic, "Figuras y sus características");
    globalThis.fetch = () => Promise.resolve(new Response("", { status: 429 }));
    await assert.rejects(() => generateSuggestion("fake-key", summary, prepare(summary, [])), (error: unknown) => error instanceof GeminiError && error.status === 429);
  } finally { globalThis.fetch = original; }
});

Deno.test("decisiones distintas a igual contenido según resultados y dificultad", () => {
  const plan = (scores: boolean[], difficulty: number) => prepare(summarize([{
    sessionType: "PRACTICA", status: "COMPLETED", attempts: scores.map(isCorrect => ({
      isCorrect, exercise: { contentCode: "ADICION", axis: "NUMEROS_OPERACIONES", difficulty }
    }))
  }], "NUMEROS_OPERACIONES"), []);
  const weak = plan([false, false, true, false, true], 1);
  assert.equal(choices(weak)[0].action, "BAJAR");
  assert.match(resolve(choices(weak)[0], weak).activity, /Inicial.*no hay ejercicios publicados/);
  assert.equal(choices(plan([true, true, true, false, false], 1))[0].action, "MANTENER");
  assert.equal(choices(plan(Array(10).fill(true), 2))[0].action, "SIN_REFUERZO");
  assert.equal(choices(plan([false, false], 2))[0].action, "OBSERVAR");
});
