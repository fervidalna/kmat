package cl.kmat.ia.practice

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.MainActivity
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.presentation.design.KMatTheme
import cl.kmat.ia.presentation.practice.PracticeContent
import cl.kmat.ia.presentation.practice.PracticeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeExplanationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun enlargedMascotLeavesTheWritingAreaAndControlsVisible() {
        val landscape = mutableStateOf(false)
        val exercise = Exercise("offline-sum-1-1", "Sumas", "1 + 1 = ?", "2", 0)
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme {
                    PracticeContent(
                        state = PracticeUiState(isLoading = false, isRecognizerStatusKnown = true,
                            isRecognizerReady = true, exercise = exercise, exercises = listOf(exercise)),
                        modifier = Modifier.size(if (landscape.value) 640.dp else 360.dp, if (landscape.value) 360.dp else 640.dp),
                        onCountSlotSelect = {}, onCountSlotDone = {}, onStrokeStart = { _, _ -> },
                        onStrokePoint = { _, _ -> }, onStrokeEnd = {}, onWritingAreaChanged = { _, _ -> },
                        onClear = {}, onPrepare = {}, onRetryLoad = {}, onSubmit = {}, onActiveBreak = {}, onExit = {}
                    )
                }
            }
        }
        for (isLandscape in listOf(false, true)) {
            compose.runOnUiThread { landscape.value = isLandscape }
            compose.onNodeWithTag("practice-puppy").assertIsDisplayed().assertHeightIsAtLeast(168.dp)
            compose.onNodeWithTag("handwriting-pad").assertIsDisplayed().assertHeightIsAtLeast(100.dp)
            compose.onNodeWithTag("clear-answer").assertIsDisplayed()
            compose.onNodeWithTag("check-answer").assertIsDisplayed()
            compose.onNodeWithTag("exercise-explanation").assertIsDisplayed()
        }
    }

    @Test fun examplesSupportSumsWithoutRevealingTheirAnswer() {
        val exercise = Exercise("offline-sum-1-1", "Sumas", "1 + 1 = ?", "2", 0)
        val currentExercise = mutableStateOf(exercise)
        val diagnostic = mutableStateOf(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme {
                    PracticeContent(
                        state = PracticeUiState(isLoading = false, isRecognizerStatusKnown = true,
                            isRecognizerReady = true, exercise = currentExercise.value, exercises = listOf(currentExercise.value)),
                        diagnosticMode = diagnostic.value,
                        onCountSlotSelect = {}, onCountSlotDone = {}, onStrokeStart = { _, _ -> },
                        onStrokePoint = { _, _ -> }, onStrokeEnd = {}, onWritingAreaChanged = { _, _ -> },
                        onClear = {}, onPrepare = {}, onRetryLoad = {}, onSubmit = {}, onActiveBreak = {}, onExit = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("exercise-explanation").assertIsDisplayed().performClick()
        compose.onNodeWithText("Aprendamos a sumar").assertIsDisplayed()
        compose.waitUntil(45_000) { compose.onAllNodesWithText("2 + 3 = 5").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 + 3 = 5").assertExists()
        compose.onNodeWithTag("explanation-star-4").assertExists()
        compose.onNodeWithText("Volver al ejercicio").performClick()
        compose.onNodeWithTag("addition-explanation").assertDoesNotExist()
        compose.runOnUiThread { currentExercise.value = exercise.copy(id = "offline-sum-2-3", statement = "2 + 3 = ?", expectedAnswer = "5") }
        compose.onNodeWithTag("exercise-explanation").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithText("1 + 2 = 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 + 2 = 3").assertExists()
        compose.onNodeWithTag("explanation-star-2").assertExists()
        compose.onNodeWithTag("explanation-star-3").assertDoesNotExist()
        compose.onNodeWithText("Volver al ejercicio").performClick()
        compose.runOnUiThread { currentExercise.value = exercise.copy(id = "offline-count-1-10") }
        compose.onNodeWithTag("exercise-explanation").assertIsDisplayed()
        compose.runOnUiThread { currentExercise.value = exercise; diagnostic.value = true }
        compose.onNodeWithTag("exercise-explanation").assertDoesNotExist()
    }

    @Test fun countingExampleAvoidsTheNumbersMissingFromTheExercise() {
        val exercise = Exercise("offline-count-1-10", "Conteo", "Completa: 1, 2, __, 4, 5, 6, __", "3,7", 0, contentCode = "CONTEO")
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme {
                    PracticeContent(
                        state = PracticeUiState(isLoading = false, isRecognizerStatusKnown = true,
                            isRecognizerReady = true, exercise = exercise, exercises = listOf(exercise)),
                        onCountSlotSelect = {}, onCountSlotDone = {}, onStrokeStart = { _, _ -> },
                        onStrokePoint = { _, _ -> }, onStrokeEnd = {}, onWritingAreaChanged = { _, _ -> },
                        onClear = {}, onPrepare = {}, onRetryLoad = {}, onSubmit = {}, onActiveBreak = {}, onExit = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("exercise-explanation").assertIsDisplayed().performClick()
        compose.onNodeWithText("Aprendamos a completar una serie").assertIsDisplayed()
        compose.onNodeWithTag("counting-example-blank").assertIsDisplayed()
        compose.waitUntil(40_000) { compose.onAllNodesWithText("El número que falta es 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("El número que falta es 4").assertExists()
        compose.onNodeWithText("Volver al ejercicio").performClick()
        compose.onNodeWithTag("counting-sequence-explanation").assertDoesNotExist()
    }

    @Test fun eachSubjectOpensItsAnimatedExampleAndReturnsToPractice() {
        val current = mutableStateOf(Exercise("subject-0", "Ejercicio", "Resuelve", "4", 0, axis = "PATRONES_ALGEBRA"))
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme {
                    PracticeContent(
                        state = PracticeUiState(isLoading = false, isRecognizerStatusKnown = true,
                            isRecognizerReady = true, exercise = current.value, exercises = listOf(current.value)),
                        onCountSlotSelect = {}, onCountSlotDone = {}, onStrokeStart = { _, _ -> },
                        onStrokePoint = { _, _ -> }, onStrokeEnd = {}, onWritingAreaChanged = { _, _ -> },
                        onClear = {}, onPrepare = {}, onRetryLoad = {}, onSubmit = {}, onActiveBreak = {}, onExit = {}
                    )
                }
            }
        }
        val cases = listOf(
            Triple("PATRONES_ALGEBRA", "4", "Sigue el 5"),
            Triple("GEOMETRIA", "4", "3 lados"),
            Triple("MEDICION", "2", "3 bloques más"),
            Triple("DATOS_PROBABILIDAD", "3", "4 votos")
        )
        cases.forEachIndexed { index, (axis, answer, result) ->
            compose.runOnUiThread { current.value = current.value.copy(id = "subject-$index", axis = axis, expectedAnswer = answer) }
            compose.onNodeWithTag("exercise-explanation").assertIsDisplayed().performClick()
            compose.onNodeWithTag("subject-explanation").assertIsDisplayed()
            compose.waitUntil(40_000) { compose.onAllNodesWithText(result).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(result).assertExists()
            compose.onNodeWithText("Volver al ejercicio").performClick()
            compose.onNodeWithTag("subject-explanation").assertDoesNotExist()
        }
    }
}
