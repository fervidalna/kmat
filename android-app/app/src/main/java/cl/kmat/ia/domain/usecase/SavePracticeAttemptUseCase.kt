package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.repository.SavedAttempt

class SavePracticeAttemptUseCase(
    private val repository: LearningSessionRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        exercise: Exercise,
        answer: String,
        elapsedMillis: Long,
        strokes: List<HandwritingStroke>
    ): SavedAttempt = repository.saveAttempt(sessionId, exercise, answer, elapsedMillis, strokes)
}
