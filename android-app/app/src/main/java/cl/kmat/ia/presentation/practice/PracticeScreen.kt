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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.launch

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
    val audio = remember(application) { PracticeEncouragementAudio(application) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audioScope = rememberCoroutineScope()
    DisposableEffect(audio, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) audio.release()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); audio.release() }
    }
    PracticeContent(
        state = state,
        diagnosticMode = state.isDiagnosticSession,
        mascotSpeaking = audio.isPlaying,
        onOpenExplanation = audio::release,
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
                    PracticeAnswerResult.NextExercise -> if (!state.isDiagnosticSession) {
                        audioScope.launch {
                            withFrameNanos { }
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) audio.play()
                        }
                    }
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
    onOpenExplanation: () -> Unit = {},
    mascotSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val exercise = state.exercise
    val exampleKind = if (diagnosticMode || exercise == null) null else when {
        exercise.contentCode == "TRAZADO_NUMEROS" -> "TRAZADO_NUMEROS"
        exercise.contentCode == "ADICION" || exercise.id.startsWith("offline-sum-") -> "SUMA"
        exercise.contentCode == "CONTEO" || exercise.id.startsWith("offline-count-") -> "CONTEO"
        exercise.axis in setOf("PATRONES_ALGEBRA", "GEOMETRIA", "MEDICION", "DATOS_PROBABILIDAD") -> exercise.axis
        else -> null
    }
    val hasExplanation = exampleKind != null
    var explanationOpen by remember(state.exercise?.id, diagnosticMode) { mutableStateOf(false) }
    if (hasExplanation && explanationOpen) {
        if (exampleKind == "TRAZADO_NUMEROS") {
            NumberTraceExampleScreen(
                number = exercise?.expectedAnswer?.toIntOrNull() ?: 1,
                onBack = { explanationOpen = false }
            )
        } else if (exampleKind == "SUMA") {
            AdditionExplanationScreen(useAlternativeExample = exercise?.expectedAnswer?.trim() == "5", onBack = { explanationOpen = false })
        } else if (exampleKind == "CONTEO" && exercise?.id != "offline-count-stars-5" &&
            !exercise?.visualSpec.orEmpty().contains("\"OBJECTS\"")) {
            CountingSequenceScreen(exercise = requireNotNull(exercise), onBack = { explanationOpen = false })
        } else {
            SubjectExplanationScreen(kind = requireNotNull(exampleKind), expectedAnswer = exercise?.expectedAnswer.orEmpty(), onBack = { explanationOpen = false })
        }
        return
    }
    KMatBackground {
        Box(modifier.fillMaxSize().safeDrawingPadding()) {
            val isTraceExercise = state.exercise?.contentCode == "TRAZADO_NUMEROS"
            val needsPreparation = !isTraceExercise && (!state.isRecognizerStatusKnown || !state.isRecognizerReady)
            if (state.loadError != null) {
                PracticeLoadError(state.loadError, onRetryLoad, onExit)
            } else if (state.isLoading || (!isTraceExercise && !state.isRecognizerStatusKnown)) {
                PracticeLoadingScreen()
            } else if (needsPreparation) {
                RecognizerPreparationScreen(
                    isPreparing = state.isPreparingRecognizer,
                    onRetry = onPrepare,
                    onExit = onExit
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sideBySide = maxWidth >= 480.dp && maxWidth > maxHeight
                    val mobile = minOf(maxWidth, maxHeight) < 600.dp
                    val compact = mobile || maxHeight < 650.dp
                    val tablet = maxWidth >= 840.dp
                    val isCountExercise = state.exercise?.id?.startsWith("offline-count-") == true
                    val isTraceExercise = state.exercise?.contentCode == "TRAZADO_NUMEROS"
                    val isEnteringCountAnswer = isCountExercise && state.activeCountAnswerIndex != null
                    val hasAnswerArea = !isCountExercise || isEnteringCountAnswer
                    val hasVisual = !state.exercise?.visualSpec.isNullOrBlank()
                    val phoneQuestionHeight = if (hasVisual) {
                        (maxHeight * 0.34f).coerceIn(if (isCountExercise) 270.dp else 210.dp, 300.dp)
                    } else (maxHeight * 0.27f).coerceIn(140.dp, 210.dp)
                    val phoneAnswerHeight = (maxHeight - phoneQuestionHeight - 160.dp).coerceAtLeast(240.dp)
                    val phoneLandscapeHeight = (maxHeight - 140.dp).coerceAtLeast(if (hasVisual && isCountExercise) 270.dp else 220.dp)
                    Column(
                        Modifier.fillMaxSize()
                            .padding(horizontal = if (tablet) 24.dp else 12.dp, vertical = 8.dp),
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
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .then(if (mobile) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                if (sideBySide) {
                    Row(
                        Modifier.fillMaxWidth().then(if (mobile) Modifier.height(phoneLandscapeHeight) else Modifier.weight(1f)),
                        horizontalArrangement = Arrangement.spacedBy(if (mobile) 10.dp else 20.dp)
                    ) {
                        ExercisePromptWithMascot(
                            state, compact, mobile, onCountSlotSelect, !diagnosticMode, mascotSpeaking, true,
                            if (hasExplanation) { { onOpenExplanation(); explanationOpen = true } } else null,
                            Modifier.weight(if (isCountExercise && !isEnteringCountAnswer) 1f else 0.36f).fillMaxHeight()
                        )
                        if (!isCountExercise || isEnteringCountAnswer) AnswerArea(
                            state, onStrokeStart, onStrokePoint, onStrokeEnd, onWritingAreaChanged,
                            onPrepare, Modifier.weight(0.64f).fillMaxHeight(), compact = compact
                        )
                    }
                } else {
                    val promptModifier = if (mobile) {
                        Modifier.fillMaxWidth().height(if (hasAnswerArea) phoneQuestionHeight else phoneQuestionHeight.coerceAtLeast(230.dp))
                    } else Modifier.fillMaxWidth()
                    ExercisePromptWithMascot(
                        state, compact, mobile, onCountSlotSelect, !diagnosticMode, mascotSpeaking, false,
                        if (hasExplanation) { { onOpenExplanation(); explanationOpen = true } } else null,
                        promptModifier
                    )
                    if (!isCountExercise || isEnteringCountAnswer) AnswerArea(
                        state, onStrokeStart, onStrokePoint, onStrokeEnd, onWritingAreaChanged,
                        onPrepare, Modifier.fillMaxWidth().then(if (mobile) Modifier.height(phoneAnswerHeight) else Modifier.weight(1f)), compact = compact
                    )
                }
                }
                // Reserve the actions' height; the canvas takes the remaining space.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !state.isSaving && !state.answerSaved && !state.isDrawing &&
                            (!isCountExercise || isEnteringCountAnswer || state.countAnswerParts.any { it.isNotBlank() }),
                        modifier = Modifier.weight(1f).height(if (mobile) 50.dp else 56.dp).testTag("clear-answer"),
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
                        else (isTraceExercise || state.isRecognizerReady) && state.strokes.isNotEmpty() &&
                            (!isTraceExercise || state.answer.isNotBlank())) &&
                            !state.isSaving && !state.isDrawing,
                        modifier = Modifier.weight(1f).height(if (mobile) 50.dp else 56.dp).testTag("check-answer"),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White)
                    ) {
                        Text(
                            when {
                                state.isSaving -> "Comprobando…"
                                state.answerSaved -> "Continuar"
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
private fun ExercisePromptWithMascot(
    state: PracticeUiState,
    compact: Boolean,
    mobile: Boolean,
    onCountSlotSelect: (Int) -> Unit,
    showMascot: Boolean,
    mascotSpeaking: Boolean,
    sideBySide: Boolean,
    onExplanation: (() -> Unit)?,
    modifier: Modifier
) {
    if (!showMascot) {
        ExercisePrompt(state, compact, mobile, sideBySide || mobile, onCountSlotSelect, modifier)
        return
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(if (mobile) 48.dp else if (compact) 168.dp else 224.dp)) {
            TalkingPracticePuppy(
                mascotSpeaking,
                Modifier.align(if (sideBySide) Alignment.Center else Alignment.CenterEnd)
                    .width(if (mobile) 104.dp else if (sideBySide && !compact) 336.dp else 272.dp)
                    .fillMaxHeight()
            )
            if (onExplanation != null) {
                IconButton(
                    onClick = onExplanation,
                    modifier = Modifier.align(Alignment.TopStart).size(48.dp)
                        .background(Color.White, CircleShape).border(2.dp, KMatInk, CircleShape)
                        .testTag("exercise-explanation")
                        .semantics { contentDescription = "Ver explicación del ejercicio" }
                ) {
                    Text("?", color = KMatInk, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        ExercisePrompt(
            state, compact, mobile, sideBySide || mobile, onCountSlotSelect,
            if (sideBySide || mobile) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()
        )
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
    val isTraceExercise = state.exercise?.contentCode == "TRAZADO_NUMEROS"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!compact) {
            Text("Escribe con tu dedo o lápiz", color = KMatInk, fontSize = 16.sp, maxLines = 1)
        }
        if (isTraceExercise && !compact) {
            Text("Empieza en el punto amarillo y sigue los puntos en orden", color = KMatInk, fontSize = 14.sp, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            HandwritingPad(
                strokes = state.strokes,
                onStrokeStart = onStrokeStart,
                onStrokePoint = onStrokePoint,
                onStrokeEnd = onStrokeEnd,
                onWritingAreaChanged = onWritingAreaChanged,
                traceNumber = state.exercise?.expectedAnswer?.toIntOrNull().takeIf { isTraceExercise },
                modifier = Modifier.fillMaxSize().testTag("handwriting-pad")
            )
            if (state.strokes.isEmpty() && !isTraceExercise) {
                Text("Escribe aquí", color = KMatInk.copy(alpha = 0.35f), fontSize = 26.sp)
            }
        }
        // Stable height: status changes must not resize the canvas or erase the ink.
        Row(Modifier.fillMaxWidth().height(if (compact) 36.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    isTraceExercise && state.recognitionMessage != null -> state.recognitionMessage.orEmpty()
                    isTraceExercise -> "En orden: ${(state.traceCoverage * 100).toInt()}% · Sobre la guía: ${(state.traceAdherence * 100).toInt()}%"
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
            if (!isTraceExercise && !state.isRecognizerReady && !state.isPreparingRecognizer) {
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
    mobile: Boolean,
    boundedHeight: Boolean,
    onCountSlotSelect: (Int) -> Unit,
    modifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(state.exercise?.content ?: "Ejercicio", color = KMatInk, fontSize = if (mobile) 16.sp else if (compact) 20.sp else 26.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        state.exercise?.visualSpec?.takeIf(String::isNotBlank)?.let {
            ExerciseVisual(
                it,
                modifier = if (boundedHeight) Modifier.weight(0.9f) else Modifier.heightIn(max = 160.dp),
                compact = mobile
            )
        }
        if (state.exercise?.id?.startsWith("offline-count-") == true) {
            val statement = state.exercise.statement
            val instruction = statement.substringBefore(": ").trim()
            if (instruction.isNotBlank()) {
                Text(
                    instruction,
                    color = KMatInk,
                    fontSize = if (mobile) 14.sp else if (compact) 16.sp else 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
            val sequenceParts = statement.substringAfter(": ", statement).split("__")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                sequenceParts.forEachIndexed { index, sequenceText ->
                    if (sequenceText.isNotEmpty()) {
                        Text(sequenceText, color = KMatInk, fontSize = if (mobile) 18.sp else if (compact) 20.sp else 28.sp,
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
            if (sequenceParts.size == 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tu respuesta:", color = KMatInk, fontSize = if (mobile) 15.sp else if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold)
                    CountAnswerSlot(
                        value = state.countAnswerParts.firstOrNull().orEmpty(),
                        isActive = state.activeCountAnswerIndex == 0
                    ) { onCountSlotSelect(0) }
                }
            }
        } else {
            val promptShape = RoundedCornerShape(24.dp)
            val questionModifier = Modifier.fillMaxWidth()
                .then(if (boundedHeight) Modifier.weight(1f) else Modifier)
                    .shadow(8.dp, promptShape)
                    .clip(promptShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFEAF6FF))
                        )
                    )
                    .border(1.dp, Color(0xFFB8DDF6), promptShape)
                    .padding(horizontal = if (mobile) 10.dp else 18.dp, vertical = if (mobile) 4.dp else 10.dp)
            AutoFitQuestionText(
                text = state.exercise?.statement ?: "Preparando...",
                modifier = questionModifier,
                maximumFontSize = if (mobile) 25.sp else if (compact) 32.sp else 42.sp,
                minimumFontSize = if (mobile) 13.sp else 16.sp
            )
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
            val visibleDots = if (tablet) exerciseCount else minOf(exerciseCount, 4)
            val completedDots = if (exerciseCount <= visibleDots) exerciseIndex + 1
                else ((exerciseIndex + 1) * visibleDots / exerciseCount).coerceAtLeast(1)
            KMatProgress(completed = completedDots, total = visibleDots)
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
