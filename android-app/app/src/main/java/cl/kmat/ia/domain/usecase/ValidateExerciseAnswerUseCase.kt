package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.Exercise

/** Regla de negocio para evaluar la respuesta de un ejercicio. */
class ValidateExerciseAnswerUseCase {
    operator fun invoke(exercise: Exercise, answer: String): Boolean =
        exercise.expectedAnswer.trim() == answer.trim()
}
