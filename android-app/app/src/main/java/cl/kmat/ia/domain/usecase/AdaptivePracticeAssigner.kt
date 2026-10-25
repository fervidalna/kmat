package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.ExercisePerformance

/** Uses the diagnosed axis profile and the last practice session to build the next session. */
class AdaptivePracticeAssigner {
    fun chooseNextSession(
        bank: List<Exercise>,
        diagnostic: DiagnosticReport,
        previousPractice: List<ExercisePerformance>,
        count: Int = SESSION_SIZE
    ): List<Exercise> {
        if (bank.isEmpty() || count <= 0) return emptyList()
        val seenRecently = previousPractice.map { it.exerciseId }.toSet()
        val selected = mutableListOf<Exercise>()
        val useCounts = mutableMapOf<String, Int>()

        repeat(count) {
            val candidates = DiagnosticAssessmentEngine.AXES.mapNotNull { axis ->
                val axisBank = bank.filter { it.axis == axis && it.id !in selected.map(Exercise::id) }
                if (axisBank.isEmpty() || (useCounts[axis] ?: 0) >= MAX_PER_AXIS_PER_SESSION) return@mapNotNull null
                val result = diagnostic.axes.firstOrNull { it.axis == axis }
                val recent = previousPractice.filter { it.axis == axis }
                val targetDifficulty = targetDifficulty(result?.level ?: 2, recent)
                val unseen = axisBank.filter { it.id !in seenRecently }
                val pool = if (unseen.isNotEmpty()) unseen else axisBank
                val closest = pool.minOf { kotlin.math.abs(it.difficulty - targetDifficulty) }
                val exercise = pool.filter { kotlin.math.abs(it.difficulty - targetDifficulty) == closest }.random()
                AxisCandidate(axis, exercise, weight(result?.level ?: 2, recent))
            }
            if (candidates.isEmpty()) return@repeat
            val chosen = weightedPick(candidates)
            selected += chosen.exercise
            useCounts[chosen.axis] = (useCounts[chosen.axis] ?: 0) + 1
        }
        return selected
    }

    private fun targetDifficulty(level: Int, recent: List<ExercisePerformance>): Int {
        val baseline = when (level) {
            1 -> 0
            3 -> 2
            else -> 1
        }
        if (recent.isEmpty()) return baseline
        val accuracy = recent.count { it.isCorrect }.toFloat() / recent.size
        val timely = recent.count { it.isCorrect && it.elapsedMillis <= it.expectedTimeMillis }.toFloat() / recent.size
        return when {
            accuracy < 0.5f -> (baseline - 1).coerceAtLeast(0)
            accuracy >= 0.8f && timely >= 0.7f -> (baseline + 1).coerceAtMost(2)
            else -> baseline
        }
    }

    private fun weight(level: Int, recent: List<ExercisePerformance>): Int {
        val initial = when (level) {
            1 -> 5
            3 -> 1
            else -> 3
        }
        if (recent.isEmpty()) return initial
        val accuracy = recent.count { it.isCorrect }.toFloat() / recent.size
        val slow = recent.count { it.elapsedMillis > it.expectedTimeMillis * 1.5f }
        return (initial + (if (accuracy < 0.6f) 2 else 0) + (if (slow.toFloat() / recent.size >= 0.5f) 1 else 0) -
            (if (accuracy >= 0.8f && slow == 0) 1 else 0)).coerceAtLeast(1)
    }

    private fun weightedPick(candidates: List<AxisCandidate>): AxisCandidate {
        val totalWeight = candidates.sumOf { it.weight }
        var selection = (1..totalWeight).random()
        for (candidate in candidates) {
            selection -= candidate.weight
            if (selection <= 0) return candidate
        }
        return candidates.last()
    }

    private data class AxisCandidate(val axis: String, val exercise: Exercise, val weight: Int)

    companion object {
        const val SESSION_SIZE = 5
        private const val MAX_PER_AXIS_PER_SESSION = 2
    }
}
