package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.repository.LearningSessionRepository

class GetDailyOfflineExerciseUseCase(
    private val repository: LearningSessionRepository
) {
    suspend operator fun invoke(studentId: String): List<Exercise> = repository.getOfflineExercises(studentId)
    suspend fun diagnosticBank(studentId: String): List<Exercise> = repository.getOfflineExercises(studentId, diagnosticBank = true)
}
