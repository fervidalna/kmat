package cl.kmat.ia.presentation.practice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.kmat.ia.R
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo

internal data class CountingSequenceExample(val numbers: List<Int>, val step: Int, val audio: Int) {
    val missing: Int get() = numbers[2]
}

/** Examples match the direction and interval of the exercise, with a different missing answer. */
internal fun countingSequenceExample(exercise: Exercise): CountingSequenceExample {
    val text = "${exercise.content} ${exercise.statement}".lowercase()
    val pair = Regex("(\\d+)\\s*,\\s*(\\d+)").find(exercise.statement.substringAfter(":", ""))
    val observedStep = pair?.let { it.groupValues[2].toInt() - it.groupValues[1].toInt() }
    val step = when {
        "hacia atrás" in text || exercise.id.contains("count-back") -> -1
        exercise.id.contains("count-by-10-") || "10 en 10" in text -> 10
        exercise.id.contains("count-by-5-") || "5 en 5" in text -> 5
        exercise.id.contains("count-by-2-") || "2 en 2" in text -> 2
        observedStep in listOf(-1, 2, 5, 10) -> observedStep!!
        else -> 1
    }
    return when (step) {
        -1 -> CountingSequenceExample(listOf(5, 4, 3, 2), step, R.raw.serie_atras)
        2 -> CountingSequenceExample(listOf(2, 4, 6, 8), step, R.raw.serie_dos)
        5 -> CountingSequenceExample(listOf(5, 10, 15, 20), step, R.raw.serie_cinco)
        10 -> CountingSequenceExample(listOf(10, 20, 30, 40), step, R.raw.serie_diez)
        else -> {
            val answers = exercise.expectedAnswer.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            if (4 in answers) CountingSequenceExample(listOf(1, 2, 3, 4), 1, R.raw.serie_uno_tres)
            else CountingSequenceExample(listOf(2, 3, 4, 5), 1, R.raw.serie_uno_cuatro)
        }
    }
}

@Composable
internal fun CountingSequenceScreen(exercise: Exercise, onBack: () -> Unit) {
    val example = remember(exercise) { countingSequenceExample(exercise) }
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val voice = remember(context) { ExplanationVoice(context) }
    var revealed by remember { mutableStateOf(false) }
    var runNumber by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    BackHandler(onBack = onBack)
    LaunchedEffect(runNumber, running) {
        if (!running) return@LaunchedEffect
        revealed = false
        voice.play(example.audio)
        revealed = true
    }
    DisposableEffect(voice, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                running = false
                voice.release()
                revealed = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); voice.release() }
    }

    KMatBackground {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag("counting-sequence-explanation")) {
            val landscape = maxWidth >= 480.dp && maxWidth > maxHeight
            val mascotSize = if (landscape) 360.dp else minOf(300.dp, maxHeight * 0.35f)
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    KMatLogo(compact = true)
                    OutlinedButton(onClick = onBack) { Text("Volver al ejercicio", color = KMatInk, fontWeight = FontWeight.Bold) }
                }
                Text("Aprendamos a completar una serie", Modifier.fillMaxWidth(), color = KMatInk,
                    fontSize = if (landscape) 22.sp else 25.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (landscape) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(0.35f), contentAlignment = Alignment.Center) {
                            TalkingPracticePuppy(voice.isPlaying, Modifier.size(mascotSize))
                        }
                        SequenceExamplePanel(example, revealed, Modifier.weight(0.65f))
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TalkingPracticePuppy(voice.isPlaying, Modifier.size(mascotSize))
                    }
                    SequenceExamplePanel(example, revealed, Modifier.fillMaxWidth().weight(1f))
                }
                if (revealed) {
                    Button(onClick = { running = true; runNumber++ }, modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White)) {
                        Text("Ver explicación otra vez", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SequenceExamplePanel(example: CountingSequenceExample, revealed: Boolean, modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "espacio-vacío").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "resaltar-espacio")
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.95f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val instruction = when (example.step) {
            -1 -> "Cuenta hacia atrás y completa la serie"
            1 -> "Cuenta de uno en uno y completa la serie"
            else -> "Cuenta de ${example.step} en ${example.step} y completa la serie"
        }
        Text(instruction, color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().testTag("counting-example-series"), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            example.numbers.forEachIndexed { index, number ->
                if (index > 0) Text(",", color = KMatInk, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp))
                if (index == 2) {
                    Box(Modifier.size(width = 54.dp, height = 54.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White).border(2.dp, if (revealed) KMatGreen else KMatGreen.copy(alpha = pulse), RoundedCornerShape(10.dp))
                        .testTag("counting-example-blank"), contentAlignment = Alignment.Center) {
                        Text(if (revealed) "$number" else "__", color = KMatInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("$number", color = KMatInk, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (revealed) Text("El número que falta es ${example.missing}", color = KMatGreen,
            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            modifier = Modifier.testTag("counting-example-result"))
    }
}
