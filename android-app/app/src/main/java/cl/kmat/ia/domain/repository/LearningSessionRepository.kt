package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.model.DiagnosticReport
import kotlinx.coroutines.flow.Flow

data class SavedAttempt(
    val attemptId: String,
    val isCorrect: Boolean
)

data class SavedExerciseAnswer(
    val exercise: Exercise,
    val answer: String,
    val isCorrect: Boolean,
    val elapsedMillis: Long
)

data class SessionSyncState(val status: String, val answeredCount: Int, val diagnosticCompleted: Boolean)

interface LearningSessionRepository {
    fun observeSessionSyncState(sessionId: String): Flow<SessionSyncState?>
    suspend fun synchronize(studentId: String)
    suspend fun getSessionPlan(sessionId: String): List<Exercise>
    suspend fun saveSessionPlan(sessionId: String, exercises: List<Exercise>)
    suspend fun getSessionAnswers(sessionId: String): List<SavedExerciseAnswer>
    suspend fun getDailySessionId(studentId: String): String
    suspend fun getDiagnosticSessionId(studentId: String): String
    suspend fun getOfflineExercise(): Exercise
    suspend fun getOfflineExercises(studentId: String, diagnosticBank: Boolean = false): List<Exercise>
    suspend fun saveDiagnosticReport(studentId: String, report: DiagnosticReport)
    suspend fun hasCompletedDiagnostic(studentId: String): Boolean
    /** Persist the report and mark the session completed in the same transaction. */
    suspend fun completeDiagnosticSession(sessionId: String, studentId: String, report: DiagnosticReport)
    suspend fun completeSession(sessionId: String)
    suspend fun getPracticeProgress(studentId: String): List<StudentSubjectProgress>
    suspend fun getDiagnosticReport(studentId: String): DiagnosticReport?
    suspend fun saveAttempt(
        sessionId: String,
        exercise: Exercise,
        answer: String,
        elapsedMillis: Long,
        strokes: List<HandwritingStroke>
    ): SavedAttempt
}
