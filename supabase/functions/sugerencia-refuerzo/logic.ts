export const AXES = ["NUMEROS_OPERACIONES", "PATRONES_ALGEBRA", "GEOMETRIA", "MEDICION", "DATOS_PROBABILIDAD"] as const;
export type Axis = typeof AXES[number];

const TOPICS: Record<Axis, Record<string, string>> = {
  NUMEROS_OPERACIONES: { TRAZADO_NUMEROS: "Trazado de números", CONTEO: "Conteo", ADICION: "Sumas", SUSTRACCION: "Restas", GENERAL: "Números y operaciones" },
  PATRONES_ALGEBRA: { PATRONES_NUMERICOS: "Patrones numéricos", GENERAL: "Patrones y álgebra" },
  GEOMETRIA: { FIGURAS_2D: "Figuras y sus características", GENERAL: "Geometría" },
  MEDICION: { LONGITUD: "Longitud", DURACION: "Duración", GENERAL: "Medición" },
  DATOS_PROBABILIDAD: { TABLAS_CONTEO: "Tablas de conteo", PICTOGRAMAS: "Pictogramas", INTERPRETACION: "Interpretación de datos", GENERAL: "Datos y probabilidades" },
};

type Exercise = { content?: string; id?: string; axis?: string; contentCode?: string; difficulty?: number; expectedTimeMillis?: number; teacherAssignmentId?: string | null };
type Attempt = { createdAt?: number; exerciseId?: string; exercise?: Exercise | null; isCorrect?: boolean; elapsedMillis?: number };
export type LearningSession = { id?: string; sessionType?: string; status?: string; startedAt?: number; endedAt?: number | null; report?: { completedAt?: number; axes?: Array<{ axis?: string; level?: number }> } | null; plan?: Exercise[]; attempts?: Attempt[] };
export type CatalogOption = { codigo: string; nombre: string; eje: string; dificultad: number; ejercicios_disponibles: number };
type Score = { total: number; correct: number };
export type TopicSummary = { code: string; label: string; total: number; correct: number; difficulty: number | null; recent: Score; previous: Score };
export type Summary = { axis: Axis; level: number | null; totalPractice: number; totalCorrect: number; recentTotal: number; recentCorrect: number; averageSeconds: number | null; expectedSeconds: number | null; topics: TopicSummary[] };

function finiteNumber(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function attemptsFor(session: LearningSession) {
  const plan = new Map((Array.isArray(session.plan) ? session.plan : []).map((item) => [item.id, item]));
  return (Array.isArray(session.attempts) ? session.attempts : []).flatMap((attempt) => {
    const exercise = attempt.exercise ?? plan.get(attempt.exerciseId);
    if (!exercise || typeof attempt.isCorrect !== "boolean") return [];
    return [{ exercise, correct: attempt.isCorrect, at: finiteNumber(attempt.createdAt, finiteNumber(session.endedAt, finiteNumber(session.startedAt, 0))), elapsed: Math.min(Math.max(finiteNumber(attempt.elapsedMillis, 0), 0), 900_000) }];
  });
}

function exerciseAxis(exercise: Exercise): string {
  return exercise.axis || "NUMEROS_OPERACIONES";
}

export function summarize(sessions: LearningSession[], axis: Axis, catalog: CatalogOption[] = []): Summary {
  const diagnostic = sessions.filter((s) => s.sessionType === "DIAGNOSTICO" && s.status === "COMPLETED" && s.report)
    .sort((a, b) => finiteNumber(a.report?.completedAt, finiteNumber(a.endedAt, finiteNumber(a.startedAt, 0))) - finiteNumber(b.report?.completedAt, finiteNumber(b.endedAt, finiteNumber(b.startedAt, 0))))[0];
  const startLevel = diagnostic?.report?.axes?.find((x) => x.axis === axis)?.level;
  let level = typeof startLevel === "number" && startLevel >= 1 && startLevel <= 3 ? startLevel : null;
  const after = finiteNumber(diagnostic?.report?.completedAt, 0);
  const practice = sessions.filter((s) => s.sessionType === "PRACTICA" && s.status === "COMPLETED")
    .sort((a, b) => finiteNumber(a.endedAt, finiteNumber(a.startedAt, 0)) - finiteNumber(b.endedAt, finiteNumber(b.startedAt, 0)) || finiteNumber(a.startedAt, 0) - finiteNumber(b.startedAt, 0));
  let levelAnswers: boolean[] = [];
  if (level !== null) {
    for (const session of practice) {
      if (finiteNumber(session.startedAt, 0) < after) continue;
      let promotion = false;
      for (const row of attemptsFor(session)) {
        if (exerciseAxis(row.exercise) !== axis || row.exercise.difficulty !== level - 1 || promotion) continue;
        if (row.exercise.teacherAssignmentId && row.exercise.contentCode !== "DOCENTE_CLASIFICADO") continue;
        levelAnswers.push(row.correct);
        if (level < 3 && levelAnswers.length >= 20 && levelAnswers.slice(-10).filter(Boolean).length >= 8) promotion = true;
      }
      if (promotion) { level++; levelAnswers = []; }
    }
  }
  const all = practice.flatMap(attemptsFor).filter((row) => exerciseAxis(row.exercise) === axis).sort((a, b) => a.at - b.at);
  const recent = all.slice(-20);
  const grouped = new Map<string, typeof recent>();
  for (const row of recent) {
    let raw = row.exercise.contentCode ?? "";
    if (raw === "DOCENTE_CLASIFICADO") {
      const matches = catalog.filter(option => option.eje === axis && option.nombre === row.exercise.content);
      const codes = new Set(matches.map(option => catalogTopic(option)));
      if (codes.size === 1) raw = [...codes][0];
    }
    if (raw === "GENERAL" || !raw) {
      if (row.exercise.id?.startsWith("offline-count-")) raw = "CONTEO";
      if (row.exercise.id?.startsWith("offline-sum-")) raw = "ADICION";
      if (row.exercise.id?.startsWith("trace-number-")) raw = "TRAZADO_NUMEROS";
    }
    const code = Object.hasOwn(TOPICS[axis], raw) ? raw : "GENERAL";
    const rows = grouped.get(code) ?? [];
    rows.push(row);
    grouped.set(code, rows);
  }
  const score = (rows: typeof recent) => ({ total: rows.length, correct: rows.filter(r => r.correct).length });
  const topics = Array.from(grouped, ([code, rows]) => {
    const last = rows[rows.length - 1].exercise.difficulty;
    const difficulty = last === 0 || last === 1 || last === 2 ? last : null;
    const comparable = rows.filter(r => r.exercise.difficulty === last);
    return { code, label: TOPICS[axis][code], ...score(rows), difficulty,
      recent: score(comparable.slice(-5)), previous: score(comparable.slice(-10, -5)) };
  });
  const avg = (values: number[]) => values.length ? Math.round(values.reduce((a, b) => a + b, 0) / values.length / 1000) : null;
  return { axis, level, totalPractice: all.length, totalCorrect: all.filter(r => r.correct).length,
    recentTotal: recent.length, recentCorrect: recent.filter((r) => r.correct).length,
    averageSeconds: avg(recent.map((r) => r.elapsed)),
    expectedSeconds: avg(recent.map((r) => Math.min(Math.max(finiteNumber(r.exercise.expectedTimeMillis, 30_000), 1000), 600_000))),
    topics: topics.sort((a, b) => a.code.localeCompare(b.code)) };
}

export function catalogTopic(option: CatalogOption): string {
  const code = option.codigo.toUpperCase();
  const topics = TOPICS[option.eje as Axis] ?? {};
  for (const key of Object.keys(topics)) {
    if (code === key || code.startsWith(key + "_")) return key;
  }
  if (option.eje === "NUMEROS_OPERACIONES") {
    if (/^SUMAS?(?:_|$)/.test(code)) return "ADICION";
    if (/^RESTAS?(?:_|$)/.test(code)) return "SUSTRACCION";
  }
  return "GENERAL";
}
