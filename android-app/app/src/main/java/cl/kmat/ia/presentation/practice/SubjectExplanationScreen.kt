package cl.kmat.ia.presentation.practice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import kotlinx.coroutines.delay

/** Choose an example whose final number is different from the active exercise's answer. */
internal fun subjectExampleResult(kind: String, answer: String): Int {
    val answerNumber = answer.trim().toIntOrNull()
    return when (kind) {
        "CONTEO" -> {
            val answers = answer.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            listOf(3, 4, 5, 2, 1).firstOrNull { it !in answers } ?: 3
        }
        "PATRONES_ALGEBRA" -> if (answerNumber == 4) 5 else 4
        "GEOMETRIA" -> if (answerNumber == 4) 3 else 4
        "MEDICION" -> if (answerNumber == 2) 3 else 2
        "DATOS_PROBABILIDAD" -> if (answerNumber == 3) 4 else 3
        else -> error("Materia sin ejemplo")
    }
}

@Composable
internal fun SubjectExplanationScreen(kind: String, expectedAnswer: String, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val voice = remember(context) { ExplanationVoice(context) }
    val result = remember(kind, expectedAnswer) { subjectExampleResult(kind, expectedAnswer) }
    val isPattern = kind == "PATRONES_ALGEBRA"
    val title = when (kind) {
        "CONTEO" -> "Aprendamos a contar"
        "PATRONES_ALGEBRA" -> "Aprendamos sobre patrones"
        "GEOMETRIA" -> "Aprendamos geometría"
        "MEDICION" -> "Aprendamos a medir"
        else -> "Aprendamos sobre datos"
    }
    val introduction = when (kind) {
        "CONTEO" -> "Contemos cada estrella una sola vez."
        "PATRONES_ALGEBRA" -> "Cada número aumenta en uno. ¿Cuál sigue?"
        "GEOMETRIA" -> "Contemos los lados de esta figura."
        "MEDICION" -> "¿Cuántos bloques más tiene la barra larga?"
        else -> "Cada estrella representa un voto."
    }
    val audio = when (kind) {
        "CONTEO" -> R.raw.ejemplo_conteo
        "PATRONES_ALGEBRA" -> R.raw.ejemplo_patrones
        "GEOMETRIA" -> R.raw.ejemplo_geometria
        "MEDICION" -> R.raw.ejemplo_medicion
        else -> R.raw.ejemplo_datos
    }
    val numberAudio = listOf(R.raw.sumar_count_one, R.raw.sumar_count_two, R.raw.sumar_count_three,
        R.raw.sumar_count_four, R.raw.sumar_count_five)
    var stage by remember { mutableIntStateOf(0) }
    var activeIndex by remember { mutableIntStateOf(-1) }
    var completedCount by remember { mutableIntStateOf(0) }
    var runNumber by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var caption by remember { mutableStateOf(introduction) }
    BackHandler(onBack = onBack)

    LaunchedEffect(runNumber, running) {
        if (!running) return@LaunchedEffect
        stage = 1
        activeIndex = -1
        completedCount = 0
        caption = introduction
        voice.play(audio)
        delay(350)
        caption = if (isPattern) "Sigamos el patrón." else "¡Contemos!"
        if (!isPattern) voice.play(R.raw.sumar_say_count)
        val count = if (isPattern) 3 else result
        repeat(count) { index ->
            activeIndex = index
            val number = if (isPattern) result - 3 + index else index + 1
            caption = listOf("Uno", "Dos", "Tres", "Cuatro", "Cinco")[number - 1]
            voice.play(numberAudio[number - 1])
            completedCount = index + 1
            activeIndex = -1
            delay(200)
        }
        caption = "El resultado es…"
        voice.play(R.raw.ejemplo_resultado)
        stage = 2
        caption = when (kind) {
            "CONTEO" -> if (result == 1) "Hay una estrella." else "Hay $result estrellas."
            "PATRONES_ALGEBRA" -> "El número que sigue es $result."
            "GEOMETRIA" -> "La figura tiene $result lados."
            "MEDICION" -> "La diferencia es de $result bloques."
            else -> "Hay $result votos."
        }
        voice.play(numberAudio[result - 1])
    }
    DisposableEffect(voice, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                running = false
                voice.release()
                stage = 2
                completedCount = result
                activeIndex = -1
                caption = "Resultado: $result"
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); voice.release() }
    }

    KMatBackground {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag("subject-explanation")) {
            val landscape = maxWidth >= 480.dp && maxWidth > maxHeight
            val mascotSize = if (landscape) 360.dp else minOf(300.dp, maxHeight * 0.35f)
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    KMatLogo(compact = true)
                    OutlinedButton(onClick = onBack) { Text("Volver al ejercicio", color = KMatInk, fontWeight = FontWeight.Bold) }
                }
                Text(title, Modifier.fillMaxWidth(), color = KMatInk, fontSize = if (landscape) 22.sp else 25.sp,
                    fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (landscape) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(0.35f), contentAlignment = Alignment.Center) {
                            TalkingPracticePuppy(voice.isPlaying, Modifier.size(mascotSize))
                        }
                        SubjectExamplePanel(kind, result, stage, activeIndex, completedCount, caption, Modifier.weight(0.65f))
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TalkingPracticePuppy(voice.isPlaying, Modifier.size(mascotSize))
                    }
                    SubjectExamplePanel(kind, result, stage, activeIndex, completedCount, caption, Modifier.fillMaxWidth().weight(1f))
                }
                if (stage >= 2) {
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
private fun SubjectExamplePanel(kind: String, result: Int, stage: Int, active: Int, completed: Int,
    caption: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.95f))
        .padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(caption, color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.testTag("subject-example-caption"))
        Spacer(Modifier.height(20.dp))
        when (kind) {
            "PATRONES_ALGEBRA" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { index ->
                    val number = result - 3 + index
                    val visible = index < 3 || stage >= 2
                    val lift by animateDpAsState(if (active == index) (-12).dp else 0.dp, tween(180), label = "patron-$index")
                    Box(Modifier.offset(y = lift).size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (active == index) KMatGreen else Color(0xFFE2F3FF)), contentAlignment = Alignment.Center) {
                        Text(if (visible) "$number" else "?", color = if (active == index) Color.White else KMatInk,
                            fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                }
            }
            "GEOMETRIA" -> GeometryExample(result, active, completed)
            "MEDICION" -> MeasurementExample(result, active, completed)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(result) { index ->
                    val lift by animateDpAsState(if (active == index) (-16).dp else 0.dp, tween(180), label = "voto-$index")
                    Text("⭐", fontSize = 32.sp, modifier = Modifier.offset(y = lift))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (stage >= 2) {
            val output = when (kind) {
                "CONTEO" -> if (result == 1) "1 estrella" else "$result estrellas"
                "PATRONES_ALGEBRA" -> "Sigue el $result"
                "GEOMETRIA" -> "$result lados"
                "MEDICION" -> "$result bloques más"
                else -> "$result votos"
            }
            Text(output, color = KMatGreen, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("subject-example-result"))
        }
    }
}

@Composable
private fun GeometryExample(sides: Int, active: Int, completed: Int) {
    Canvas(Modifier.size(145.dp).testTag("example-geometry")) {
        val x1 = size.width * 0.18f; val x2 = size.width * 0.82f
        val y1 = size.height * 0.18f; val y2 = size.height * 0.82f
        val points = if (sides == 3) listOf(Offset(size.width / 2, y1), Offset(x2, y2), Offset(x1, y2))
            else listOf(Offset(x1, y1), Offset(x2, y1), Offset(x2, y2), Offset(x1, y2))
        points.indices.forEach { index ->
            val color = if (index == active) Color(0xFFFFA000) else if (index < completed) KMatGreen else KMatInk.copy(alpha = 0.45f)
            drawLine(color, points[index], points[(index + 1) % points.size], strokeWidth = if (index == active) 12.dp.toPx() else 7.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun MeasurementExample(difference: Int, active: Int, completed: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("example-measurement")) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { index ->
                val extraIndex = index - (5 - difference)
                val color = when {
                    extraIndex == active && active >= 0 -> Color(0xFFFFB300)
                    extraIndex >= 0 && extraIndex < completed -> KMatGreen
                    else -> Color(0xFF70B6F3)
                }
                Box(Modifier.size(33.dp).clip(RoundedCornerShape(5.dp)).background(color).border(1.dp, Color.White, RoundedCornerShape(5.dp)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5 - difference) { Box(Modifier.size(33.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF9BD8A5)).border(1.dp, Color.White, RoundedCornerShape(5.dp))) }
        }
    }
}
