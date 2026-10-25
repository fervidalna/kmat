package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kmat.ia.KMatApplication
import cl.kmat.ia.data.recognition.MlKitHandwritingRecognitionRepository
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.SavePracticeAttemptUseCase
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatOutline
import cl.kmat.ia.presentation.design.KMatProgress
import cl.kmat.ia.presentation.design.KMatSkyDeep

@Composable
fun PracticeScreen(
    onComplete: (List<ExerciseOutcome>) -> Unit,
    onActiveBreak: () -> Unit,
    onExit: () -> Unit,
    diagnosticMode: Boolean = false,
    schoolLevel: String = "BASICO_1",
    onDiagnosticComplete: (List<ExerciseOutcome>, DiagnosticReport) -> Unit = { outcomes, _ -> onComplete(outcomes) }
) {
    val application = LocalContext.current.applicationContext as KMatApplication
    val viewModel: PracticeViewModel = viewModel(
        factory = practiceViewModelFactory(
            application.container.learningSessionRepository,
            application.container.getDailyOfflineExercise,
            application.container.savePracticeAttempt,
            application.container.authRepository.activeStudentIdOrNull(),
            diagnosticMode,
            schoolLevel
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PracticeContent(
        state = state,
        diagnosticMode = diagnosticMode,
        onCountSlotSelect = viewModel::selectCountAnswer,
        onCountSlotDone = viewModel::confirmCountAnswer,
        onStrokeStart = viewModel::startStroke,
        onStrokePoint = viewModel::appendPointToStroke,
        onStrokeEnd = viewModel::endStroke,
        onWritingAreaChanged = viewModel::updateWritingArea,
        onClear = {
            if (state.exercise?.id?.startsWith("offline-count-") == true) viewModel.clearCurrentCountAnswer()
            else viewModel.clearAnswerAndStrokes()
        },
        onPrepare = viewModel::prepareRecognizer,
        onRetryLoad = viewModel::retryLoadOfflineSession,
        onSubmit = {
            viewModel.submitAnswer { result ->
                when (result) {
                    PracticeAnswerResult.NextExercise -> Unit
                    is PracticeAnswerResult.SessionComplete -> {
                        if (result.diagnosticReport != null) onDiagnosticComplete(result.outcomes, result.diagnosticReport)
                        else onComplete(result.outcomes)
                    }
                }
            }
        },
        onActiveBreak = onActiveBreak,
        onExit = onExit
    )
}

@Composable
internal fun PracticeContent(
    state: PracticeUiState,
    diagnosticMode: Boolean = false,
    onCountSlotSelect: (Int) -> Unit,
    onCountSlotDone: () -> Unit,
    onStrokeStart: (Float, Float) -> Unit,
    onStrokePoint: (Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    onWritingAreaChanged: (Float, Float) -> Unit,
    onClear: () -> Unit,
    onPrepare: () -> Unit,
    onRetryLoad: () -> Unit,
    onSubmit: () -> Unit,
    onActiveBreak: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    KMatBackground {
        Box(modifier.fillMaxSize().safeDrawingPadding()) {
            val needsPreparation = !state.isRecognizerStatusKnown || !state.isRecognizerReady
            if (state.loadError != null) {
                PracticeLoadError(state.loadError, onRetryLoad, onExit)
            } else if (state.isLoading || !state.isRecognizerStatusKnown) {
                PracticeLoadingScreen()
            } else if (needsPreparation) {
                RecognizerPreparationScreen(
                    isPreparing = state.isPreparingRecognizer,
                    onRetry = onPrepare,
                    onExit = onExit
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sideBySide = maxWidth >= 600.dp && maxWidth > maxHeight
                    val compact = maxHeight < 650.dp
                    val tablet = maxWidth >= 840.dp
                    val isCountExercise = state.exercise?.id?.startsWith("offline-count-") == true
                    val isEnteringCountAnswer = isCountExercise && state.activeCountAnswerIndex != null
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = if (tablet) 24.dp else 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                PracticeHeader(
                    tablet = tablet,
                    exerciseIndex = state.exerciseIndex,
                    exerciseCount = if (diagnosticMode) 18 else state.exercises.size,
                    diagnosticMode = diagnosticMode,
                    onActiveBreak = onActiveBreak,
                    onExit = onExit
                )
                if (sideBySide) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ExercisePrompt(state, compact, onCountSlotSelect, Modifier.weight(if (isCountExercise && !isEnteringCountAnswer) 1f else 0.36f).fillMaxHeight())
                        if (!isCountExercise || isEnteringCountAnswer) AnswerArea(
                            state, onStrokeStart, onStrokePoint, onStrokeEnd, onWritingAreaChanged,
                            onPrepare, Modifier.weight(0.64f).fillMaxHeight(), compact = compact
                        )
                    }
                } else {
                    ExercisePrompt(state, compact, onCountSlotSelect, Modifier.fillMaxWidth())
                    if (!isCountExercise || isEnteringCountAnswer) AnswerArea(
                        state, onStrokeStart, onStrokePoint, onStrokeEnd, onWritingAreaChanged,
                        onPrepare, Modifier.fillMaxWidth().weight(1f)
                    )
                }
                // Reserve the actions' height; the canvas takes the remaining space.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !state.isSaving && !state.isDrawing &&
                            (!isCountExercise || isEnteringCountAnswer || state.countAnswerParts.any { it.isNotBlank() }),
                        modifier = Modifier.weight(1f).height(56.dp).testTag("clear-answer"),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = KMatSkyDeep,
                            contentColor = KMatInk
                        ),
                        border = androidx.compose.foundation.BorderStroke(2.dp, KMatInk)
                    ) { Text("⌫ Borrar", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Button(
                        onClick = if (isEnteringCountAnswer) onCountSlotDone else onSubmit,
                        enabled = !state.isLoading && (if (isEnteringCountAnswer) {
                            state.isRecognizerReady && state.answer.isNotBlank() && state.strokes.isNotEmpty()
                        } else if (isCountExercise) state.countAnswerParts.all { it.isNotBlank() }
                        else state.isRecognizerReady && state.strokes.isNotEmpty()) &&
                            !state.isSaving && !state.isDrawing,
                        modifier = Modifier.weight(1f).height(56.dp).testTag("check-answer"),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White)
                    ) {
                        Text(
                            when {
                                state.isSaving -> "Comprobando…"
                                isEnteringCountAnswer -> "Listo"
                                else -> "✓ Comprobar"
                            }, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
        }
            }
        }
    }
}
}

@Composable
private fun PracticeLoadError(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    KMatBackground {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(message, color = KMatInk, textAlign = TextAlign.Center, fontSize = 20.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("Reintentar") }
            TextButton(onClick = onExit) { Text("Volver al inicio") }
        }
    }
}

@Composable
private fun PracticeLoadingScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.94f)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KMatLogo()
            KMatMascot(Modifier.size(112.dp))
            CircularProgressIndicator(color = KMatGreen)
            Text("Preparando tu sesión", color = KMatInk, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Text("Estamos cargando tus ejercicios…", color = KMatInk, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RecognizerPreparationScreen(
    isPreparing: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.94f)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KMatLogo()
            KMatMascot(Modifier.size(112.dp))
            if (isPreparing) CircularProgressIndicator(color = KMatGreen)
            Text(
                text = if (isPreparing) "Descargando el detector de escritura" else "Necesitamos preparar la escritura",
                color = KMatInk,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isPreparing) {
                    "La primera descarga requiere internet. Espera un momento; la práctica se abrirá cuando esté lista."
                } else {
                    "No se pudo descargar el detector. Conéctate a internet y reintenta, o vuelve más tarde."
                },
                color = KMatInk,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            if (!isPreparing) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White)
                ) { Text("Reintentar descarga", fontWeight = FontWeight.Bold) }
                TextButton(onClick = onExit) { Text("Volver", color = KMatInk, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun AnswerArea(
    state: PracticeUiState,
    onStrokeStart: (Float, Float) -> Unit,
    onStrokePoint: (Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    onWritingAreaChanged: (Float, Float) -> Unit,
    onPrepare: () -> Unit,
    modifier: Modifier,
    compact: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!compact) {
            Text("Escribe con tu dedo o lápiz", color = KMatInk, fontSize = 16.sp, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            HandwritingPad(
                strokes = state.strokes,
                onStrokeStart = onStrokeStart,
                onStrokePoint = onStrokePoint,
                onStrokeEnd = onStrokeEnd,
                onWritingAreaChanged = onWritingAreaChanged,
                modifier = Modifier.fillMaxSize().testTag("handwriting-pad")
            )
            if (state.strokes.isEmpty()) {
                Text("Escribe aquí", color = KMatInk.copy(alpha = 0.35f), fontSize = 26.sp)
            }
        }
        // Stable height: status changes must not resize the canvas or erase the ink.
        Row(Modifier.fillMaxWidth().height(if (compact) 36.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    state.isPreparingRecognizer -> "Preparando escritura… La primera vez necesita internet."
                    !state.isRecognizerReady -> "Conéctate a internet y toca Reintentar."
                    state.isDrawing -> "Termina de escribir tu número."
                    state.isRecognizing -> "Leyendo tu número…"
                    state.recognitionMessage != null -> state.recognitionMessage.orEmpty()
                    state.answer.isNotEmpty() -> "Respuesta detectada: ${state.answer}"
                    state.exercise?.id?.startsWith("offline-count-") == true -> "Escribe el número para el espacio resaltado."
                    else -> "Escribe un número y pulsa Comprobar."
                },
                modifier = Modifier.weight(1f).testTag("recognition-status"),
                color = KMatInk,
                fontSize = if (compact) 14.sp else 16.sp,
                lineHeight = if (compact) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!state.isRecognizerReady && !state.isPreparingRecognizer) {
                OutlinedButton(
                    onClick = onPrepare,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = KMatSkyDeep,
                        contentColor = KMatInk
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, KMatInk)
                ) { Text("Reintentar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ExercisePrompt(
    state: PracticeUiState,
    compact: Boolean,
    onCountSlotSelect: (Int) -> Unit,
    modifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(state.exercise?.content ?: "Ejercicio", color = KMatInk, fontSize = if (compact) 20.sp else 26.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        state.exercise?.visualSpec?.takeIf(String::isNotBlank)?.let { ExerciseVisual(it) }
        if (state.exercise?.id?.startsWith("offline-count-") == true) {
            val sequenceParts = state.exercise.statement.substringAfter(": ").split("__")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                sequenceParts.forEachIndexed { index, sequenceText ->
                    if (sequenceText.isNotEmpty()) {
                        Text(sequenceText, color = KMatInk, fontSize = if (compact) 20.sp else 28.sp,
                            fontWeight = FontWeight.ExtraBold)
                    }
                    if (index < sequenceParts.lastIndex) {
                        CountAnswerSlot(
                            value = state.countAnswerParts.getOrElse(index) { "" },
                            isActive = state.activeCountAnswerIndex == index
                        ) { onCountSlotSelect(index) }
                    }
                }
            }
        } else {
            Text(state.exercise?.statement ?: "Preparando…", color = KMatInk,
                fontSize = if (compact) 32.sp else 42.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, lineHeight = if (compact) 38.sp else 50.sp)
        }
    }
}

@Composable
private fun CountAnswerSlot(value: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(width = 70.dp, height = 58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(2.dp, if (isActive) KMatGreen else KMatOutline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(value.ifBlank { "__" }, color = if (value.isBlank()) KMatInk.copy(alpha = 0.45f) else KMatInk,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PracticeHeader(
    tablet: Boolean,
    exerciseIndex: Int,
    exerciseCount: Int,
    diagnosticMode: Boolean,
    onActiveBreak: () -> Unit,
    onExit: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        KMatLogo(compact = true)
        if (exerciseCount > 0) {
            Text("${if (diagnosticMode) "Diagnóstico · " else ""}${exerciseIndex + 1} / $exerciseCount", color = KMatInk, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp))
            KMatProgress(completed = exerciseIndex + 1, total = exerciseCount)
        }
        Spacer(Modifier.weight(1f))
        if (tablet) {
            KMatMascot(Modifier.size(42.dp))
            Text("Tomás", color = KMatInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
        }
        OutlinedButton(
            onClick = onActiveBreak,
            modifier = Modifier.testTag("active-break"),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = KMatSkyDeep,
                contentColor = KMatInk
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, KMatInk)
        ) {
            Text("Pausa", color = KMatInk, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.testTag("exit-practice"),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = KMatSkyDeep,
                contentColor = KMatInk
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, KMatInk)
        ) {
            Text("Salir ×", color = KMatInk, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun practiceViewModelFactory(
    learningSessionRepository: LearningSessionRepository,
    getDailyOfflineExercise: GetDailyOfflineExerciseUseCase,
    savePracticeAttempt: SavePracticeAttemptUseCase,
    studentId: String?,
    diagnosticMode: Boolean,
    schoolLevel: String
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PracticeViewModel(learningSessionRepository, getDailyOfflineExercise, savePracticeAttempt, studentId,
            MlKitHandwritingRecognitionRepository(), diagnosticMode, schoolLevel) as T
}
