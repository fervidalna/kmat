package cl.kmat.ia.practice

import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.data.recognition.MlKitHandwritingRecognitionRepository
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.StrokePoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandwritingRecognitionTest {
    @Test
    fun recognizesFourWithoutSubstitutingTheExpectedAnswer() = runBlocking {
        val recognizer = MlKitHandwritingRecognitionRepository()
        try {
            withTimeout(120_000) { recognizer.prepare() }
            val strokes = listOf(
                HandwritingStroke(listOf(StrokePoint(160f, 40f, 0), StrokePoint(80f, 180f, 200),
                    StrokePoint(220f, 180f, 400))),
                HandwritingStroke(listOf(StrokePoint(180f, 40f, 500), StrokePoint(180f, 260f, 800)))
            )
            assertEquals("4", withTimeout(15_000) { recognizer.recognize(strokes, 600f, 400f) })
        } finally {
            recognizer.close()
        }
    }

    @Test
    fun recognizesHandwrittenTwoAtDifferentSizes() = runBlocking {
        val recognizer = MlKitHandwritingRecognitionRepository()
        try {
            withTimeout(120_000) { recognizer.prepare() }
            for (scale in listOf(0.5f, 1f, 2f)) {
                val strokes = listOf(twoStroke(scale, 120f, 80f))
                val answer = withTimeout(15_000) { recognizer.recognize(strokes, 1000f, 700f) }
                assertEquals("Handwritten 2 at scale $scale", "2", answer)
            }
        } finally {
            recognizer.close()
        }
    }
}

internal fun twoStroke(scale: Float = 1f, x: Float = 0f, y: Float = 0f): HandwritingStroke {
    // A curved handwritten 2: upper arch, diagonal down and a horizontal baseline.
    val points = listOf(
        10f to 35f, 13f to 24f, 20f to 14f, 30f to 8f, 42f to 5f,
        55f to 5f, 67f to 9f, 77f to 17f, 82f to 28f, 82f to 39f,
        78f to 50f, 70f to 61f, 59f to 72f, 46f to 84f, 32f to 97f,
        20f to 109f, 10f to 120f, 25f to 120f, 40f to 120f,
        55f to 120f, 70f to 120f, 85f to 120f
    )
    return HandwritingStroke(points.mapIndexed { index, point ->
        StrokePoint(x + point.first * scale, y + point.second * scale, index * 20L)
    })
}
