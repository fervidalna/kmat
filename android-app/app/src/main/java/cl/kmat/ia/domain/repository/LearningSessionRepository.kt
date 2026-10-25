package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.model.DiagnosticReport

data class SavedAttempt(
    val attemptId: String,
    val isCorrect: Boolean
)

interface LearningSessionRepository {
    suspend fun getDailySessionId(studentId: String): String
    suspend fun getDiagnosticSessionId(studentId: String): String
    suspend fun getOfflineExercise(): Exercise
    suspend fun getOfflineExercises(studentId: String, diagnosticBank: Boolean = false): List<Exercise>
    suspend fun saveDiagnosticReport(studentId: String, report: DiagnosticReport)
    suspend fun hasCompletedDiagnostic(studentId: String): Boolean
    suspend fun completeSession(sessionId: String)
    suspend fun getPracticeProgress(studentId: String): List<StudentSubjectProgress>
    suspend fun saveAttempt(
        sessionId: String,
        exercise: Exercise,
        answer: String,
        elapsedMillis: Long,
        strokes: List<HandwritingStroke>
    ): SavedAttempt
}
