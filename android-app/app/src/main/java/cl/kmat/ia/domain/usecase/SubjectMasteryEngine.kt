package cl.kmat.ia.domain.usecase

/** A completed practice session, in the order its answers were recorded. */
data class MasterySession(val attempts: List<MasteryAttempt>)

data class MasteryAttempt(val axis: String, val difficulty: Int, val isCorrect: Boolean)

data class SubjectMastery(
    val diagnosticLevel: Int,
    val currentLevel: Int,
    val attemptsAtLevel: Int,
    val recentCorrect: Int,
    val recentCount: Int,
    val transitions: List<MasteryTransition>
)

data class MasteryTransition(val fromLevel: Int, val toLevel: Int, val correctInLastTen: Int)

/** Replays completed sessions in chronological order; a promotion takes effect between sessions. */
class SubjectMasteryEngine {
    fun calculate(axis: String, diagnosticLevel: Int, sessions: List<MasterySession>): SubjectMastery {
        val startingLevel = diagnosticLevel.coerceIn(1, 3)
        var level = startingLevel
        var results = mutableListOf<Boolean>()
        val transitions = mutableListOf<MasteryTransition>()
        for (session in sessions) {
            var promotionEarned = false
            for (attempt in session.attempts) {
                if (attempt.axis != axis || attempt.difficulty != level - 1 || promotionEarned) continue
                results.add(attempt.isCorrect)
                if (level < 3 && results.size >= MIN_ATTEMPTS && results.takeLast(WINDOW).count { it } >= MIN_CORRECT) {
                    promotionEarned = true
                }
            }
            if (promotionEarned) {
                transitions.add(MasteryTransition(level, level + 1, results.takeLast(WINDOW).count { it }))
                level++
                results = mutableListOf()
            }
        }
        val recent = results.takeLast(WINDOW)
        return SubjectMastery(startingLevel, level, results.size, recent.count { it }, recent.size, transitions)
    }

    companion object {
        const val MIN_ATTEMPTS = 20
        const val WINDOW = 10
        const val MIN_CORRECT = 8
    }
}
