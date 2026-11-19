package cl.kmat.ia.presentation.practice

import cl.kmat.ia.domain.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CountingSequenceExampleTest {
    @Test fun choosesAMatchingIntervalAndDifferentMissingNumber() {
        val cases = listOf(
            Triple("offline-count-1-10", "Completa: 1, 2, __, 4", "3,7") to 1,
            Triple("offline-count-1-6", "Completa: 1, 2, 3, __, __, 6", "4,5") to 1,
            Triple("offline-count-by-2-20", "Completa: 20, 22, 24, __, __", "26,28") to 2,
            Triple("offline-count-55-70", "Cuenta de 5 en 5: 55, 60, __, 70", "65") to 5,
            Triple("offline-count-by-10-30", "Completa: 30, 40, __, 60, __, 80", "50,70") to 10,
            Triple("offline-count-back-15", "Completa hacia atrás: 15, 14, __, __, 11", "13,12") to -1
        )
        cases.forEach { (input, step) ->
            val (id, statement, answers) = input
            val example = countingSequenceExample(Exercise(id, "Conteo", statement, answers, 0))
            assertEquals(step, example.step)
            assertEquals(listOf(step, step, step), example.numbers.zipWithNext { a, b -> b - a })
            assertFalse(answers.split(",").map { it.toInt() }.contains(example.missing))
        }
    }
}
