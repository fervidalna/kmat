package cl.kmat.ia.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectMasteryEngineTest {
    private val engine = SubjectMasteryEngine()
    private fun answer(correct: Boolean, difficulty: Int = 0, axis: String = "NUMEROS") =
        MasteryAttempt(axis, difficulty, correct)

    @Test fun twoCorrectAnswersDoNotPromote() {
        val result = engine.calculate("NUMEROS", 1, listOf(MasterySession(List(2) { answer(true) })))
        assertEquals(1, result.currentLevel)
        assertEquals(2, result.attemptsAtLevel)
    }

    @Test fun twentyAttemptsAndEightOfTenPromoteFromDiagnosticLevel() {
        val answers = List(10) { answer(false, 1) } + List(8) { answer(true, 1) } + List(2) { answer(false, 1) }
        val result = engine.calculate("NUMEROS", 2, listOf(MasterySession(answers)))
        assertEquals(3, result.currentLevel)
        assertEquals(0, result.attemptsAtLevel)
        assertEquals(8, result.transitions.single().correctInLastTen)
    }

    @Test fun rollingWindowCanPromoteAfterTwenty() {
        val initial = List(13) { answer(false) } + List(7) { answer(true) }
        val result = engine.calculate("NUMEROS", 1, listOf(MasterySession(initial), MasterySession(listOf(answer(true)))))
        assertEquals(2, result.currentLevel)
    }

    @Test fun otherAxesAndAnswersInSameSessionDoNotCountTowardsNewLevel() {
        val session = List(20) { answer(true) } + listOf(answer(true, 1), answer(true, 0, "DATOS"))
        val result = engine.calculate("NUMEROS", 1, listOf(MasterySession(session)))
        assertEquals(2, result.currentLevel)
        assertEquals(0, result.attemptsAtLevel)
    }

    @Test fun highestLevelDoesNotPromoteFurther() {
        val result = engine.calculate("NUMEROS", 3, listOf(MasterySession(List(25) { answer(true, 2) })))
        assertEquals(3, result.currentLevel)
        assertEquals(25, result.attemptsAtLevel)
    }
}
