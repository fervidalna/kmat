package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.SavePracticeAttemptUseCase
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatCream
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatOutline
import cl.kmat.ia.presentation.design.KMatProgress
import cl.kmat.ia.presentation.design.KMatYellow

@Composable
fun PracticeScreen(
    onCorrect: () -> Unit,
    onNeedsReview: () -> Unit,
    onActiveBreak: () -> Unit,
    onExit: () -> Unit
) {
    val application = LocalContext.current.applicationContext as KMatApplication
    val viewModel: PracticeViewModel = viewModel(
        factory = practiceViewModelFactory(
            application.container.learningSessionRepository,
            application.container.getDailyOfflineExercise,
            application.container.savePracticeAttempt
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PracticeContent(
        state = state,
        onStrokeStart = viewModel::startStroke,
        onStrokePoint = viewModel::appendPointToStroke,
        onStrokeEnd = viewModel::endStroke,
        onWritingAreaChanged = viewModel::updateWritingArea,
        onClear = viewModel::clearAnswerAndStrokes,
        onPrepare = viewModel::prepareRecognizer,
        onSubmit = {
            viewModel.submitAnswer { result ->
                when (result) {
                    PracticeAnswerResult.Correct -> onCorrect()
                    PracticeAnswerResult.NeedsReview -> onNeedsReview()
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
    onStrokeStart: (Float, Float) -> Unit,
    onStrokePoint: (Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    onWritingAreaChanged: (Float, Float) -> Unit,
    onClear: () -> Unit,
    onPrepare: () -> Unit,
    onSubmit: () -> Unit,
    onActiveBreak: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    KMatBackground {
        BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
            val sideBySide = maxWidth >= 600.dp && maxWidth > maxHeight
            val compact = maxHeight < 650.dp
            val tablet = maxWidth >= 840.dp
            Column(
                Modifier.fillMaxSize().padding(horizontal = if (tablet) 24.dp else 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PracticeHeader(tablet, onActiveBreak, onExit)
                if (sideBySide) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ExercisePrompt(state, compact, Modifier.weight(0.36f).fillMaxHeight())
                        AnswerArea(
                            state, onStrokeStart, onStrokePoint, onStrokeEnd, onWritingAreaChanged,
                            onPrepare, Modifier.weight(0.64f).fillMaxHeight(), compact = compact
                        )
                    }
                } else {
                    ExercisePrompt(state, compact, Modifier.fillMaxWidth())
                    AnswerArea(
                        state, onStrokeStart, onStrokePoint, onStrokeEnd, onWritingAreaChanged,
                        onPrepare, Modifier.fillMaxWidth().weight(1f)
                    )
                }
                // Reserve the actions' height; the canvas takes the remaining space.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !state.isSaving && !state.isDrawing,
                        modifier = Modifier.weight(1f).height(56.dp).testTag("clear-answer"),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KMatInk)
                    ) { Text("⌫ Borrar", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isLoading && state.isRecognizerReady && state.strokes.isNotEmpty() &&
                            !state.isSaving && !state.isDrawing,
                        modifier = Modifier.weight(1f).height(56.dp).testTag("check-answer"),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White)
                    ) {
                        Text(if (state.isSaving) "Comprobando…" else "✓ Comprobar", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
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
                    state.answer.isNotEmpty() -> "Número detectado: ${state.answer}"
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
                TextButton(onClick = onPrepare) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun ExercisePrompt(state: PracticeUiState, compact: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Cuenta las manzanas", color = KMatInk, fontSize = if (compact) 20.sp else 26.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(state.exercise?.statement ?: "Preparando…", color = KMatInk,
            fontSize = if (compact) 42.sp else 60.sp, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppleBubble(compact)
            Text("+", color = KMatInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            AppleBubble(compact)
        }
    }
}

@Composable
private fun PracticeHeader(tablet: Boolean, onActiveBreak: () -> Unit, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        KMatLogo(compact = true)
        Spacer(Modifier.weight(1f))
        if (tablet) {
            KMatMascot(Modifier.size(42.dp))
            Text("Tomás", color = KMatInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
            KMatProgress(completed = 2)
        }
        TextButton(onClick = onActiveBreak, modifier = Modifier.testTag("active-break")) {
            Text("Pausa", color = KMatInk, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onExit, modifier = Modifier.testTag("exit-practice")) {
            Text("Salir ×", color = KMatInk, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppleBubble(compact: Boolean) {
    Box(
        Modifier.size(if (compact) 40.dp else 56.dp).clip(CircleShape)
            .background(KMatYellow.copy(alpha = 0.55f)).border(2.dp, KMatCream, CircleShape),
        contentAlignment = Alignment.Center
    ) { Text("🍎", fontSize = if (compact) 28.sp else 38.sp) }
}

private fun practiceViewModelFactory(
    learningSessionRepository: LearningSessionRepository,
    getDailyOfflineExercise: GetDailyOfflineExerciseUseCase,
    savePracticeAttempt: SavePracticeAttemptUseCase
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PracticeViewModel(learningSessionRepository, getDailyOfflineExercise, savePracticeAttempt,
            MlKitHandwritingRecognitionRepository()) as T
}
