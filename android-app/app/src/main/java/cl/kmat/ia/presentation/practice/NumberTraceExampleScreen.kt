package cl.kmat.ia.presentation.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.kmat.ia.domain.usecase.NormalizedTracePoint
import cl.kmat.ia.domain.usecase.NumberTraceEvaluator
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun NumberTraceExampleScreen(number: Int, onBack: () -> Unit) {
    val segments = remember(number) { NumberTraceEvaluator.routeSegments(number) }
    val totalPoints = remember(segments) { segments.sumOf { it.size } }
    val context = LocalContext.current.applicationContext
    val narrationResource = remember(context) {
        context.resources.getIdentifier("trazado_ejemplo_gemini", "raw", context.packageName)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val voice = remember(context) { ExplanationVoice(context) }
    var shownPoints by remember(number) { mutableIntStateOf(0) }
    var replay by remember(number) { mutableIntStateOf(0) }
    var running by remember(number) { mutableStateOf(true) }
    var caption by remember(number) { mutableStateOf("Escucha y mira cómo se forma.") }
    BackHandler(onBack = onBack)

    LaunchedEffect(number, replay, running) {
        if (!running) return@LaunchedEffect
        shownPoints = 0
        caption = "Mira cómo trazamos este número."
        if (!running) return@LaunchedEffect
        caption = "Empieza en el punto amarillo y sigue los puntos en orden."
        coroutineScope {
            val animation = launch {
                val stepDelay = (3_600L / totalPoints.coerceAtLeast(1)).coerceAtLeast(18L)
                repeat(totalPoints) { index ->
                    delay(stepDelay)
                    shownPoints = index + 1
                }
            }
            if (narrationResource != 0) voice.play(narrationResource)
            animation.join()
        }
        if (!running) return@LaunchedEffect
        caption = "Si omites hasta dos, continúa. Retoma al trazar tres puntos seguidos."
    }

    DisposableEffect(voice, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                running = false
                voice.release()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); voice.release() }
    }

    KMatBackground {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("number-trace-example")
        ) {
            val compact = maxHeight < 600.dp || maxWidth < 360.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    KMatLogo(compact = true)
                    OutlinedButton(onClick = onBack) {
                        Text("Volver al ejercicio", color = KMatInk, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TalkingPracticePuppy(voice.isPlaying, Modifier.size(if (compact) 78.dp else 104.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Mira cómo trazar el número $number",
                            modifier = Modifier.fillMaxWidth(), color = KMatInk,
                            fontSize = if (compact) 20.sp else 25.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            caption,
                            modifier = Modifier.fillMaxWidth().testTag("number-trace-example-caption"), color = KMatInk,
                            fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp))
                        .background(Color.White).border(2.dp, Color(0xFFB8DDF6), RoundedCornerShape(24.dp))
                        .padding(8.dp)
                ) {
                    Canvas(Modifier.fillMaxSize().testTag("number-trace-example-pad")) {
                        var consumed = 0
                        segments.forEach { segment ->
                            val shownInSegment = (shownPoints - consumed).coerceIn(0, segment.size)
                            drawTraceSegment(segment, shownInSegment, size.width, size.height, 10.dp.toPx())
                            consumed += segment.size
                        }
                        var index = 0
                        segments.forEach { segment ->
                            segment.forEach { point ->
                                val position = NumberTraceEvaluator.canvasPoint(point, size.width, size.height)
                                val center = androidx.compose.ui.geometry.Offset(position.x, position.y)
                                val completed = index < shownPoints
                                drawCircle(
                                    color = when {
                                        completed -> Color(0xFF12A85A)
                                        index == 0 -> Color(0xFFFFB52E)
                                        else -> Color(0xFF70BDEB)
                                    },
                                    radius = if (completed || index == 0) 7.dp.toPx() else 5.dp.toPx(),
                                    center = center
                                )
                                index++
                            }
                        }
                    }
                }
                Text(
                    if (shownPoints >= totalPoints) "Los puntos omitidos no suman al avance. Los verdes ya se recorrieron."
                    else "El perrito te muestra el recorrido punto por punto.",
                    modifier = Modifier.fillMaxWidth(), color = KMatInk,
                    fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { running = true; replay++ }, modifier = Modifier.weight(1f)) {
                        Text("Ver explicación otra vez", color = KMatInk, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onBack, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White)
                    ) { Text("Intentarlo", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTraceSegment(
    points: List<NormalizedTracePoint>,
    visibleCount: Int,
    width: Float,
    height: Float,
    penWidth: Float
) {
    if (visibleCount <= 1) return
    val visible = points.take(visibleCount)
    val path = Path().apply {
        val first = NumberTraceEvaluator.canvasPoint(visible.first(), width, height)
        moveTo(first.x, first.y)
        visible.drop(1).forEach {
            val point = NumberTraceEvaluator.canvasPoint(it, width, height)
            lineTo(point.x, point.y)
        }
    }
    drawPath(path, KMatInk, style = Stroke(width = penWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
