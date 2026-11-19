package cl.kmat.ia.presentation.practice

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PracticeEncouragementSelectionTest {
    @Test fun avoidsPreviousAndCanChooseEitherOtherPhrase() {
        for (previous in 0..2) {
            val random = Random(previous)
            val choices = (1..200).map { nextEncouragementIndex(previous, random) }.toSet()
            assertEquals((0..2).filter { it != previous }.toSet(), choices)
        }
    }

    @Test fun sequenceNeverRepeatsAndIncludesAllPhrases() {
        val random = Random(42)
        var previous = -1
        val heard = mutableSetOf<Int>()
        repeat(200) {
            val next = nextEncouragementIndex(previous, random)
            assertNotEquals(previous, next)
            heard.add(next)
            previous = next
        }
        assertEquals(setOf(0, 1, 2), heard)
    }
}
