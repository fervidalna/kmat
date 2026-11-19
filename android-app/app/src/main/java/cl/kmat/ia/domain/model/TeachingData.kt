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
    val availableExercises: Int,
    val axis: String = "",
    val difficulty: Int = -1
)

data class StudentReport(
    val studentId: String,
    val studentName: String,
    val educationLevel: String,
    val sessions: Int,
    val correct: Int,
    val incorrect: Int,
    val sessionProgress: List<SessionProgressPoint>,
    val history: List<ExerciseHistoryEntry>,
    val performanceLevel: String? = null
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
    val area: String = "",
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

data class ReinforcementSuggestion(
    val topic: String,
    val evidence: String,
    val activity: String,
    val generatedAt: String,
    val practiceCount: Int
)

data class SubjectSuggestionState(
    val axis: String,
    val practiceCount: Int,
    val minimum: Int,
    val newAnswers: Int,
    val canGenerate: Boolean,
    val suggestion: ReinforcementSuggestion?
)

sealed interface SuggestionResult {
    data class Failure(val detail: String) : SuggestionResult
    data class Success(val subjects: List<SubjectSuggestionState>) : SuggestionResult
    data object Unauthorized : SuggestionResult
    data object Busy : SuggestionResult
    data object QuotaExceeded : SuggestionResult
    data object NotConfigured : SuggestionResult
    data object NetworkError : SuggestionResult
}

sealed interface TeachingResult<out T> {
    data class Success<T>(val value: T) : TeachingResult<T>
    data object Unauthorized : TeachingResult<Nothing>
    data object InvalidRequest : TeachingResult<Nothing>
    data object NotConfigured : TeachingResult<Nothing>
    data object NetworkError : TeachingResult<Nothing>
}
