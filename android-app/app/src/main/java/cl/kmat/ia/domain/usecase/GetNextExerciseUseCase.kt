package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.repository.ExerciseRepository

class GetNextExerciseUseCase(
    private val repository: ExerciseRepository
) {
    suspend operator fun invoke(): Exercise = repository.getNextExercise()
}
