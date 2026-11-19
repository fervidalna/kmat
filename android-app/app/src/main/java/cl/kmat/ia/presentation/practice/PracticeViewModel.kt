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
import cl.kmat.ia.domain.repository.SavedExerciseAnswer
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import cl.kmat.ia.domain.usecase.NumberTraceEvaluator
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
    val isDiagnosticSession: Boolean = false,
    val isSaving: Boolean = false,
    val answerSaved: Boolean = false,
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
    val traceCoverage: Float = 0f,
    val traceAdherence: Float = 0f,
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
    private var diagnosticMode: Boolean = false,
    private val schoolLevel: String = "BASICO_1",
    private val diagnosticEngine: DiagnosticAssessmentEngine = DiagnosticAssessmentEngine()
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()
    private var recognitionJob: Job? = null
    private var syncObservation: Job? = null
    private var strokeRevision = 0
    private var writingWidth = 0f
    private var writingHeight = 0f
    private var diagnosticBank: List<Exercise> = emptyList()
    private var pendingOutcome: ExerciseOutcome? = null
    private var completedResult: PracticeAnswerResult.SessionComplete? = null

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
        if (index !in state.countAnswerParts.indices || state.isSaving || state.answerSaved) return
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
        if (state.isSaving || state.answerSaved) return
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
        if (_uiState.value.isSaving || _uiState.value.answerSaved) return
        val count = _uiState.value.countAnswerParts.size
        _uiState.value = _uiState.value.copy(
            countAnswerParts = List(count) { "" },
            countStrokesByPart = List(count) { emptyList() },
            answer = "",
            recognitionMessage = null
        )
    }

    fun clearAnswerAndStrokes() {
        if (_uiState.value.isSaving || _uiState.value.answerSaved) return
        recognitionJob?.cancel()
        strokeRevision++
        _uiState.value = _uiState.value.copy(
            answer = "", strokes = emptyList(), isDrawing = false, isRecognizing = false, recognitionMessage = null
        )
    }

    fun startStroke(x: Float, y: Float) {
        if (_uiState.value.isSaving || _uiState.value.answerSaved) return
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
        if (_uiState.value.isSaving || _uiState.value.answerSaved) return
        val strokes = _uiState.value.strokes
        val lastStroke = strokes.lastOrNull() ?: return
        val updatedStroke = lastStroke.copy(points = lastStroke.points + StrokePoint(x, y, System.currentTimeMillis()))
        _uiState.value = _uiState.value.copy(strokes = strokes.dropLast(1) + updatedStroke)
    }

    fun endStroke() {
        val state = _uiState.value
        _uiState.value = state.copy(isDrawing = false)
        if (state.isSaving || state.answerSaved) return
        val exercise = state.exercise
        if (exercise?.contentCode == "TRAZADO_NUMEROS") {
            val number = exercise.expectedAnswer.toIntOrNull() ?: return
            val result = NumberTraceEvaluator.evaluate(number, state.strokes, writingWidth, writingHeight)
            val passed = NumberTraceEvaluator.passes(result, exercise.difficulty)
            _uiState.value = _uiState.value.copy(
                answer = if (passed) exercise.expectedAnswer else "",
                traceCoverage = result.coverage,
                traceAdherence = result.routeAdherence,
                recognitionMessage = when {
                    passed && result.skippedCheckpointIndexes.isNotEmpty() ->
                        "¡Muy bien! Retomaste el trazo. Los puntos naranjas no sumaron. Ya puedes comprobar."
                    passed -> "¡Muy bien! Seguiste los puntos en orden. Ya puedes comprobar."
                    result.skippedCheckpointIndexes.isNotEmpty() ->
                        "Retomaste el trazo. Los puntos naranjas no suman; sigue hasta completar el porcentaje."
                    else -> null
                },
                isRecognizing = false
            )
            return
        }
        if (!state.isRecognizerReady) return
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
        completedResult?.let { onSaved(it); return }
        val state = _uiState.value
        val exercise = state.exercise ?: return
        val sessionId = state.sessionId ?: return
        val isCountExercise = exercise.id.startsWith(COUNT_PREFIX)
        val isTraceExercise = exercise.contentCode == "TRAZADO_NUMEROS"
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
        if (isTraceExercise && state.answer.isBlank()) {
            _uiState.value = state.copy(
                recognitionMessage = if (state.traceAdherence < NumberTraceEvaluator.MIN_ROUTE_ADHERENCE) {
                    "Mantén el trazo sobre la guía y sigue los puntos en orden."
                } else {
                    "Sigue los puntos en orden y cubre al menos ${(NumberTraceEvaluator.passThreshold(exercise.difficulty) * 100).toInt()}% del recorrido."
                }
            )
            return
        }
        if (!isCountExercise && !isTraceExercise && !state.isRecognizerReady) {
            _uiState.value = state.copy(recognitionMessage = "Espera a que el reconocimiento esté listo.")
            return
        }
        recognitionJob?.cancel()

        _uiState.value = state.copy(isSaving = true, isRecognizing = false, recognitionMessage = null)
        viewModelScope.launch {
            var stage = "lectura"
            val transition: PracticeAnswerResult
            try {
                pendingOutcome ?: run {
                    val answer = if (isCountExercise) {
                        state.countAnswerParts.joinToString(",")
                    } else if (isTraceExercise) {
                        state.answer.ifBlank { exercise.expectedAnswer }
                    } else {
                        // A new stroke clears answer, so an existing value belongs to these exact strokes.
                        state.answer.takeIf { it.isNotBlank() } ?: run {
                            _uiState.value = _uiState.value.copy(isRecognizing = true)
                            handwritingRecognitionRepository.recognize(state.strokes, writingWidth, writingHeight)
                        }
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
                    stage = "guardado"
                    val result = savePracticeAttempt(
                        sessionId = sessionId,
                        exercise = exercise,
                        answer = answer,
                        elapsedMillis = elapsedMillis,
                        strokes = if (isCountExercise) state.countStrokesByPart.flatten() else state.strokes
                    )
                    ExerciseOutcome(
                        exerciseName = exercise.content,
                        answer = answer,
                        expectedAnswer = exercise.expectedAnswer,
                        isCorrect = result.isCorrect,
                        elapsedMillis = elapsedMillis,
                        exerciseId = exercise.id,
                        axis = exercise.axis,
                        difficulty = exercise.difficulty,
                        expectedTimeMillis = exercise.expectedTimeMillis
                    ).also {
                        pendingOutcome = it
                        _uiState.value = _uiState.value.copy(answerSaved = true)
                    }
                }
                stage = "avance"
                val saved = learningSessionRepository.getSessionAnswers(sessionId)
                val outcomes = saved.map { it.toOutcome() }
                val answered = saved.map { it.exercise.id }.toSet()
                val remaining = state.exercises.filter { it.id !in answered }
                val nextIndex = outcomes.size
                val nextExercise = remaining.firstOrNull() ?: if (diagnosticMode) {
                    diagnosticEngine.nextQuestion(diagnosticBank, schoolLevel, outcomes.map { it.toDiagnosticAnswer() })
                } else null
                if (nextExercise != null) {
                    val nextExercises = saved.map { it.exercise } + remaining.ifEmpty { listOf(nextExercise) }
                    pendingOutcome = null
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        answerSaved = false,
                        isRecognizing = false,
                        exercises = nextExercises,
                        completedExercises = outcomes,
                        exerciseIndex = nextIndex,
                        exercise = nextExercise,
                        answer = "",
                        countAnswerParts = emptyCountAnswers(nextExercise),
                        countStrokesByPart = emptyCountStrokes(nextExercise),
                        activeCountAnswerIndex = null,
                        strokes = emptyList(),
                        recognitionMessage = null,
                        traceCoverage = 0f,
                        traceAdherence = 0f,
                        attemptStartedAt = System.currentTimeMillis()
                    )
                    transition = PracticeAnswerResult.NextExercise
                } else {
                    stage = "cierre"
                    val diagnosticReport = if (diagnosticMode) {
                        diagnosticEngine.buildReport(outcomes.map { it.toDiagnosticAnswer() }).also {
                            learningSessionRepository.completeDiagnosticSession(sessionId, studentId.orEmpty(), it)
                        }
                    } else {
                        learningSessionRepository.completeSession(sessionId)
                        null
                    }
                    _uiState.value = _uiState.value.copy(isSaving = false, completedExercises = outcomes)
                    transition = PracticeAnswerResult.SessionComplete(outcomes, diagnosticReport)
                    completedResult = transition
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("KMatPractice", "Fallo en $stage, pregunta ${state.exerciseIndex + 1}, ejercicio ${exercise.id}", error)
                _uiState.value = _uiState.value.copy(
                    isSaving = false, isRecognizing = false,
                    recognitionMessage = when (stage) {
                        "lectura" -> "No pude leer el número. Intenta comprobarlo otra vez."
                        "guardado" -> "No pude guardar la respuesta. Intenta otra vez."
                        else -> "Tu respuesta está guardada. Pulsa Continuar para seguir."
                    }
                )
                return@launch
            }
            // Navigation errors are not answer-recognition or persistence errors.
            onSaved(transition)
        }
    }

    private fun loadOfflineSession() {
        syncObservation?.cancel()
        recognitionJob?.cancel()
        strokeRevision++
        viewModelScope.launch {
            if (studentId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = "Primero selecciona un perfil de estudiante desde el acceso adulto."
                )
                return@launch
            }
            try {
                if (diagnosticMode) {
                    diagnosticBank = getDailyOfflineExercise.diagnosticBank(studentId)
                }
                while (true) {
                    val sessionId = if (diagnosticMode) learningSessionRepository.getDiagnosticSessionId(studentId)
                        else learningSessionRepository.getDailySessionId(studentId)
                    var plan = learningSessionRepository.getSessionPlan(sessionId)
                    val saved = learningSessionRepository.getSessionAnswers(sessionId)
                    if (plan.isEmpty()) {
                        val candidates = if (diagnosticMode) {
                            diagnosticEngine.initialQuestions(diagnosticBank, schoolLevel)
                        } else getDailyOfflineExercise(studentId)
                        val answeredIds = saved.map { it.exercise.id }.toSet()
                        val target = if (diagnosticMode) DiagnosticAssessmentEngine.INITIAL_QUESTIONS else 5
                        plan = saved.map { it.exercise } + candidates.filter { it.id !in answeredIds }
                            .take((target - saved.size).coerceAtLeast(0))
                        if (plan.isEmpty()) error("No exercises available")
                        learningSessionRepository.saveSessionPlan(sessionId, plan)
                        plan = learningSessionRepository.getSessionPlan(sessionId)
                    }
                    val outcomes = saved.map { it.toOutcome() }
                    val answeredIds = saved.map { it.exercise.id }.toSet()
                    val remaining = plan.filter { it.id !in answeredIds }
                    val next = remaining.firstOrNull() ?: if (diagnosticMode) {
                        diagnosticEngine.nextQuestion(diagnosticBank, schoolLevel, outcomes.map { it.toDiagnosticAnswer() })
                    } else null
                    if (next == null) {
                        // The last answer may have reached disk before the app was closed.
                        if (diagnosticMode) {
                            learningSessionRepository.completeDiagnosticSession(sessionId, studentId,
                                diagnosticEngine.buildReport(outcomes.map { it.toDiagnosticAnswer() }))
                            diagnosticMode = false
                        } else learningSessionRepository.completeSession(sessionId)
                        continue
                    }
                    pendingOutcome = null
                    completedResult = null
                    val exercises = saved.map { it.exercise } + remaining.ifEmpty { listOf(next) }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, isDiagnosticSession = diagnosticMode, loadError = null,
                        sessionId = sessionId, exercises = exercises, completedExercises = outcomes,
                        exerciseIndex = outcomes.size, exercise = next, answer = "", answerSaved = false,
                        countAnswerParts = emptyCountAnswers(next), countStrokesByPart = emptyCountStrokes(next),
                        activeCountAnswerIndex = null, strokes = emptyList(),
                        isDrawing = false, isRecognizing = false, recognitionMessage = null,
                        attemptStartedAt = if (_uiState.value.isRecognizerReady) System.currentTimeMillis() else 0L
                    )
                    observeRemoteProgress(sessionId)
                    // Local work can start immediately; reconciliation continues in the background.
                    viewModelScope.launch { learningSessionRepository.synchronize(studentId) }
                    break
                }
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

    fun refreshWhenResumed() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isDrawing || state.strokes.isNotEmpty() ||
            state.countStrokesByPart.any { it.isNotEmpty() } || state.countAnswerParts.any { it.isNotBlank() }) return
        _uiState.value = state.copy(isLoading = true)
        loadOfflineSession()
    }

    private fun observeRemoteProgress(sessionId: String) {
        syncObservation = viewModelScope.launch {
            try {
                learningSessionRepository.observeSessionSyncState(sessionId).collect { progress ->
                    val current = _uiState.value
                    if (progress != null && completedResult == null && pendingOutcome == null &&
                        !current.isLoading && !current.isSaving && !current.isDrawing && !current.answerSaved && current.sessionId == sessionId &&
                        (progress.status == "COMPLETED" || progress.answeredCount > current.completedExercises.size ||
                            (current.isDiagnosticSession && progress.diagnosticCompleted))) {
                        _uiState.value = current.copy(isLoading = true)
                        loadOfflineSession()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("KMatPractice", "No se pudo observar el progreso sincronizado", error)
            }
        }
    }

    private fun SavedExerciseAnswer.toOutcome() = ExerciseOutcome(
        exercise.content, answer, exercise.expectedAnswer, isCorrect, elapsedMillis,
        exercise.id, exercise.axis, exercise.difficulty, exercise.expectedTimeMillis
    )

    fun retryLoadOfflineSession() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
        loadOfflineSession()
    }

    override fun onCleared() {
        syncObservation?.cancel()
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
