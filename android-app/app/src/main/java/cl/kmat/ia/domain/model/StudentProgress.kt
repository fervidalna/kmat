package cl.kmat.ia.domain.model

data class StudentSubjectProgress(
    val subject: String,
    val correct: Int,
    val incorrect: Int,
    val averageResponseMillis: Long,
    val history: List<SubjectProgressPoint>
)

data class SubjectProgressPoint(
    val dateEpochMillis: Long,
    val correct: Int,
    val incorrect: Int,
    val averageResponseMillis: Long
)
