package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.DiagnosticAxisResult
import cl.kmat.ia.domain.model.DiagnosticAnswer
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.model.Exercise

/** Selects a balanced diagnostic sample and adapts its follow-up questions by axis. */
class DiagnosticAssessmentEngine {
    fun initialQuestions(bank: List<Exercise>, schoolLevel: String): List<Exercise> {
        val baseline = if (schoolLevel == "BASICO_2") 1 else 0
        return AXES.flatMap { axis ->
        val candidates = bank.filter { it.axis == axis }
            listOf(baseline, (baseline + 1).coerceAtMost(2)).mapNotNull { difficulty ->
                candidates.filter { it.difficulty == difficulty }.shuffled().firstOrNull()
            }.distinctBy { it.id }
        }.shuffled()
    }

    fun nextQuestion(
        bank: List<Exercise>,
        schoolLevel: String,
        outcomes: List<DiagnosticAnswer>
    ): Exercise? {
        if (outcomes.size < INITIAL_QUESTIONS) return null
        val usedIds = outcomes.map { it.exerciseId }.toSet()
        val baseline = if (schoolLevel == "BASICO_2") 1 else 0

        // First use one adaptive probe for every axis whose two initial answers did not settle it.
        AXES.forEach { axis ->
            val axisOutcomes = outcomes.filter { it.axis == axis }
            if (axisOutcomes.size == 2 && !isClearAtTwo(axisOutcomes)) {
                val targetDifficulty = when {
                    axisOutcomes.all { it.isCorrect } -> (axisOutcomes.maxOf { it.difficulty } + 1).coerceAtMost(2)
                    axisOutcomes.none { it.isCorrect } -> (axisOutcomes.minOf { it.difficulty } - 1).coerceAtLeast(0)
                    else -> 1
                }
                return choose(bank, axis, targetDifficulty, schoolLevel, usedIds)
            }
        }

        val unresolved = AXES.mapNotNull { axis ->
            val rows = outcomes.filter { it.axis == axis }
            axis.takeIf { rows.size >= 3 && rows.any { it.isCorrect } && rows.any { !it.isCorrect } }
        }
        if (outcomes.size >= MAX_QUESTIONS || unresolved.isEmpty()) return null

        // At most three extra questions; prioritize the axis with the fewest observations.
        val axis = unresolved.minByOrNull { key -> outcomes.count { it.axis == key } } ?: return null
        val rows = outcomes.filter { it.axis == axis }
        val target = when {
            rows.count { it.isCorrect } > rows.count { !it.isCorrect } -> (rows.maxOf { it.difficulty } + 1).coerceAtMost(2)
            rows.count { it.isCorrect } < rows.count { !it.isCorrect } -> (rows.minOf { it.difficulty } - 1).coerceAtLeast(0)
            else -> 1
        }
        return choose(bank, axis, target, schoolLevel, usedIds)
    }

    fun buildReport(outcomes: List<DiagnosticAnswer>): DiagnosticReport {
        val axisResults = AXES.map { axis ->
            val rows = outcomes.filter { it.axis == axis }
            val correct = rows.count { it.isCorrect }
            val rate = if (rows.isEmpty()) 0f else correct.toFloat() / rows.size
            val level = when {
                rate >= 0.8f && rows.any { it.difficulty == 2 && it.isCorrect } -> 3
                rate >= 0.6f -> 2
                else -> 1
            }
            val status = when {
                rows.isEmpty() -> "Por evaluar"
                level == 3 -> "Avance sólido"
                level == 2 -> "En desarrollo"
                else -> "Conviene reforzar"
            }
            DiagnosticAxisResult(
                axis, AXIS_LABELS.getValue(axis), correct, rows.size, status, level,
                rows.count { it.elapsedMillis <= it.expectedTimeMillis }
            )
        }
        val overall = axisResults.map { it.level }.average().toInt().coerceIn(1, 3)
        return DiagnosticReport(overall, outcomes.size, axisResults)
    }

    private fun isClearAtTwo(rows: List<DiagnosticAnswer>): Boolean =
        (rows.all { it.isCorrect } && rows.any { it.difficulty == 2 }) ||
            (rows.none { it.isCorrect } && rows.any { it.difficulty == 0 })

    private fun choose(bank: List<Exercise>, axis: String, difficulty: Int, schoolLevel: String, used: Set<String>): Exercise? {
        val available = bank.filter { it.axis == axis && it.difficulty == difficulty && it.id !in used }
        if (available.isNotEmpty()) return available.random()
        return bank.filter { it.axis == axis && it.id !in used }
            .minByOrNull { kotlin.math.abs(it.difficulty - difficulty) }
    }

    companion object {
        const val MAX_QUESTIONS = 18
        const val INITIAL_QUESTIONS = 10
        val AXES = listOf("NUMEROS_OPERACIONES", "PATRONES_ALGEBRA", "GEOMETRIA", "MEDICION", "DATOS_PROBABILIDAD")
        val AXIS_LABELS = mapOf(
            "NUMEROS_OPERACIONES" to "Números y operaciones",
            "PATRONES_ALGEBRA" to "Patrones y álgebra",
            "GEOMETRIA" to "Geometría",
            "MEDICION" to "Medición",
            "DATOS_PROBABILIDAD" to "Datos y probabilidades"
        )
    }
}
