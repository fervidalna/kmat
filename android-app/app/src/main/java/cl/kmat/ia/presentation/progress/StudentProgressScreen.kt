package cl.kmat.ia.presentation.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.usecase.GetStudentProgressUseCase
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StudentProgressScreen(
    getStudentProgress: GetStudentProgressUseCase,
    studentId: String,
    onBack: () -> Unit
) {
    var subjects by remember { mutableStateOf<List<StudentSubjectProgress>?>(null) }
    var selectedSubject by remember { mutableStateOf<StudentSubjectProgress?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(studentId) {
        runCatching { getStudentProgress(studentId) }
            .onSuccess { subjects = it }
            .onFailure { loadFailed = true }
    }

    val loadedSubjects = subjects
    KMatBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                selectedSubject?.let { "Progreso: ${it.subject}" } ?: "Mi progreso",
                color = KMatInk,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp
            )
            when {
                loadedSubjects == null && !loadFailed -> CircularProgressIndicator(color = KMatInk)
                loadFailed -> Text("No se pudo cargar el progreso. Revisa tu conexión e inténtalo de nuevo.", color = KMatInk)
                loadedSubjects == null || loadedSubjects.all { it.correct == 0 && it.incorrect == 0 } -> Text(
                    "Todavía no hay ejercicios resueltos. Cuando practiques, aquí verás tu progreso por materia.",
                    color = KMatInk,
                    fontSize = 17.sp
                )
                else -> {
                    val chart = selectedSubject
                    if (chart != null) {
                        SubjectHistoryChart(chart, Modifier.weight(1f).fillMaxWidth())
                    } else {
                        loadedSubjects.forEach { subject ->
                            SubjectCard(subject) { selectedSubject = subject }
                        }
                    }
                }
            }
            KMatPrimaryButton(
                if (selectedSubject == null) "Volver" else "Volver a materias",
                onClick = { if (selectedSubject == null) onBack() else selectedSubject = null },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SubjectCard(subject: StudentSubjectProgress, onShowChart: () -> Unit) {
    val averageSeconds = subject.averageResponseMillis / 1000.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(subject.subject, color = KMatInk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Correctas: ${subject.correct}", color = KMatInk, fontSize = 17.sp)
            Text("Incorrectas: ${subject.incorrect}", color = KMatInk, fontSize = 17.sp)
            Text("Tiempo promedio: ${"%.1f".format(averageSeconds)} s", color = KMatInk, fontSize = 17.sp)
            KMatSecondaryButton("Ver gráfico", onShowChart, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SubjectHistoryChart(subject: StudentSubjectProgress, modifier: Modifier = Modifier) {
    val history = subject.history
    val labels = remember(history) {
        val formatter = DateTimeFormatter.ofPattern("dd/MM")
        history.map {
            Instant.ofEpochMilli(it.dateEpochMillis)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        }
    }
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Eje izquierdo: respuestas · Eje derecho: segundos", color = KMatInk, fontSize = 14.sp)
        if (history.isEmpty()) {
            Text("Aún no hay datos para graficar esta materia.", color = KMatInk)
        } else {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                val left = 54.dp.toPx()
                val right = 50.dp.toPx()
                val top = 20.dp.toPx()
                val bottom = 38.dp.toPx()
                val chartWidth = (size.width - left - right).coerceAtLeast(1f)
                val chartHeight = (size.height - top - bottom).coerceAtLeast(1f)
                val maxResponses = (history.maxOf { maxOf(it.correct, it.incorrect) }).coerceAtLeast(1)
                val maxSeconds = history.maxOf { it.averageResponseMillis / 1000.0 }.coerceAtLeast(1.0)
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 12.dp.toPx()
                }
                val nativeCanvas = drawContext.canvas.nativeCanvas

                for (tick in 0..4) {
                    val fraction = tick / 4f
                    val y = top + chartHeight * (1f - fraction)
                    drawLine(Color.LightGray, androidx.compose.ui.geometry.Offset(left, y),
                        androidx.compose.ui.geometry.Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
                    nativeCanvas.drawText("${(maxResponses * fraction).toInt()}", left - 8.dp.toPx(), y + 4.dp.toPx(), textPaint.apply {
                        textAlign = android.graphics.Paint.Align.RIGHT
                    })
                    nativeCanvas.drawText("${"%.0f".format(maxSeconds * fraction)}s", size.width - right + 6.dp.toPx(), y + 4.dp.toPx(), textPaint.apply {
                        textAlign = android.graphics.Paint.Align.LEFT
                    })
                }

                fun xAt(index: Int): Float = left + if (history.size == 1) chartWidth / 2f
                    else chartWidth * index / (history.size - 1).toFloat()
                fun drawSeries(values: List<Float>, maxValue: Float, color: Color) {
                    val points = values.mapIndexed { index, value ->
                        androidx.compose.ui.geometry.Offset(
                            xAt(index),
                            top + chartHeight * (1f - (value / maxValue).coerceIn(0f, 1f))
                        )
                    }
                    points.zipWithNext().forEach { (start, end) -> drawLine(color, start, end, strokeWidth = 3.dp.toPx()) }
                    points.forEach { drawCircle(color, radius = 4.dp.toPx(), center = it) }
                }
                drawSeries(history.map { it.correct.toFloat() }, maxResponses.toFloat(), Color(0xFF00A951))
                drawSeries(history.map { it.incorrect.toFloat() }, maxResponses.toFloat(), Color(0xFFFF5C42))
                drawSeries(history.map { (it.averageResponseMillis / 1000.0).toFloat() }, maxSeconds.toFloat(), Color(0xFF0799E8))

                val labelIndexes = listOf(0, history.size / 2, history.lastIndex).distinct()
                labelIndexes.forEach { index ->
                    val x = xAt(index)
                    textPaint.textAlign = android.graphics.Paint.Align.CENTER
                    nativeCanvas.drawText(labels[index], x, size.height - 8.dp.toPx(), textPaint)
                }
            }
            Text("Fecha", Modifier.align(Alignment.CenterHorizontally), color = KMatInk, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ChartLegend("Correctas", Color(0xFF00A951))
                ChartLegend("Incorrectas", Color(0xFFFF5C42))
                ChartLegend("Promedio (s)", Color(0xFF0799E8))
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(10.dp).padding(1.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(color) }
        }
        Text(label, color = KMatInk, fontSize = 12.sp)
    }
}
