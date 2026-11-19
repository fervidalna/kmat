package cl.kmat.ia.practice

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.MainActivity
import cl.kmat.ia.presentation.design.KMatTheme
import cl.kmat.ia.presentation.practice.PracticeScreen
import cl.kmat.ia.presentation.practice.AutoFitQuestionText
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun longAndShortQuestionsFitTheirAssignedCards() {
        val shortLayout = AtomicReference<Pair<Float, Boolean>?>(null)
        val longLayout = AtomicReference<Pair<Float, Boolean>?>(null)
        val longQuestion = "A ribbon is 5 cm and another is 8 cm. How many centimeters longer is the second ribbon?"

        compose.activity.setContent {
            KMatTheme {
                Column {
                    AutoFitQuestionText(
                        text = "How many sides?",
                        modifier = Modifier.size(width = 280.dp, height = 170.dp),
                        maximumFontSize = 42.sp,
                        minimumFontSize = 14.sp,
                        onLayout = { fontSize, fits -> shortLayout.set(fontSize.value to fits) }
                    )
                    AutoFitQuestionText(
                        text = longQuestion,
                        modifier = Modifier.size(width = 280.dp, height = 170.dp),
                        maximumFontSize = 42.sp,
                        minimumFontSize = 14.sp,
                        onLayout = { fontSize, fits -> longLayout.set(fontSize.value to fits) }
                    )
                }
            }
        }

        compose.waitUntil(10_000) {
            shortLayout.get()?.second == true && longLayout.get()?.second == true
        }
        assertTrue("Short text should keep a larger font", shortLayout.get()!!.first > longLayout.get()!!.first)
    }

    @Test
    fun writesTwoClearsAndChecksWithoutScrolling() {
        val completed = AtomicBoolean(false)
        val hasIncorrectOutcome = AtomicBoolean(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme {
                    PracticeScreen(
                        onComplete = { outcomes ->
                            hasIncorrectOutcome.set(outcomes.any { !it.isCorrect })
                            completed.set(true)
                        },
                        onActiveBreak = {},
                        onExit = {}
                    )
                }
            }
        }
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText("Escribe un número y pulsa Comprobar.").fetchSemanticsNodes().isNotEmpty()
        }
        saveScreenshot()
        assertControlsVisible()
        val configuration = compose.activity.resources.configuration
        val shortLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.screenHeightDp < 450
        compose.onNodeWithTag("handwriting-pad").assertHeightIsAtLeast(if (shortLandscape) 128.dp else 140.dp)
        compose.onNodeWithTag("check-answer").assertIsNotEnabled()
        writeTwo()
        waitForTwo()
        saveScreenshot()

        compose.onNodeWithTag("clear-answer").performClick()
        compose.onNodeWithTag("check-answer").assertIsNotEnabled()
        compose.onNodeWithText("Escribe aquí").assertIsDisplayed()
        writeTwo()
        waitForTwo()
        assertControlsVisible()
        compose.onNodeWithTag("check-answer").assertIsEnabled().performClick()
        compose.waitUntil(15_000) { completed.get() }
        assertFalse("The handwritten 2 must solve 1 + 1", hasIncorrectOutcome.get())
    }

    private fun assertControlsVisible() {
        compose.onNodeWithTag("clear-answer").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag("check-answer").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag("active-break").assertIsDisplayed()
        compose.onNodeWithTag("exit-practice").assertIsDisplayed()
    }

    private fun writeTwo() {
        compose.onNodeWithTag("handwriting-pad").performTouchInput {
            val scale = minOf(width * 0.55f / 90f, height * 0.65f / 120f)
            val points = twoStroke(scale, width * 0.25f, height * 0.15f).points
            down(Offset(points.first().x, points.first().y))
            points.drop(1).forEach { moveTo(Offset(it.x, it.y), delayMillis = 20L) }
            up()
        }
    }

    private fun waitForTwo() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Número detectado: 2").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun saveScreenshot() {
        val configuration = compose.activity.resources.configuration
        val file = File(compose.activity.getExternalFilesDir(null),
            "practice-${configuration.screenWidthDp}x${configuration.screenHeightDp}.png")
        file.outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
