package cl.kmat.ia.domain.model

data class TeachingGroup(
    val id: String,
    val name: String,
    val joinCode: String,
    val studentCount: Int
)

data class TeachingStudent(
    val id: String,
    val name: String,
    val educationLevel: String
)

data class ReinforcementOption(
    val contentId: String,
    val name: String,
    val availableExercises: Int
)

data class StudentReport(
    val studentId: String,
    val studentName: String,
    val educationLevel: String,
    val sessions: Int,
    val correct: Int,
    val incorrect: Int,
    val sessionProgress: List<SessionProgressPoint>,
    val history: List<ExerciseHistoryEntry>
)

data class SessionProgressPoint(
    val date: String,
    val correct: Int,
    val total: Int
) {
    val percentage: Float get() = if (total == 0) 0f else correct * 100f / total
}

data class ExerciseHistoryEntry(
    val activity: String,
    val type: String,
    val correct: Boolean?,
    val answer: String,
    val durationMillis: Long,
    val date: String
)

data class GroupStudentReport(
    val studentId: String,
    val name: String,
    val educationLevel: String,
    val sessions: Int,
    val correct: Int,
    val incorrect: Int,
    val lastActivity: String?
)

sealed interface TeachingResult<out T> {
    data class Success<T>(val value: T) : TeachingResult<T>
    data object Unauthorized : TeachingResult<Nothing>
    data object InvalidRequest : TeachingResult<Nothing>
    data object NotConfigured : TeachingResult<Nothing>
    data object NetworkError : TeachingResult<Nothing>
}
