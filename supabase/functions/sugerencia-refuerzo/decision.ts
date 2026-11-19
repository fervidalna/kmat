import { catalogTopic, type CatalogOption, type Summary, type TopicSummary } from "./logic.ts";

const levels = ["Inicial", "Medio", "Alto"];
type Action = "OBSERVAR" | "BAJAR" | "MANTENER" | "SUBIR" | "SIN_REFUERZO";
type Decision = ReturnType<typeof decide>;

function describePerformance(correct: number, total: number): string {
  if (total < 5) return "Todavía hay pocas respuestas para valorar su desempeño.";
  if (correct / total >= 0.75) return "El estudiante va bien en este contenido.";
  if (correct * 2 < total) return "Este contenido todavía le cuesta al estudiante.";
  return "El estudiante acierta en parte de los ejercicios y aún necesita práctica.";
}

// Older suggestions kept counts only in these two server-generated formats.
// Interpret the saved snapshot so a later practice cannot change its meaning.
export function displayEvidence(evidence: string): string {
  const legacy = /^En las últimas (\d+) respuestas? de este contenido: (\d+) correctas? y (\d+) incorrectas?\.$/u.exec(evidence);
  if (legacy && Number(legacy[1]) === Number(legacy[2]) + Number(legacy[3])) {
    return describePerformance(Number(legacy[2]), Number(legacy[1])) + " " + evidence;
  }
  const block = /Últimas respuestas: (\d+)\/(\d+) correctas\./u.exec(evidence);
  if (block) return describePerformance(Number(block[1]), Number(block[2])) + " " + evidence;
  return evidence;
}

function decide(topic: TopicSummary, catalog: CatalogOption[]) {
  const { recent, previous, difficulty } = topic;
  const comparable = recent.total === 5 && previous.total === 5;
  const improving = comparable && recent.correct >= previous.correct + 2;
  const strong = comparable && recent.correct >= 4 && previous.correct >= 4;
  let action: Action = "OBSERVAR";
  let target = difficulty;
  let priority = 3;
  let reason = "Reunir más respuestas comparables antes de elegir la dificultad.";
  if (topic.code === "GENERAL" || difficulty === null) {
    reason = "Falta identificar el contenido o la dificultad de esos ejercicios.";
  } else if (recent.total === 5) {
    if (recent.correct <= 2 && !improving) {
      action = difficulty > 0 ? "BAJAR" : "MANTENER";
      target = Math.max(0, difficulty - 1);
      priority = 0;
      reason = "Conviene consolidar este contenido porque predominan los errores recientes.";
    } else if (strong) {
      const next = difficulty + 1;
      const canAdvance = catalog.some(o => catalogTopic(o) === topic.code &&
        o.dificultad === next && o.ejercicios_disponibles > 0);
      action = canAdvance ? "SUBIR" : "SIN_REFUERZO";
      target = canAdvance ? next : difficulty;
      priority = 2;
      reason = "Sostuvo al menos cuatro aciertos en dos bloques seguidos de cinco.";
    } else {
      action = "MANTENER";
      priority = 1;
      reason = improving ? "Los aciertos aumentaron al practicar en la misma dificultad."
        : "Conviene confirmar la regularidad de los aciertos en esta dificultad.";
    }
  }
  const needsExercises = ["BAJAR", "MANTENER", "SUBIR"].includes(action);
  const options = needsExercises ? catalog.filter(o => catalogTopic(o) === topic.code &&
    o.dificultad === target && o.ejercicios_disponibles > 0) : [];
  return { topic, action, target, priority, reason, needsExercises, options,
    confidence: action === "OBSERVAR" ? "limitada" : comparable ? "alta" : recent.total === 5 ? "moderada" : "limitada" };
}

export function topicDecisions(summary: Summary, catalog: CatalogOption[]) {
  const options = catalog.filter(o => o.eje === summary.axis);
  return summary.topics.map(t => decide(t, options));
}

export function prepare(summary: Summary, catalog: CatalogOption[]) {
  const decisions = topicDecisions(summary, catalog);
  const priority = Math.min(...decisions.map(d => d.priority));
  return decisions.filter(d => d.priority === priority);
}

export function choices(decisions: Decision[]) {
  return decisions.flatMap(d => {
    const base = { content_code: d.topic.code, action: d.action, difficulty: d.target,
      confidence: d.confidence, reason: d.reason };
    if (!d.options.length) return [{ ...base, option: 0, catalog_code: "", count: 0 }];
    return d.options.map((o, i) => ({ ...base, option: i + 1, catalog_code: o.codigo, count: Math.min(3, o.ejercicios_disponibles) }));
  });
}

export function resolve(value: unknown, decisions: Decision[]) {
  const output = value as { content_code?: string; action?: string; option?: number } | null;
  const selected = choices(decisions).find(c => c.content_code === output?.content_code &&
    c.action === output?.action && c.option === output?.option);
  if (!selected) throw new Error("La decisión no corresponde a la evidencia o al catálogo.");
  const d = decisions.find(d => d.topic.code === selected.content_code)!;
  const t = d.topic;
  const evidence = [describePerformance(t.correct, t.total),
    `Acertó ${t.correct} de ${t.total}.`,
    t.difficulty === null ? "Recientes" : "Nivel " + levels[t.difficulty],
    `· últimos: ${t.recent.correct}/${t.recent.total}.`,
    t.previous.total ? `Anteriores: ${t.previous.correct}/${t.previous.total}.` : "",
    d.reason, "Confianza " + d.confidence + "."].filter(Boolean).join(" ");
  let activity = "Observar las próximas prácticas antes de asignar refuerzo.";
  if (d.action === "SIN_REFUERZO") activity = "No asignar más refuerzo de este contenido por ahora.";
  if (d.needsExercises) {
    const verb = { BAJAR: "Bajar", MANTENER: "Mantener", SUBIR: "Subir" }[d.action as "BAJAR" | "MANTENER" | "SUBIR"];
    const option = d.options[selected.option - 1];
    activity = verb + " la dificultad (" + levels[d.target!] + "). " +
      (option ? "Asignar " + selected.count + (selected.count === 1 ? " ejercicio de «" : " ejercicios de «") + option.nombre.slice(0, 120) + "»."
        : "Actualmente no hay ejercicios publicados de este contenido en esa dificultad.");
  }
  return { topic: t.label, evidence, activity };
}

export function requestChoices(decisions: Decision[], previous?: {topic?: string; activity?: string}) {
  const allowed = choices(decisions);
  if (!previous?.activity || allowed.length < 2) return allowed;
  const alternatives = allowed.filter(option => {
    const proposal = resolve(option, decisions);
    return proposal.topic !== previous.topic || proposal.activity !== previous.activity;
  });
  return alternatives.length ? alternatives : allowed;
}
