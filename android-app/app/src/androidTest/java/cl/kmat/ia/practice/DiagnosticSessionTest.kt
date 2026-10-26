package cl.kmat.ia.practice

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.data.local.OfflineDatabase
import cl.kmat.ia.data.repository.RoomLearningSessionRepository
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.repository.HandwritingRecognitionRepository
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.SavePracticeAttemptUseCase
import cl.kmat.ia.presentation.practice.PracticeAnswerResult
import cl.kmat.ia.presentation.practice.PracticeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the actual ViewModel, question bank and Room writes without depending on ML Kit downloads. */
@RunWith(AndroidJUnit4::class)
class DiagnosticSessionTest {
    @Test
    fun firstGradeAdvancesFromTenToAdaptiveQuestionsAndFinishes() = runBlocking {
        runDiagnostic("BASICO_1", mixedAnswers = true)
    }

    @Test
    fun secondGradeAdvancesFromTenToAdaptiveQuestionsAndFinishes() = runBlocking {
        runDiagnostic("BASICO_2", mixedAnswers = true)
    }

    @Test
    fun consistentDifficultyCanFinishAtTenWithoutBlocking() = runBlocking {
        runDiagnostic("BASICO_1", mixedAnswers = false)
    }

    @Test
    fun allCorrectAnswersReachAdvancedQuestionsAndFinish() = runBlocking {
        runDiagnostic("BASICO_1", mixedAnswers = false, allCorrect = true)
    }

    @Test
    fun reportSaveFailureRetriesWithoutDuplicatingTheLastAnswer() = runBlocking {
        runDiagnostic("BASICO_1", mixedAnswers = false, failReportOnce = true)
    }

    private suspend fun runDiagnostic(
        schoolLevel: String,
        mixedAnswers: Boolean,
        allCorrect: Boolean = false,
        failReportOnce: Boolean = false
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "diagnostic-regression-${UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(context, OfflineDatabase::class.java, databaseName).build()
        val roomRepository = RoomLearningSessionRepository(database, context, downloadRemoteProgress = { null })
        var bankReads = 0
        var reportFailed = false
        val repository = object : LearningSessionRepository by roomRepository {
            override suspend fun getOfflineExercises(studentId: String, diagnosticBank: Boolean): List<Exercise> {
                bankReads++
                check(bankReads == 1) { "Diagnostic must reuse the bank loaded at session start" }
                return roomRepository.getOfflineExercises(studentId, diagnosticBank)
            }

            override suspend fun completeDiagnosticSession(sessionId: String, studentId: String, report: DiagnosticReport) {
                if (failReportOnce && !reportFailed) {
                    reportFailed = true
                    error("Simulated report write failure")
                }
                roomRepository.completeDiagnosticSession(sessionId, studentId, report)
            }
        }
        val recognizer = NumberRecognizer()
        val store = ViewModelStore()
        val studentId = "diagnostic-regression-$schoolLevel"
        try {
            val vm = withContext(Dispatchers.Main) {
                PracticeViewModel(repository, GetDailyOfflineExerciseUseCase(repository),
                    SavePracticeAttemptUseCase(repository), studentId, recognizer,
                    diagnosticMode = true, schoolLevel = schoolLevel).also {
                    store.put("diagnostic", it)
                    it.updateWritingArea(600f, 400f)
                }
            }
            withTimeout(15_000) { vm.uiState.first { !it.isLoading && it.isRecognizerReady } }
            assertNull(vm.uiState.value.loadError)
            assertEquals(10, vm.uiState.value.exercises.size)
            val seen = mutableSetOf<String>()
            val answeredByAxis = mutableMapOf<String, Int>()
            var completed: PracticeAnswerResult.SessionComplete? = null
            for (number in 1..18) {
                val exercise = requireNotNull(vm.uiState.value.exercise)
                assertTrue("Exercise must not repeat: ${exercise.id}", seen.add(exercise.id))
                val axisCount = answeredByAxis[exercise.axis] ?: 0
                answeredByAxis[exercise.axis] = axisCount + 1
                val correct = allCorrect || (mixedAnswers && axisCount % 2 == 0)
                val parts = exercise.expectedAnswer.split(',').map { if (correct) it else if (it == "0") "1" else "0" }
                if (exercise.id.startsWith("offline-count-")) {
                    parts.forEachIndexed { index, answer ->
                        withContext(Dispatchers.Main) {
                            vm.selectCountAnswer(index)
                            recognizer.answer = answer
                            writeNumber(vm)
                        }
                        withTimeout(5_000) { vm.uiState.first { it.answer == answer && !it.isRecognizing } }
                        withContext(Dispatchers.Main) { vm.confirmCountAnswer() }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        recognizer.answer = parts.single()
                        writeNumber(vm)
                    }
                    withTimeout(5_000) { vm.uiState.first { it.answer == parts.single() && !it.isRecognizing } }
                }
                val result = CompletableDeferred<PracticeAnswerResult>()
                withContext(Dispatchers.Main) { vm.submitAnswer { result.complete(it) } }
                withTimeout(15_000) { vm.uiState.first { !it.isSaving } }
                if (failReportOnce && number == 10) {
                    assertTrue(reportFailed)
                    assertFalse(result.isCompleted)
                    assertTrue(vm.uiState.value.answerSaved)
                    assertEquals(10, database.learningDao().getPracticeProgress(studentId).size)
                    // Changing/clearing an already committed answer must not invalidate the retry.
                    withContext(Dispatchers.Main) {
                        vm.clearAnswerAndStrokes()
                        vm.clearCountAnswers()
                        vm.startStroke(1f, 1f)
                        vm.submitAnswer { result.complete(it) }
                    }
                }
                val transition = try {
                    withTimeout(15_000) { result.await() }
                } catch (error: Exception) {
                    throw AssertionError("Blocked at question $number (${exercise.id}): ${vm.uiState.value.recognitionMessage}", error)
                }
                assertEquals(number, vm.uiState.value.completedExercises.size)
                assertFalse(vm.uiState.value.isSaving)
                if (transition is PracticeAnswerResult.SessionComplete) {
                    completed = transition
                    break
                }
                assertEquals(number, vm.uiState.value.exerciseIndex)
                assertEquals(vm.uiState.value.exercise, vm.uiState.value.exercises[number])
                assertNull(vm.uiState.value.recognitionMessage)
            }
            val result = requireNotNull(completed) { "Diagnostic must finish by question 18" }
            if (mixedAnswers || allCorrect) assertTrue("Must go past the ten initial questions", result.outcomes.size > 10)
            else assertEquals(10, result.outcomes.size)
            assertEquals(result.outcomes.size, result.diagnosticReport?.totalQuestions)
            assertEquals(5, result.diagnosticReport?.axes?.size)
            assertTrue(repository.hasCompletedDiagnostic(studentId))
            val attempts = database.learningDao().getPracticeProgress(studentId)
            assertEquals("Each answer must be saved exactly once", result.outcomes.size, attempts.size)
            assertTrue(attempts.all { it.sessionStatus == "COMPLETED" })
            assertEquals(1, bankReads)
            // Repeated completion must not add another attempt or outcome.
            withContext(Dispatchers.Main) {
                vm.submitAnswer { assertEquals(result, it) }
            }
            assertEquals(result.outcomes.size, database.learningDao().getPracticeProgress(studentId).size)
            val savedReport = database.learningDao().getDiagnosticResult(studentId)
            withContext(Dispatchers.Main) { store.clear() }
            database.close()
            // Simulate opening the app again with a fresh repository and ViewModel.
            database = Room.databaseBuilder(context, OfflineDatabase::class.java, databaseName).build()
            val reopenedRepository = RoomLearningSessionRepository(database, context, downloadRemoteProgress = { null })
            assertTrue(reopenedRepository.hasCompletedDiagnostic(studentId))
            assertFalse(reopenedRepository.hasCompletedDiagnostic("another-student"))
            val nextVm = withContext(Dispatchers.Main) {
                PracticeViewModel(reopenedRepository, GetDailyOfflineExerciseUseCase(reopenedRepository),
                    SavePracticeAttemptUseCase(reopenedRepository), studentId, NumberRecognizer(),
                    diagnosticMode = true, schoolLevel = schoolLevel).also { store.put("next", it) }
            }
            withTimeout(15_000) { nextVm.uiState.first { !it.isLoading } }
            assertNull(nextVm.uiState.value.loadError)
            assertFalse("A stale request must not repeat the diagnostic", nextVm.uiState.value.isDiagnosticSession)
            assertEquals("PRACTICA", database.learningDao().getSession(requireNotNull(nextVm.uiState.value.sessionId))?.sessionType)
            assertTrue(nextVm.uiState.value.exercises.isNotEmpty())
            assertEquals("The first diagnostic must remain unchanged", savedReport, database.learningDao().getDiagnosticResult(studentId))
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun writeNumber(vm: PracticeViewModel) {
        vm.startStroke(20f, 20f)
        vm.appendPointToStroke(80f, 100f)
        vm.endStroke()
    }

    private class NumberRecognizer : HandwritingRecognitionRepository {
        var answer = "0"
            set(value) { field = value; available = true }
        private var available = false
        override suspend fun isReady() = true
        override suspend fun prepare() = Unit
        override suspend fun recognize(strokes: List<HandwritingStroke>, width: Float, height: Float): String {
            check(available) { "An already recognized answer must not be recognized a second time" }
            available = false
            return answer
        }
        override fun close() = Unit
    }
}
