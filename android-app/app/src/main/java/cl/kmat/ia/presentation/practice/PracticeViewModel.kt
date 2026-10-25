package cl.kmat.ia.presentation.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.DiagnosticAnswer
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.StrokePoint
import cl.kmat.ia.domain.repository.HandwritingRecognitionRepository
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import cl.kmat.ia.domain.usecase.SavePracticeAttemptUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PracticeUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val sessionId: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val exerciseIndex: Int = 0,
    val exercise: Exercise? = null,
    val answer: String = "",
    val countAnswerParts: List<String> = listOf("", ""),
    val countStrokesByPart: List<List<HandwritingStroke>> = listOf(emptyList(), emptyList()),
    val activeCountAnswerIndex: Int? = null,
    val completedExercises: List<ExerciseOutcome> = emptyList(),
    val strokes: List<HandwritingStroke> = emptyList(),
    val isPreparingRecognizer: Boolean = false,
    val isRecognizerStatusKnown: Boolean = false,
    val isRecognizerReady: Boolean = false,
    val isRecognizing: Boolean = false,
    val isDrawing: Boolean = false,
    val recognitionMessage: String? = null,
    val attemptStartedAt: Long = System.currentTimeMillis()
)

data class ExerciseOutcome(
    val exerciseName: String,
    val answer: String,
    val expectedAnswer: String,
    val isCorrect: Boolean,
    val elapsedMillis: Long,
    val exerciseId: String = "",
    val axis: String = "NUMEROS_OPERACIONES",
    val difficulty: Int = 0,
    val expectedTimeMillis: Long = 30_000L
)

sealed interface PracticeAnswerResult {
    data object NextExercise : PracticeAnswerResult
    data class SessionComplete(val outcomes: List<ExerciseOutcome>, val diagnosticReport: DiagnosticReport? = null) : PracticeAnswerResult
}

class PracticeViewModel(
    private val learningSessionRepository: LearningSessionRepository,
    private val getDailyOfflineExercise: GetDailyOfflineExerciseUseCase,
    private val savePracticeAttempt: SavePracticeAttemptUseCase,
    private val studentId: String?,
    private val handwritingRecognitionRepository: HandwritingRecognitionRepository,
    private val diagnosticMode: Boolean = false,
    private val schoolLevel: String = "BASICO_1",
    private val diagnosticEngine: DiagnosticAssessmentEngine = DiagnosticAssessmentEngine()
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()
    private var recognitionJob: Job? = null
    private var strokeRevision = 0
    private var writingWidth = 0f
    private var writingHeight = 0f

    init {
        loadOfflineSession()
        prepareRecognizer()
    }

    fun updateWritingArea(width: Float, height: Float) {
        if (writingWidth > 0f && writingHeight > 0f &&
            (writingWidth != width || writingHeight != height)
        ) {
            clearAnswerAndStrokes()
        }
        writingWidth = width
        writingHeight = height
    }

    fun selectCountAnswer(index: Int) {
        val state = _uiState.value
        if (index !in state.countAnswerParts.indices || state.isSaving) return
        if (state.activeCountAnswerIndex == index) return

        val answers = state.countAnswerParts.toMutableList()
        val strokesByPart = state.countStrokesByPart.toMutableList()
        state.activeCountAnswerIndex?.let { previousIndex ->
            if (state.answer.matches(Regex("[0-9]{1,2}"))) {
                answers[previousIndex] = state.answer
            }
            strokesByPart[previousIndex] = state.strokes
        }

        recognitionJob?.cancel()
        strokeRevision++
        _uiState.value = _uiState.value.copy(
            countAnswerParts = answers,
            countStrokesByPart = strokesByPart,
            activeCountAnswerIndex = index,
            answer = answers[index],
            strokes = strokesByPart[index],
            isDrawing = false,
            isRecognizing = false,
            recognitionMessage = null,
            attemptStartedAt = _uiState.value.attemptStartedAt.takeIf { it > 0L }
                ?: if (_uiState.value.isRecognizerReady) System.currentTimeMillis() else 0L
        )
        if (strokesByPart[index].isNotEmpty() && answers[index].isBlank()) endStroke()
    }

    fun confirmCountAnswer() {
        val state = _uiState.value
        val index = state.activeCountAnswerIndex ?: return
        if (state.isSaving || !state.answer.matches(Regex("[0-9]{1,2}"))) {
            _uiState.value = state.copy(recognitionMessage = "Escribe el número y espera a que se detecte para continuar.")
            return
        }
        val parts = state.countAnswerParts.toMutableList()
        parts[index] = state.answer
        val strokesByPart = state.countStrokesByPart.toMutableList()
        strokesByPart[index] = state.strokes
        _uiState.value = state.copy(
            countAnswerParts = parts,
            countStrokesByPart = strokesByPart,
            activeCountAnswerIndex = null,
            answer = "",
            strokes = emptyList(),
            isDrawing = false,
            isRecognizing = false,
            recognitionMessage = null
        )
    }

    fun clearCurrentCountAnswer() {
        val state = _uiState.value
        val index = state.activeCountAnswerIndex
        if (index == null) {
            clearCountAnswers()
            return
        }
        if (state.isSaving) return
        recognitionJob?.cancel()
        strokeRevision++
        val parts = state.countAnswerParts.toMutableList()
        parts[index] = ""
        val strokesByPart = state.countStrokesByPart.toMutableList()
        strokesByPart[index] = emptyList()
        _uiState.value = state.copy(
            countAnswerParts = parts,
            countStrokesByPart = strokesByPart,
            answer = "",
            strokes = emptyList(),
            isDrawing = false,
            isRecognizing = false,
            recognitionMessage = null
        )
    }

    fun clearCountAnswers() {
        if (_uiState.value.isSaving) return
        val count = _uiState.value.countAnswerParts.size
        _uiState.value = _uiState.value.copy(
            countAnswerParts = List(count) { "" },
            countStrokesByPart = List(count) { emptyList() },
            answer = "",
            recognitionMessage = null
        )
    }

    fun clearAnswerAndStrokes() {
        if (_uiState.value.isSaving) return
        recognitionJob?.cancel()
        strokeRevision++
        _uiState.value = _uiState.value.copy(
            answer = "", strokes = emptyList(), isDrawing = false, isRecognizing = false, recognitionMessage = null
        )
    }

    fun startStroke(x: Float, y: Float) {
        if (_uiState.value.isSaving) return
        recognitionJob?.cancel()
        strokeRevision++
        val point = StrokePoint(x, y, System.currentTimeMillis())
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            answer = "", isDrawing = true, isRecognizing = false, recognitionMessage = null,
            strokes = _uiState.value.strokes + HandwritingStroke(listOf(point)),
            attemptStartedAt = _uiState.value.attemptStartedAt.takeIf { it > 0L } ?: now
        )
    }

    fun appendPointToStroke(x: Float, y: Float) {
        if (_uiState.value.isSaving) return
        val strokes = _uiState.value.strokes
        val lastStroke = strokes.lastOrNull() ?: return
        val updatedStroke = lastStroke.copy(points = lastStroke.points + StrokePoint(x, y, System.currentTimeMillis()))
        _uiState.value = _uiState.value.copy(strokes = strokes.dropLast(1) + updatedStroke)
    }

    fun endStroke() {
        _uiState.value = _uiState.value.copy(isDrawing = false)
        if (!_uiState.value.isRecognizerReady || _uiState.value.isSaving) return
        val revision = strokeRevision
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            delay(550)
            if (writingWidth <= 0f || writingHeight <= 0f) return@launch
            val strokes = _uiState.value.strokes
            _uiState.value = _uiState.value.copy(isRecognizing = true)
            try {
                val answer = handwritingRecognitionRepository.recognize(strokes, writingWidth, writingHeight)
                if (revision == strokeRevision) {
                    _uiState.value = _uiState.value.copy(
                        answer = answer.orEmpty(),
                        isRecognizing = false,
                        recognitionMessage = if (answer == null) "No pude leer un número. Bórralo y prueba otra vez." else null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (revision == strokeRevision) {
                    _uiState.value = _uiState.value.copy(
                        isRecognizing = false,
                        recognitionMessage = "No pude reconocerlo. Intenta otra vez."
                    )
                }
            }
        }
    }

    fun prepareRecognizer() {
        if (_uiState.value.isPreparingRecognizer || _uiState.value.isRecognizerReady) return
        startRecognizerPreparation()
    }

    private fun startRecognizerPreparation() {
        _uiState.value = _uiState.value.copy(
            isPreparingRecognizer = false,
            isRecognizerStatusKnown = false,
            recognitionMessage = null
        )
        viewModelScope.launch {
            try {
                if (handwritingRecognitionRepository.isReady()) {
                    _uiState.value = _uiState.value.copy(
                        isRecognizerStatusKnown = true,
                        isRecognizerReady = true,
                        isPreparingRecognizer = false,
                        recognitionMessage = null
                    )
                    if (_uiState.value.exercise != null && _uiState.value.attemptStartedAt == 0L) {
                        _uiState.value = _uiState.value.copy(attemptStartedAt = System.currentTimeMillis())
                    }
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isRecognizerStatusKnown = true,
                    isPreparingRecognizer = true
                )
                handwritingRecognitionRepository.prepare()
                _uiState.value = _uiState.value.copy(
                    isRecognizerStatusKnown = true,
                    isPreparingRecognizer = false,
                    isRecognizerReady = true,
                    recognitionMessage = null
                )
                if (_uiState.value.exercise != null && _uiState.value.attemptStartedAt == 0L) {
                    _uiState.value = _uiState.value.copy(attemptStartedAt = System.currentTimeMillis())
                }
                if (_uiState.value.strokes.isNotEmpty() && !_uiState.value.isDrawing) endStroke()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRecognizerStatusKnown = true,
                    isPreparingRecognizer = false,
                    recognitionMessage = "Se necesita conexión para descargar el reconocimiento de escritura."
                )
            }
        }
    }

    fun submitAnswer(onSaved: (PracticeAnswerResult) -> Unit) {
        val state = _uiState.value
        val exercise = state.exercise ?: return
        val sessionId = state.sessionId ?: return
        val isCountExercise = exercise.id.startsWith(COUNT_PREFIX)
        if (state.isSaving || state.isDrawing) return
        if (isCountExercise && state.activeCountAnswerIndex != null) return
        if (isCountExercise && state.countAnswerParts.any { it.isBlank() }) {
            val blankCount = state.countAnswerParts.size
            _uiState.value = state.copy(
                recognitionMessage = "Completa ${if (blankCount == 1) "el espacio" else "los $blankCount espacios"}."
            )
            return
        }
        if (!isCountExercise && state.strokes.isEmpty()) {
            _uiState.value = state.copy(recognitionMessage = "Escribe un número en el espacio blanco.")
            return
        }
        if (!isCountExercise && !state.isRecognizerReady) {
            _uiState.value = state.copy(recognitionMessage = "Espera a que el reconocimiento esté listo.")
            return
        }
        recognitionJob?.cancel()

        _uiState.value = state.copy(isSaving = true, isRecognizing = true, recognitionMessage = null)
        viewModelScope.launch {
            try {
                val answer = if (isCountExercise) {
                    state.countAnswerParts.joinToString(",")
                } else {
                    handwritingRecognitionRepository.recognize(state.strokes, writingWidth, writingHeight)
                }
                if (answer == null) {
                    _uiState.value = _uiState.value.copy(
                        answer = "", isSaving = false, isRecognizing = false,
                        recognitionMessage = "No pude leer un número. Bórralo y prueba otra vez."
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(answer = answer, isRecognizing = false)
                val elapsedMillis = (System.currentTimeMillis() - state.attemptStartedAt).coerceAtLeast(0)
                val result = savePracticeAttempt(
                    sessionId = sessionId,
                    exercise = exercise,
                    answer = answer,
                    elapsedMillis = elapsedMillis,
                    strokes = if (isCountExercise) state.countStrokesByPart.flatten() else state.strokes
                )
                val outcome = ExerciseOutcome(
                    exerciseName = exercise.content,
                    answer = answer,
                    expectedAnswer = exercise.expectedAnswer,
                    isCorrect = result.isCorrect,
                    elapsedMillis = elapsedMillis,
                    exerciseId = exercise.id,
                    axis = exercise.axis,
                    difficulty = exercise.difficulty,
                    expectedTimeMillis = exercise.expectedTimeMillis
                )
                val outcomes = state.completedExercises + outcome
                val nextIndex = state.exerciseIndex + 1
                val diagnosticBank = if (diagnosticMode) getDailyOfflineExercise.diagnosticBank(studentId.orEmpty()) else emptyList()
                val nextDiagnostic = if (diagnosticMode) diagnosticEngine.nextQuestion(
                    diagnosticBank, schoolLevel, outcomes.map { it.toDiagnosticAnswer() }
                ) else null
                val nextExercise = nextDiagnostic ?: state.exercises.getOrNull(nextIndex)
                if (nextExercise != null) {
                    val nextExercises = if (nextIndex < state.exercises.size) state.exercises else state.exercises + nextExercise
                    val nextExerciseIndex = if (nextIndex < state.exercises.size) nextIndex else nextExercises.lastIndex
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        isRecognizing = false,
                        exercises = nextExercises,
                        completedExercises = outcomes,
                        exerciseIndex = nextExerciseIndex,
                        exercise = nextExercise,
                        answer = "",
                        countAnswerParts = emptyCountAnswers(state.exercises[nextIndex]),
                        countStrokesByPart = emptyCountStrokes(state.exercises[nextIndex]),
                        activeCountAnswerIndex = null,
                        strokes = emptyList(),
                        recognitionMessage = null,
                        attemptStartedAt = System.currentTimeMillis()
                    )
                    onSaved(PracticeAnswerResult.NextExercise)
                } else {
                    learningSessionRepository.completeSession(sessionId)
                    val diagnosticReport = if (diagnosticMode) {
                        diagnosticEngine.buildReport(outcomes.map { it.toDiagnosticAnswer() }).also {
                            learningSessionRepository.saveDiagnosticReport(studentId.orEmpty(), it)
                        }
                    } else null
                    _uiState.value = _uiState.value.copy(isSaving = false, completedExercises = outcomes)
                    onSaved(PracticeAnswerResult.SessionComplete(outcomes, diagnosticReport))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false, isRecognizing = false,
                    recognitionMessage = "No pude comprobar la respuesta. Intenta otra vez."
                )
            }
        }
    }

    private fun loadOfflineSession() {
        viewModelScope.launch {
            if (studentId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = "Primero selecciona un perfil de estudiante desde el acceso adulto."
                )
                return@launch
            }
            try {
                val sessionId = if (diagnosticMode) learningSessionRepository.getDiagnosticSessionId(studentId)
                    else learningSessionRepository.getDailySessionId(studentId)
                val exercises = if (diagnosticMode) {
                    diagnosticEngine.initialQuestions(getDailyOfflineExercise.diagnosticBank(studentId), schoolLevel)
                } else getDailyOfflineExercise(studentId)
                if (exercises.isEmpty()) error("No hay ejercicios disponibles")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = null,
                    sessionId = sessionId,
                    exercises = exercises,
                    exerciseIndex = 0,
                    exercise = exercises.firstOrNull(),
                    countAnswerParts = exercises.firstOrNull()?.let { emptyCountAnswers(it) } ?: listOf("", ""),
                    countStrokesByPart = exercises.firstOrNull()?.let { emptyCountStrokes(it) }
                        ?: listOf(emptyList(), emptyList()),
                    attemptStartedAt = if (_uiState.value.isRecognizerReady) System.currentTimeMillis() else 0L
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("KMatPractice", "No se pudo cargar la sesión o los ejercicios offline", error)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = "No se pudieron cargar los ejercicios. Inténtalo otra vez. Si vuelve a pasar, avísanos."
                )
            }
        }
    }

    fun retryLoadOfflineSession() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
        loadOfflineSession()
    }

    override fun onCleared() {
        recognitionJob?.cancel()
        handwritingRecognitionRepository.close()
        super.onCleared()
    }

    private companion object {
        const val COUNT_PREFIX = "offline-count-"
        fun emptyCountAnswers(exercise: Exercise): List<String> =
            List(if (exercise.id.startsWith(COUNT_PREFIX)) exercise.expectedAnswer.split(',').size else 2) { "" }

        fun emptyCountStrokes(exercise: Exercise): List<List<HandwritingStroke>> =
            List(if (exercise.id.startsWith(COUNT_PREFIX)) exercise.expectedAnswer.split(',').size else 2) { emptyList() }
    }

    private fun ExerciseOutcome.toDiagnosticAnswer() = DiagnosticAnswer(
        exerciseId, axis, difficulty, isCorrect, elapsedMillis, expectedTimeMillis
    )
}
