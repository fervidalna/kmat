package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.HandwritingStroke

data class SavedAttempt(
    val attemptId: String,
    val isCorrect: Boolean
)

interface LearningSessionRepository {
    suspend fun getDailySessionId(studentId: String): String
    suspend fun getOfflineExercise(): Exercise
    suspend fun saveAttempt(
        sessionId: String,
        exercise: Exercise,
        answer: String,
        elapsedMillis: Long,
        strokes: List<HandwritingStroke>
    ): SavedAttempt
}
