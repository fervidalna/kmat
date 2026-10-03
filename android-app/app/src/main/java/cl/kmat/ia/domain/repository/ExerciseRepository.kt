package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.Exercise

interface ExerciseRepository {
    suspend fun getNextExercise(): Exercise
}
