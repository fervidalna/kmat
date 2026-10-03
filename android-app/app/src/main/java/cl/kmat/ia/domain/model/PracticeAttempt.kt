package cl.kmat.ia.domain.model

data class PracticeAttempt(
    val exerciseId: String,
    val answer: String,
    val isCorrect: Boolean,
    val attemptNumber: Int,
    val elapsedMillis: Long
)
