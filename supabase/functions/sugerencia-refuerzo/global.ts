import { topicDecisions } from "./decision.ts";
import type { Axis, CatalogOption, Summary } from "./logic.ts";

const names: Record<Axis, string> = {
  NUMEROS_OPERACIONES: "Números y operaciones", PATRONES_ALGEBRA: "Patrones y álgebra",
  GEOMETRIA: "Geometría", MEDICION: "Medición", DATOS_PROBABILIDAD: "Datos y probabilidades",
};
const levels = ["inicial", "medio", "alto"];
const list = new Intl.ListFormat("es", { type: "conjunction" });
const questions: Record<string, string[]> = {
  CONTEO: ["¿Cómo puedes comprobar que contaste cada elemento una sola vez?", "¿Desde dónde empezarías a contar?"],
  ADICION: ["¿Qué cantidades vas a juntar?", "¿Cómo comprobarías que tu suma es correcta?"],
  SUSTRACCION: ["¿Qué cantidad tenías al principio y cuánto queda?", "¿Cómo podrías comprobar tu resta con una suma?"],
  TRAZADO_NUMEROS: ["¿Dónde empieza el trazo y qué camino sigue?"],
  PATRONES_NUMERICOS: ["¿Qué cambia de un número al siguiente?", "¿Cómo usarías la regla para encontrar el próximo número?"],
  FIGURAS_2D: ["¿Qué característica te ayudó a reconocer la figura?", "¿Cómo describirías esta figura para que alguien más la reconozca?"],
  LONGITUD: ["¿Cómo decidirías cuál es más largo?", "¿Desde dónde empezarías a medir?"],
  DURACION: ["¿Qué tiempos necesitas comparar?", "¿Cómo comprobarías cuánto tiempo pasó?"],
  TABLAS_CONTEO: ["¿En qué parte de la tabla encontraste el dato?", "¿Cómo comprobarías el total con la tabla?"],
  PICTOGRAMAS: ["¿Qué representa cada símbolo del pictograma?", "¿Qué símbolos necesitas contar para responder?"],
  INTERPRETACION: ["¿Qué dato te ayudó a responder?", "¿Cómo explicarías tu respuesta usando los datos?"],
};

function group(d: ReturnType<typeof topicDecisions>[number]) {
  if (d.action === "OBSERVAR") return "observar";
  if (d.priority === 0) return "reforzar";
  if (d.topic.recent.correct >= 4) return "habitual";
  return "consolidar";
}

export function globalBrief(summaries: Summary[], catalog: CatalogOption[]) {
  const topics = summaries.flatMap(summary => topicDecisions(summary, catalog).map(d => ({
    ...d, axis: summary.axis, group: group(d), questions: questions[d.topic.code] ?? [],
    label: d.topic.code === "GENERAL" ? "ejercicios sin clasificar de " + names[summary.axis]
      : d.topic.code === "FIGURAS_2D" ? "figuras geométricas" : d.topic.label.toLowerCase(),
  })));
  return { summaries, topics };
}
type Brief = ReturnType<typeof globalBrief>;
type Item = Brief["topics"][number];

function example(item: Item, optionIndex: number) {
  const level = levels[item.target!];
  const option = item.options[optionIndex] ?? item.options[0];
  const change = item.action === "BAJAR" ? "baja al nivel " : "mantén el nivel ";
  if (!option) return `Para ${item.label} convendría trabajar en el nivel ${level}, aunque todavía no hay ejercicios publicados de ese contenido en ese nivel.`;
  const count = Math.min(3, option.ejercicios_disponibles);
  return `En ${item.label}, ${change}${level} y asigna ${count} ${count === 1 ? "ejercicio" : "ejercicios"} de «${option.nombre.slice(0, 120)}».`;
}

function trend(item: Item) {
  const { recent, previous, difficulty } = item.topic;
  if (difficulty === null) return "";
  const score = `${recent.correct}/${recent.total}`;
  if (previous.total === 5 && recent.total === 5) {
    const before = `${previous.correct}/${previous.total}`;
    return recent.correct === previous.correct
      ? `En ${item.label} obtuvo ${score} aciertos en cada uno de los dos últimos bloques del nivel ${levels[difficulty]}.`
      : `En ${item.label} pasó de ${before} a ${score} aciertos en el nivel ${levels[difficulty]}.`;
  }
  return `En ${item.label} acertó ${recent.correct} de las últimas ${recent.total} respuestas del nivel ${levels[difficulty]}.`;
}

export type Selection = { topic: number; option: number; question: number };
export function composeGlobal(brief: Brief, selections: Selection[]) {
  // The model orders concrete examples. Coverage and instructional decisions come from the records.
  const ordered = [...new Set([...selections.map(s => s.topic), ...brief.topics.keys()])]
    .map(i => brief.topics[i]).filter(item => item !== undefined);
  const items = (kind: Item["group"]) => ordered.filter(item => item.group === kind);
  const labels = (rows: Item[]) => list.format(rows.map(i => i.label));
  const weak = items("reforzar"), developing = items("consolidar"), good = items("habitual"), observe = items("observar");
  const missing = brief.summaries.filter(s => !s.totalPractice).map(s => names[s.axis]);
  const paragraphs: string[] = [];

  const focus: string[] = [];
  if (weak.length) focus.push(`Dedica la mayor parte de la próxima práctica a ${labels(weak)}, donde los errores recientes superan a los aciertos.`);
  if (developing.length) focus.push(weak.length
    ? `Reserva también un momento para ${labels(developing)} en la dificultad actual.`
    : `Continúa trabajando ${labels(developing)} en la dificultad actual para que el estudiante gane regularidad antes de avanzar.`);
  if (focus.length) paragraphs.push(focus.join(" "));

  // Prefer two different axes among equally urgent needs, when possible.
  const availableFirst = (a: Item, b: Item) => Number(!!b.options.length) - Number(!!a.options.length);
  const candidates = [...weak.sort(availableFirst), ...developing.sort(availableFirst)];
  const first = candidates[0];
  const second = first && (candidates.find(i => i.group === first.group && i.axis !== first.axis) ?? candidates[1]);
  const examples = [first, second].filter((i): i is Item => !!i);
  if (examples.length) paragraphs.push(examples.map(item => {
    const choice = selections.find(s => brief.topics[s.topic] === item);
    return example(item, choice?.option ?? 0);
  }).join(" "));
  const coached = examples.find(i => i.questions.length);
  if (coached) {
    const choice = selections.find(s => brief.topics[s.topic] === coached);
    const question = coached.questions[choice?.question ?? 0] ?? coached.questions[0];
    paragraphs[paragraphs.length - 1] += ` Al revisar ${coached.label}, pregúntale «${question}» para conocer su estrategia.`;
  }

  if (good.length) {
    let routine = `El estudiante está resolviendo bien ${labels(good)}. Mantén estos contenidos en la práctica habitual, sin añadir refuerzo por ahora.`;
    const challenge = !examples.length && good.find(i => i.action === "SUBIR" && i.options.length);
    if (challenge) {
      const choice = selections.find(s => brief.topics[s.topic] === challenge);
      const option = challenge.options[choice?.option ?? 0] ?? challenge.options[0];
      routine += ` En la siguiente sesión habitual de ${challenge.label}, puedes probar un ejercicio de «${option.nombre.slice(0, 120)}» del nivel ${levels[challenge.target!]} para ver cómo aplica lo aprendido.`;
    }
    paragraphs.push(routine);
  }
  if (observe.length) paragraphs.push(`En ${labels(observe)}, observa algunas respuestas más con contenido y dificultad identificados antes de decidir si necesita apoyo.`);

  const practiced = brief.summaries.filter(s => s.totalPractice > 0);
  const totals = practiced.map(s => `${names[s.axis]} (${s.totalCorrect}/${s.totalPractice})`);
  const evidence = [`Aciertos en las prácticas completadas: ${list.format(totals)}.`];
  const highlighted = examples.length ? examples : [...good, ...observe].slice(0, 2);
  evidence.push(...highlighted.map(trend).filter(Boolean));
  if (missing.length) evidence.push(`Aún no hay práctica registrada en ${list.format(missing)}.`);
  return { topic: "Cómo distribuir la próxima práctica", activity: paragraphs.join("\n\n"), evidence: evidence.join(" ") };
}
