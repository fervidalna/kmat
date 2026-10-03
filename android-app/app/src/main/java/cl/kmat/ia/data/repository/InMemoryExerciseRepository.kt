package cl.kmat.ia.data.repository

import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.repository.ExerciseRepository

class InMemoryExerciseRepository : ExerciseRepository {
    private val sampleExercise = Exercise(
        id = "demo-sum-01",
        content = "Suma",
        statement = "1 + 1 = ?",
        expectedAnswer = "2",
        difficulty = 1
    )

    override suspend fun getNextExercise(): Exercise = sampleExercise
}
