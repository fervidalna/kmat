package cl.kmat.ia.domain.model

data class DiagnosticAxisResult(
    val axis: String,
    val label: String,
    val correct: Int,
    val total: Int,
    val status: String,
    val level: Int,
    val withinExpectedTime: Int
)

data class DiagnosticReport(
    val overallLevel: Int,
    val totalQuestions: Int,
    val axes: List<DiagnosticAxisResult>,
    val completedAt: Long = System.currentTimeMillis()
)

data class DiagnosticAnswer(
    val exerciseId: String,
    val axis: String,
    val difficulty: Int,
    val isCorrect: Boolean,
    val elapsedMillis: Long,
    val expectedTimeMillis: Long
)
