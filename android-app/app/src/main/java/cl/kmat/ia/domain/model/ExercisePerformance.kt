package cl.kmat.ia.domain.model

data class ExercisePerformance(
    val exerciseId: String,
    val isCorrect: Boolean,
    val elapsedMillis: Long,
    val axis: String = "NUMEROS_OPERACIONES",
    val expectedTimeMillis: Long = 30_000L,
    val difficulty: Int = 0
)
