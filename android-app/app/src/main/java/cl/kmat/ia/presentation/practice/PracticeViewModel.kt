package cl.kmat.ia.presentation.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.StrokePoint
import cl.kmat.ia.domain.repository.HandwritingRecognitionRepository
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
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
    val sessionId: String? = null,
    val exercise: Exercise? = null,
    val answer: String = "",
    val strokes: List<HandwritingStroke> = emptyList(),
    val isPreparingRecognizer: Boolean = false,
    val isRecognizerReady: Boolean = false,
    val isRecognizing: Boolean = false,
    val isDrawing: Boolean = false,
    val recognitionMessage: String? = null,
    val attemptStartedAt: Long = System.currentTimeMillis()
)

enum class PracticeAnswerResult { Correct, NeedsReview }

class PracticeViewModel(
    private val learningSessionRepository: LearningSessionRepository,
    private val getDailyOfflineExercise: GetDailyOfflineExerciseUseCase,
    private val savePracticeAttempt: SavePracticeAttemptUseCase,
    private val handwritingRecognitionRepository: HandwritingRecognitionRepository
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
        _uiState.value = _uiState.value.copy(
            answer = "", isDrawing = true, isRecognizing = false, recognitionMessage = null,
            strokes = _uiState.value.strokes + HandwritingStroke(listOf(point))
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
        _uiState.value = _uiState.value.copy(isPreparingRecognizer = true, recognitionMessage = null)
        viewModelScope.launch {
            try {
                handwritingRecognitionRepository.prepare()
                _uiState.value = _uiState.value.copy(
                    isPreparingRecognizer = false, isRecognizerReady = true, recognitionMessage = null
                )
                if (_uiState.value.strokes.isNotEmpty() && !_uiState.value.isDrawing) endStroke()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
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
        if (state.isSaving || state.isDrawing) return
        if (state.strokes.isEmpty()) {
            _uiState.value = state.copy(recognitionMessage = "Escribe un número en el espacio blanco.")
            return
        }
        if (!state.isRecognizerReady) {
            _uiState.value = state.copy(recognitionMessage = "Espera a que el reconocimiento esté listo.")
            return
        }
        recognitionJob?.cancel()

        _uiState.value = state.copy(isSaving = true, isRecognizing = true, recognitionMessage = null)
        viewModelScope.launch {
            try {
                val answer = handwritingRecognitionRepository.recognize(
                    state.strokes, writingWidth, writingHeight
                )
                if (answer == null) {
                    _uiState.value = _uiState.value.copy(
                        answer = "", isSaving = false, isRecognizing = false,
                        recognitionMessage = "No pude leer un número. Bórralo y prueba otra vez."
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(answer = answer, isRecognizing = false)
                val result = savePracticeAttempt(
                    sessionId = sessionId,
                    exercise = exercise,
                    answer = answer,
                    elapsedMillis = System.currentTimeMillis() - state.attemptStartedAt,
                    strokes = state.strokes
                )
                _uiState.value = _uiState.value.copy(isSaving = false)
                onSaved(if (result.isCorrect) PracticeAnswerResult.Correct else PracticeAnswerResult.NeedsReview)
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
            val sessionId = learningSessionRepository.getDailySessionId(LOCAL_TOMAS_ID)
            val exercise = getDailyOfflineExercise()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sessionId = sessionId,
                exercise = exercise,
                attemptStartedAt = System.currentTimeMillis()
            )
        }
    }

    override fun onCleared() {
        recognitionJob?.cancel()
        handwritingRecognitionRepository.close()
        super.onCleared()
    }

    private companion object {
        const val LOCAL_TOMAS_ID = "local-tomas"
    }
}
