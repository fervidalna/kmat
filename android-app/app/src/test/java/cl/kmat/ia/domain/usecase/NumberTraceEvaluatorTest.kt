package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberTraceEvaluatorTest {
    @Test
    fun allGuidesKeepTheirProportionsAndAreRecognizedOnWideAndTallCanvases() {
        for (number in 1..10) {
            for ((width, height) in listOf(1600f to 800f, 800f to 1600f)) {
                val points = NumberTraceEvaluator.checkpoints(number)
                val strokes = points.mapIndexed { index, point ->
                    val screen = NumberTraceEvaluator.canvasPoint(point, width, height)
                    HandwritingStroke(listOf(StrokePoint(screen.x, screen.y, index.toLong())))
                }
                assertTrue("number=$number canvas=$width x $height", NumberTraceEvaluator.passes(number, strokes, width, height, 2))
                val a = NumberTraceEvaluator.canvasPoint(NormalizedTracePoint(0f, 0f), width, height)
                val b = NumberTraceEvaluator.canvasPoint(NormalizedTracePoint(1f, 1f), width, height)
                assertEquals(b.x - a.x, b.y - a.y, 0.01f)
            }
        }
    }
    @Test
    fun everyNumberFromOneToTenAcceptsItsCompleteGuideAtEveryDifficulty() {
        for (number in 1..10) {
            val stroke = strokeFromGuide(number)
            for (difficulty in 0..2) {
                assertTrue("number=$number difficulty=$difficulty", NumberTraceEvaluator.passes(number, stroke, 1000f, 1000f, difficulty))
            }
        }
    }

    @Test
    fun marksOffRouteDrawingAsNotCompletedForAllNumbers() {
        val offRoute = listOf(HandwritingStroke(List(30) { index -> StrokePoint(950f, 40f + index, index.toLong()) }))
        for (number in 1..10) {
            assertFalse("number=$number", NumberTraceEvaluator.passes(number, offRoute, 1000f, 1000f, 0))
        }
    }

    @Test
    fun quickSparsePointerEventsStillCoverTheRouteBetweenEvents() {
        val segments = NumberTraceEvaluator.routeSegments(1)
        val denseFirstStroke = segments.first().mapIndexed { index, p ->
            HandwritingStroke(listOf(StrokePoint(p.x * 1000f, p.y * 1000f, index.toLong())))
        }
        val base = segments.last()
        val sparseBaseStroke = HandwritingStroke(listOf(
            StrokePoint(base.first().x * 1000f, base.first().y * 1000f, 100),
            StrokePoint(base.last().x * 1000f, base.last().y * 1000f, 200)
        ))

        assertEquals(1f, NumberTraceEvaluator.evaluate(1, denseFirstStroke + sparseBaseStroke, 1000f, 1000f).coverage)
    }

    @Test
    fun rejectsTracingAllDotsInReverseOrder() {
        val reversed = NumberTraceEvaluator.checkpoints(3).asReversed().mapIndexed { index, p ->
            HandwritingStroke(listOf(StrokePoint(p.x * 1000f, p.y * 1000f, index.toLong())))
        }

        assertFalse(NumberTraceEvaluator.passes(3, reversed, 1000f, 1000f, 0))
    }

    @Test
    fun canResumeAfterSkippingTwoDotsOnceThreeFollowingDotsAreChained() {
        val number = 2
        val guide = NumberTraceEvaluator.checkpoints(number)
        val skipped = 11
        val strokes = guide.mapIndexedNotNull { index, p ->
            if (index == skipped || index == skipped + 1) null
            else HandwritingStroke(listOf(StrokePoint(p.x * 1000f, p.y * 1000f, index.toLong())))
        }

        val result = NumberTraceEvaluator.evaluate(number, strokes, 1000f, 1000f)
        assertEquals(guide.size - 2, result.coveredPoints)
        assertFalse(skipped in result.coveredCheckpointIndexes)
        assertFalse(skipped + 1 in result.coveredCheckpointIndexes)
        assertTrue(skipped in result.skippedCheckpointIndexes)
        assertTrue(skipped + 1 in result.skippedCheckpointIndexes)
        assertTrue(skipped + 2 in result.coveredCheckpointIndexes)
        assertTrue(NumberTraceEvaluator.passes(result, difficulty = 2))
    }

    @Test
    fun doesNotResumeAfterOnlyTwoFollowingDots() {
        val number = 2
        val guide = NumberTraceEvaluator.checkpoints(number)
        val skipped = 11
        val strokes = guide.take(skipped).mapIndexed { index, p ->
            HandwritingStroke(listOf(StrokePoint(p.x * 1000f, p.y * 1000f, index.toLong())))
        } + guide.drop(skipped + 2).take(2).mapIndexed { index, p ->
            HandwritingStroke(listOf(StrokePoint(p.x * 1000f, p.y * 1000f, (skipped + index).toLong())))
        }

        val result = NumberTraceEvaluator.evaluate(number, strokes, 1000f, 1000f)
        assertEquals(skipped, result.coveredPoints)
        assertFalse(NumberTraceEvaluator.passes(result, difficulty = 0))
    }

    @Test
    fun rejectsScreenWideZigzagEvenWhenItCrossesTheGuideDots() {
        val points = buildList {
            (0..18).forEach { row ->
                val y = 50f + row * 50f
                val xs = if (row % 2 == 0) 0..18 else 18 downTo 0
                xs.forEach { column -> add(StrokePoint(50f + column * 50f, y, size.toLong())) }
            }
        }
        val scribble = listOf(HandwritingStroke(points))

        assertFalse(NumberTraceEvaluator.passes(2, scribble, 1000f, 1000f, 0))
        assertTrue(NumberTraceEvaluator.evaluate(2, scribble, 1000f, 1000f).routeAdherence < NumberTraceEvaluator.MIN_ROUTE_ADHERENCE)
    }

    @Test
    fun requiredCoverageRisesWithDifficulty() {
        val guide = NumberTraceEvaluator.checkpoints(3)
        val prefix = guide.take((guide.size * 0.62f).toInt())
        val partial = listOf(HandwritingStroke(prefix.mapIndexed { index, p -> StrokePoint(p.x * 1000f, p.y * 1000f, index.toLong()) }))

        assertTrue(NumberTraceEvaluator.passes(3, partial, 1000f, 1000f, 0))
        assertFalse(NumberTraceEvaluator.passes(3, partial, 1000f, 1000f, 1))
        assertFalse(NumberTraceEvaluator.passes(3, partial, 1000f, 1000f, 2))
    }

    @Test
    fun emptyDrawingHasNoCoverage() {
        assertEquals(0f, NumberTraceEvaluator.evaluate(10, emptyList(), 1000f, 1000f).coverage)
    }

    private fun strokeFromGuide(number: Int): List<HandwritingStroke> = NumberTraceEvaluator.checkpoints(number).mapIndexed { index, p ->
        HandwritingStroke(listOf(StrokePoint(p.x * 1000f, p.y * 1000f, index.toLong())))
    }
}
