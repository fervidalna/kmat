package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.ExercisePerformance

/** Selects a five-question session, adapting the balance and difficulty by skill. */
class AdaptiveLearningEngine {
    fun chooseNextExerciseIds(previousSession: List<ExercisePerformance>): List<String> {
        val counting = previousSession.filter { it.exerciseId.startsWith(COUNT_PREFIX) }
        val additions = previousSession.filter { it.exerciseId.startsWith(SUM_PREFIX) }
        val countingScore = mastery(counting, COUNT_EXPECTED_TIME_MILLIS)
        val additionScore = mastery(additions, SUM_EXPECTED_TIME_MILLIS)
        val countingDifficulty = targetDifficulty(counting, COUNT_EXPECTED_TIME_MILLIS)
        val additionDifficulty = targetDifficulty(additions, SUM_EXPECTED_TIME_MILLIS)

        val countGetsThree = countingScore <= additionScore
        val categories = if (countGetsThree) {
            listOf(true, false, true, false, true)
        } else {
            listOf(false, true, false, true, false)
        }
        val countingCount = if (countGetsThree) COUNT_PER_SESSION else SUM_PER_SESSION
        val additionCount = SESSION_EXERCISE_COUNT - countingCount
        val selectedCounting = chooseAtLevels(
            countingDifficulty, COUNT_EXERCISES_BY_LEVEL, countingCount, counting.map { it.exerciseId }.toSet()
        )
        val selectedAdditions = chooseAtLevels(
            additionDifficulty, SUM_EXERCISES_BY_LEVEL, additionCount, additions.map { it.exerciseId }.toSet()
        )
        var countIndex = 0
        var sumIndex = 0
        return categories.map { isCounting ->
            if (isCounting) selectedCounting[countIndex++] else selectedAdditions[sumIndex++]
        }.also { check(it.size == SESSION_EXERCISE_COUNT) }
    }

    private fun mastery(attempts: List<ExercisePerformance>, expectedTimeMillis: Long): Double {
        if (attempts.isEmpty()) return 0.5
        val accuracy = attempts.count { it.isCorrect }.toDouble() / attempts.size
        val timelyRate = attempts.count { it.isCorrect && it.elapsedMillis <= expectedTimeMillis }.toDouble() /
            attempts.size
        return accuracy * 0.8 + timelyRate * 0.2
    }

    private fun targetDifficulty(attempts: List<ExercisePerformance>, expectedTimeMillis: Long): Int {
        if (attempts.isEmpty()) return 0
        val accuracy = attempts.count { it.isCorrect }.toDouble() / attempts.size
        val timelyRate = attempts.count { it.isCorrect && it.elapsedMillis <= expectedTimeMillis }.toDouble() /
            attempts.size
        return when {
            accuracy < 0.6 -> 0
            accuracy < 0.85 || timelyRate < 0.5 -> 1
            else -> 2
        }
    }

    private fun chooseAtLevels(target: Int, pools: List<List<String>>, count: Int, previouslySeen: Set<String>): List<String> {
        val offsets = if (count == COUNT_PER_SESSION) listOf(-1, 0, 1) else listOf(-1, 1)
        val chosen = mutableSetOf<String>()
        return offsets.map { offset ->
            val level = (target + offset).coerceIn(0, pools.lastIndex)
            val choices = pools[level].filterNot { it in previouslySeen || it in chosen }
            (choices.ifEmpty { pools[level].filterNot { it in chosen }.ifEmpty { pools[level] } }).shuffled().first()
                .also(chosen::add)
        }
    }

    companion object {
        const val COUNT_PREFIX = "offline-count-"
        const val SUM_PREFIX = "offline-sum-"
        const val SESSION_EXERCISE_COUNT = 5
        private const val COUNT_PER_SESSION = 3
        private const val SUM_PER_SESSION = 2
        private const val COUNT_EXPECTED_TIME_MILLIS = 60_000L
        private const val SUM_EXPECTED_TIME_MILLIS = 30_000L

        val COUNT_EXERCISES_BY_LEVEL = listOf(
            listOf("offline-count-1-10", "offline-count-11-20", "offline-count-20-30", "offline-count-1-6", "offline-count-8-11", "offline-count-stars-5"),
            listOf("offline-count-28-32", "offline-count-46-49", "offline-count-55-70", "offline-count-by-2-20", "offline-count-back-15", "offline-count-by-5-35"),
            listOf("offline-count-77-82", "offline-count-91-96", "offline-count-96-100", "offline-count-by-10-30", "offline-count-forward-18", "offline-count-by-2-86")
        )
        val SUM_EXERCISES_BY_LEVEL = listOf(
            listOf("offline-sum-1-1", "offline-sum-2-3", "offline-sum-4-1"),
            listOf("offline-sum-6-2", "offline-sum-5-4", "offline-sum-7-1"),
            listOf("offline-sum-8-7", "offline-sum-9-6", "offline-sum-9-9")
        )
    }
}
