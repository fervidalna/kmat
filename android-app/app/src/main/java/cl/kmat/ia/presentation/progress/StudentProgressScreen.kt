package cl.kmat.ia.presentation.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.model.StudentProgressSnapshot
import cl.kmat.ia.domain.model.SubjectProgressPoint
import cl.kmat.ia.domain.model.SubjectSuggestionState
import cl.kmat.ia.domain.model.SuggestionResult
import cl.kmat.ia.domain.repository.TeachingRepository
import cl.kmat.ia.domain.usecase.GetStudentProgressUseCase
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val ProgressGreen = Color(0xFF00A951)
private val ProgressGrid = Color(0xFFDCE5EF)

@Composable
fun StudentProgressScreen(
    getStudentProgress: GetStudentProgressUseCase,
    studentId: String,
    studentName: String,
    selectedGrade: String,
    onBack: () -> Unit,
    syncProgress: (suspend (String) -> Boolean)? = null,
    initialSnapshot: StudentProgressSnapshot? = null,
    onSnapshotUpdated: ((StudentProgressSnapshot) -> Unit)? = null
) {
    SharedStudentProgressScreen(
        studentId, studentName, selectedGrade, onBack, syncProgress,
        initialSnapshot = initialSnapshot,
        onSnapshotUpdated = onSnapshotUpdated
    ) { id ->
        val subjects = getStudentProgress(id)
        val diagnostic = runCatching { getStudentProgress.getDiagnosticReport(id)?.overallLevel }.getOrNull()
        StudentProgressSnapshot(subjects, diagnostic)
    }
}

@Composable
internal fun SharedStudentProgressScreen(
    studentId: String,
    studentName: String,
    selectedGrade: String,
    onBack: () -> Unit,
    syncProgress: (suspend (String) -> Boolean)? = null,
    onAssignSubject: ((String, String) -> Unit)? = null,
    suggestionRepository: TeachingRepository? = null,
    notice: String? = null,
    initialSnapshot: StudentProgressSnapshot? = null,
    onSnapshotUpdated: ((StudentProgressSnapshot) -> Unit)? = null,
    loadProgress: suspend (String) -> StudentProgressSnapshot
) {
    var subjects by remember(studentId) { mutableStateOf(initialSnapshot?.subjects) }
    var loadFailed by remember(studentId) { mutableStateOf(false) }
    var diagnosticLevel by remember(studentId) { mutableStateOf(initialSnapshot?.diagnosticLevel) }
    var syncing by remember(studentId) { mutableStateOf(false) }
    var syncMessage by remember(studentId) { mutableStateOf<String?>(null) }
    var suggestion by remember(studentId) { mutableStateOf<SubjectSuggestionState?>(null) }
    var generating by remember(studentId) { mutableStateOf(false) }
    var generationError by remember(studentId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingPdf by remember { mutableStateOf<ProgressPdfReport?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val report = pendingPdf
        if (uri != null && report != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { ProgressPdfExporter.write(it, report) }
                    ?: error("No se pudo abrir el archivo seleccionado.")
            }.onSuccess { exportMessage = "Reporte PDF guardado." }
                .onFailure { exportMessage = "No se pudo guardar el PDF." }
        }
        pendingPdf = null
    }
    LaunchedEffect(studentId, initialSnapshot) {
        if (subjects == null && initialSnapshot != null) {
            subjects = initialSnapshot.subjects
            diagnosticLevel = initialSnapshot.diagnosticLevel
            loadFailed = false
        }
    }
    LaunchedEffect(studentId) {
        try {
            val localSnapshot = loadProgress(studentId)
            subjects = localSnapshot.subjects
            diagnosticLevel = localSnapshot.diagnosticLevel
            loadFailed = false
            onSnapshotUpdated?.invoke(localSnapshot)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            loadFailed = true
            return@LaunchedEffect
        }

        // Keep the local report visible while remote changes are fetched and merged.
        if (syncProgress != null) {
            try {
                if (syncProgress(studentId)) {
                    val latestSnapshot = loadProgress(studentId)
                    subjects = latestSnapshot.subjects
                    diagnosticLevel = latestSnapshot.diagnosticLevel
                    onSnapshotUpdated?.invoke(latestSnapshot)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                // The last locally available snapshot remains on screen.
            }
        }
    }
    val loadedSubjects = subjects
    val horizontalPadding = if (LocalConfiguration.current.screenWidthDp < 600) 16.dp else 24.dp
    val syncAction: (() -> Unit)? = syncProgress?.let { synchronize ->
        {
            syncing = true
            syncMessage = null
            scope.launch {
                try {
                    if (synchronize(studentId)) {
                        val updated = loadProgress(studentId)
                        subjects = updated.subjects
                        diagnosticLevel = updated.diagnosticLevel
                        onSnapshotUpdated?.invoke(updated)
                        syncMessage = "Progreso sincronizado."
                    } else {
                        syncMessage = "No se pudo sincronizar. Revisa tu conexión e inténtalo nuevamente."
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    syncMessage = "No se pudo sincronizar. Revisa tu conexión e inténtalo nuevamente."
                } finally {
                    syncing = false
                }
            }
        }
    }
    val exportAction: () -> Unit = {
        loadedSubjects?.let { reportSubjects ->
            val daily = reportSubjects.flatMap { it.history }.groupBy { it.dateEpochMillis }.toSortedMap()
            val trend = daily.map { (date, points) ->
                ProgressPdfTrendPoint(formatDate(date), points.sumOf { it.correct }, points.sumOf { it.correct + it.incorrect })
            }
            val history = reportSubjects.flatMap { subject ->
                subject.history.map { point ->
                    val total = point.correct + point.incorrect
                    ProgressPdfHistoryRow(
                        date = formatDate(point.dateEpochMillis), area = subject.subject,
                        activity = "Resumen diario",
                        result = if (total == 0) "Sin respuestas" else "${point.correct} de $total correctos",
                        durationMillis = point.averageResponseMillis.takeIf { total > 0 }
                    )
                }
            }.sortedBy { it.date }
            pendingPdf = ProgressPdfReport(
                studentName = studentName,
                selectedGrade = selectedGrade.toDisplayGrade(),
                performanceLevel = diagnosticLevel.toPerformanceLabel(),
                areas = reportSubjects.map { subject ->
                    val level = subject.mastery?.currentLevel?.let {
                        when (it) { 1 -> "Inicial"; 2 -> "Medio"; else -> "Alto" }
                    }
                    ProgressPdfArea(subject.subject, subject.correct, subject.correct + subject.incorrect, level)
                },
                trend = trend, history = history
            )
            pdfLauncher.launch("reporte-progreso-${studentName.fileSafeName()}.pdf")
        }
    }
    KMatBackground {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier,
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Mi progreso", color = KMatInk, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                    Text(studentName, color = KMatInk.copy(alpha = .78f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (suggestionRepository != null) {
                ProgressCard {
                    SuggestionContent(suggestion, generating, generationError) {
                        if (!generating) {
                            generating = true
                            generationError = null
                            scope.launch {
                                try {
                                    when (val result = suggestionRepository.generateReinforcementSuggestion(studentId)) {
                                        is SuggestionResult.Success -> {
                                            val general = result.subjects.singleOrNull { it.axis == "GLOBAL" }
                                            if (general != null) suggestion = general
                                            else generationError = "No se recibió la sugerencia general."
                                        }
                                        else -> generationError = result.suggestionMessage()
                                    }
                                } finally { generating = false }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                syncAction?.let { action ->
                    OutlinedButton(
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                        onClick = action,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = .96f), contentColor = KMatInk
                        )
                    ) {
                        Text(if (syncing) "Sincronizando" else "Sincronizar", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
                if (loadedSubjects != null) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = exportAction,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = .96f), contentColor = KMatInk
                        )
                    ) {
                        Text("Exportar PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onBack,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFDDF1FF), contentColor = KMatInk
                    )
                ) { Text("Volver", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center) }
            }
            exportMessage?.let { Text(it, color = KMatInk, fontSize = 13.sp, textAlign = TextAlign.Center) }
            syncMessage?.let { Text(it, color = KMatInk, fontSize = 13.sp, textAlign = TextAlign.Center) }
            notice?.let { Text(it, color = KMatInk, fontSize = 14.sp, textAlign = TextAlign.Center) }
            when {
                loadedSubjects == null && !loadFailed -> CircularProgressIndicator(color = KMatInk)
                loadFailed -> Text("No se pudo cargar el progreso. Revisa tu conexión e inténtalo de nuevo.", color = KMatInk)
                loadedSubjects == null -> Text(
                    "Aún no hay ejercicios resueltos. Cuando practiques, aquí verás tu avance por contenido y sesión.",
                    color = KMatInk, fontSize = 17.sp, textAlign = TextAlign.Center
                )
                else -> {

                    ProgressOverview(loadedSubjects)
                    loadedSubjects.forEach { subject ->
                        val axis = DiagnosticAssessmentEngine.AXIS_LABELS.entries.firstOrNull { it.value == subject.subject }?.key
                        SubjectProgressCard(subject, onAssign = if (axis != null && onAssignSubject != null) {
                            { onAssignSubject(axis, subject.subject) }
                        } else null)
                    }
                    Text("El porcentaje muestra aciertos sobre ejercicios respondidos.",
                        color = KMatInk.copy(alpha = .75f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun ProgressOverview(subjects: List<StudentSubjectProgress>) {
    val correct = subjects.sumOf { it.correct }
    val total = subjects.sumOf { it.correct + it.incorrect }
    val percent = if (total == 0) 0 else (correct * 100f / total).toInt()
    ProgressCard {
        Text("Avance general", color = KMatInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (total == 0) "—" else "$percent%", color = KMatInk, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
            Column {
                Text("$correct de $total ejercicios correctos", color = KMatInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${subjects.count { it.correct + it.incorrect > 0 }} de ${subjects.size} apartados con ejercicios", color = KMatInk.copy(alpha = .72f), fontSize = 14.sp)
            }
        }
        AccuracyBar(if (total == 0) 0f else percent / 100f)
    }
}

@Composable
internal fun SubjectProgressCard(subject: StudentSubjectProgress, showLatestSession: Boolean = true, onAssign: (() -> Unit)? = null) {
    var showLatest by remember(subject.subject) { mutableStateOf(false) }
    val total = subject.correct + subject.incorrect
    val percent = if (total == 0) null else (subject.correct * 100f / total).toInt()
    val recent = subject.history.takeLast(4)
    ProgressCard {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(subject.subject, color = KMatInk, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Text(percent?.let { "$it%" } ?: "—", color = KMatInk, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        }
        AccuracyBar(if (percent == null) 0f else percent / 100f)
        Text(if (total == 0) "Sin ejercicios realizados" else "${subject.correct} de $total ejercicios correctos", color = KMatInk, fontSize = 15.sp)
        subject.mastery?.let { mastery ->
            val diagnosticLabel = when (mastery.diagnosticLevel) { 1 -> "Inicial"; 2 -> "Medio"; else -> "Alto" }
            val currentLabel = when (mastery.currentLevel) { 1 -> "Inicial"; 2 -> "Medio"; else -> "Alto" }
            Text("Diagnóstico: $diagnosticLabel · Nivel actual: $currentLabel", color = KMatInk, fontWeight = FontWeight.SemiBold)
            if (mastery.currentLevel < 3) {
                Text("Práctica mínima: ${minOf(mastery.attemptsAtLevel, 20)} de 20 respuestas", color = KMatInk)
                Text("Aciertos recientes: ${mastery.recentCorrect} de ${mastery.recentCount} · Para avanzar: 8 de 10", color = KMatInk)
            } else {
                Text("Nivel alto · ${mastery.attemptsAtLevel} respuestas de práctica", color = KMatInk)
            }
        }
        Text("Aciertos por día", color = KMatInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (recent.isNotEmpty()) {
            AccuracyTrendChart(recent, Modifier.fillMaxWidth().height(150.dp))
            recent.forEachIndexed { index, point ->
                val sessionTotal = point.correct + point.incorrect
                val sessionPercent = if (sessionTotal == 0) 0 else (point.correct * 100f / sessionTotal).toInt()
                Text(
                    "${formatDate(point.dateEpochMillis)}  ·  ${point.correct}/$sessionTotal (${sessionPercent}%)",
                    color = KMatInk.copy(alpha = .82f), fontSize = 13.sp
                )
            }
        } else Text("Sin ejercicios para graficar todavía.", color = KMatInk.copy(alpha = .72f))
        if (total in 1..4) Text("Pocos ejercicios registrados: el porcentaje aún puede variar bastante.",
            color = KMatInk.copy(alpha = .72f), fontSize = 12.sp)
        if (showLatestSession) TextButton(onClick = { showLatest = !showLatest }) {
            Text(if (showLatest) "Ocultar última sesión ▴" else "Ver última sesión ▾")
        }
        if (showLatestSession && showLatest) {
            val latest = subject.latestPractice
            if (latest == null) {
                Text("No hubo ejercicios de esta materia en la última sesión completada.", color = KMatInk)
            } else {
                Text("Última sesión: ${latest.correct} de ${latest.total} correctos", color = KMatInk, fontWeight = FontWeight.SemiBold)
                Text("Tiempo promedio: ${latest.averageResponseMillis / 1000} s · Esperado: ${latest.averageExpectedMillis / 1000} s", color = KMatInk)
            }
        }
        if (onAssign != null) {
            OutlinedButton(onClick = onAssign, modifier = Modifier.fillMaxWidth()) { Text("Asignar trabajo") }
        }
    }
}

@Composable
private fun SuggestionContent(state: SubjectSuggestionState?, generating: Boolean, error: String?, onGenerate: () -> Unit) {
    Text("Sugerencia general del estudiante", color = KMatInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val saved = state?.suggestion
    if (saved != null) {
        Text(saved.topic, color = KMatInk, fontWeight = FontWeight.SemiBold)
        Text(saved.activity, color = KMatInk)
        Text("Por qué: ${saved.evidence}", color = KMatInk)
        runCatching { Instant.parse(saved.generatedAt).toEpochMilli() }.getOrNull()?.let {
            Text("Generada: ${formatDate(it)}", color = KMatInk.copy(alpha = .7f), fontSize = 12.sp)
        }
        Text("Sugerencia generada con IA; el docente decide si asigna trabajo.", color = KMatInk.copy(alpha = .7f), fontSize = 12.sp)
    }
    if (state != null && state.practiceCount < state.minimum) {
        Text("Hay ${state.practiceCount} de ${state.minimum} respuestas de práctica necesarias para sugerir un ejercicio.",
            color = KMatInk.copy(alpha = .75f), fontSize = 13.sp)
    }
    error?.let { Text(it, color = Color(0xFF9B2525), fontSize = 13.sp) }
    Text("Se enviará a Gemini un resumen de resultados, sin nombre ni respuestas escritas.",
        color = KMatInk.copy(alpha = .7f), fontSize = 12.sp)
    OutlinedButton(enabled = !generating, onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
        Text(if (generating) "Generando sugerencia…" else if (saved == null) "Generar sugerencia" else "Generar otra sugerencia")
    }
}

private fun SuggestionResult.suggestionMessage(): String = when (this) {
    is SuggestionResult.Failure -> detail
    SuggestionResult.Unauthorized -> "No se pudo verificar el acceso al estudiante. Vuelve a iniciar sesión."
    SuggestionResult.Busy -> "Ya se está generando una sugerencia para este estudiante. Inténtalo en un momento."
    SuggestionResult.QuotaExceeded -> "Gemini alcanzó su límite de uso. Inténtalo más tarde."
    SuggestionResult.NotConfigured -> "El servicio de sugerencias todavía no está disponible."
    else -> "No se pudo consultar la sugerencia. Revisa la conexión e inténtalo de nuevo."
}

@Composable
private fun ProgressCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f)),
        shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun AccuracyBar(fraction: Float) {
    Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp))) {
        drawRoundRect(ProgressGrid)
        drawRoundRect(
            color = ProgressGreen,
            size = androidx.compose.ui.geometry.Size(size.width * fraction.coerceIn(0f, 1f), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        )
    }
}

@Composable
private fun AccuracyTrendChart(points: List<SubjectProgressPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val left = 38.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        val chartWidth = (right - left).coerceAtLeast(1f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val native = drawContext.canvas.nativeCanvas
        listOf(0, 25, 50, 75, 100).forEach { tick ->
            val y = bottom - chartHeight * tick / 100f
            drawLine(ProgressGrid, androidx.compose.ui.geometry.Offset(left, y),
                androidx.compose.ui.geometry.Offset(right, y), 1.dp.toPx())
            native.drawText("$tick%", left - 5.dp.toPx(), y + 4.dp.toPx(), paint)
        }
        fun xAt(index: Int) = left + if (points.size == 1) chartWidth / 2 else chartWidth * index / (points.size - 1)
        val positions = points.mapIndexed { index, point ->
            val total = point.correct + point.incorrect
            val rate = if (total == 0) 0f else point.correct.toFloat() / total
            androidx.compose.ui.geometry.Offset(xAt(index), bottom - chartHeight * rate)
        }
        val path = Path().apply {
            positions.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }
        if (positions.size > 1) drawPath(path, ProgressGreen, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        positions.forEach { drawCircle(ProgressGreen, radius = 5.dp.toPx(), center = it) }
        val formatter = DateTimeFormatter.ofPattern("dd/MM")
        listOf(0, points.lastIndex).distinct().forEach { index ->
            val label = Instant.ofEpochMilli(points[index].dateEpochMillis).atZone(ZoneId.systemDefault()).format(formatter)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            native.drawText(label, xAt(index), size.height - 4.dp.toPx(), paint)
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM"))
private fun Int?.toPerformanceLabel(): String = when (this) {
    3 -> "Avanzado"
    2 -> "En desarrollo"
    1 -> "Nivel inicial"
    else -> "Sin diagnóstico registrado"
}

private fun String.toDisplayGrade(): String = when (this) {
    "BASICO_1" -> "1° Básico"
    "BASICO_2" -> "2° Básico"
    else -> this
}

private fun String.fileSafeName(): String =
    lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "estudiante" }
