package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.repository.LearningSessionRepository

class GetDailyOfflineExerciseUseCase(
    private val repository: LearningSessionRepository
) {
    suspend operator fun invoke(): Exercise = repository.getOfflineExercise()
}
