package cl.kmat.ia.presentation.teacher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.domain.model.ExerciseHistoryEntry
import cl.kmat.ia.domain.model.ReinforcementOption
import cl.kmat.ia.domain.model.StudentReport
import cl.kmat.ia.domain.model.SessionProgressPoint
import cl.kmat.ia.domain.model.TeachingGroup
import cl.kmat.ia.domain.model.TeachingResult
import cl.kmat.ia.domain.model.TeachingStudent
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.model.SubjectProgressPoint
import cl.kmat.ia.domain.repository.TeachingRepository
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import cl.kmat.ia.presentation.progress.ProgressPdfArea
import cl.kmat.ia.presentation.progress.ProgressPdfExporter
import cl.kmat.ia.presentation.progress.ProgressPdfHistoryRow
import cl.kmat.ia.presentation.progress.ProgressPdfReport
import cl.kmat.ia.presentation.progress.ProgressPdfTrendPoint
import cl.kmat.ia.presentation.progress.ProgressOverview
import cl.kmat.ia.presentation.progress.SubjectProgressCard
import cl.kmat.ia.presentation.progress.SharedStudentProgressScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TeacherWorkspaceScreen(repository: TeachingRepository, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var groups by remember { mutableStateOf<List<TeachingGroup>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<TeachingGroup?>(null) }
    var students by remember { mutableStateOf<List<TeachingStudent>>(emptyList()) }
    var openGroupRequest by remember { mutableStateOf(0) }
    val studentsRequester = remember { BringIntoViewRequester() }
    var options by remember { mutableStateOf<List<ReinforcementOption>>(emptyList()) }
    var report by remember { mutableStateOf<StudentReport?>(null) }
    var progressStudent by remember { mutableStateOf<TeachingStudent?>(null) }
    var assignmentStudent by remember { mutableStateOf<TeachingStudent?>(null) }
    var assignmentAxis by remember { mutableStateOf("") }
    var assignmentSubject by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf<Int?>(null) }
    var loadingOptions by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<ReinforcementOption?>(null) }
    var groupName by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var pdfExportReport by remember { mutableStateOf<ProgressPdfReport?>(null) }
    var pendingDeleteGroup by remember { mutableStateOf<TeachingGroup?>(null) }
    var deletingGroup by remember { mutableStateOf(false) }

    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val reportData = pdfExportReport
        if (uri != null && reportData != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ProgressPdfExporter.write(it, reportData) }
                ?: error("No se pudo abrir el archivo seleccionado.")
        }.onSuccess { message = "Reporte PDF guardado correctamente." }
            .onFailure { error = "No se pudo guardar el reporte PDF." }
    }
    fun loadGroups() {
        scope.launch {
            loading = true
            error = null
            when (val result = repository.listGroups()) {
                is TeachingResult.Success -> {
                    groups = result.value
                    selectedGroup = selectedGroup?.let { current -> result.value.firstOrNull { it.id == current.id } }
                }
                else -> error = result.message()
            }
            loading = false
        }
    }

    fun openGroup(group: TeachingGroup) {
        selectedGroup = group
        openGroupRequest++
        students = emptyList()
        scope.launch {
            loading = true
            error = null
            when (val result = repository.listStudents(group.id)) {
                is TeachingResult.Success -> students = result.value
                else -> error = result.message()
            }
            loading = false
        }
    }

    LaunchedEffect(repository) { loadGroups() }

    LaunchedEffect(openGroupRequest) {
        if (openGroupRequest > 0 && selectedGroup != null) {
            withFrameNanos { }
            studentsRequester.bringIntoView()
        }
    }

    pendingDeleteGroup?.let { group -> AlertDialog(
        onDismissRequest = { if (!deletingGroup) pendingDeleteGroup = null },
        title = { Text("¿Eliminar sala?") },
        text = { Text("Se eliminará “${group.name}” del espacio docente y los estudiantes ya no podrán unirse con su código. El historial académico se conservará.") },
        confirmButton = {
            TextButton(enabled = !deletingGroup, onClick = {
                deletingGroup = true
                scope.launch {
                    when (val result = repository.deleteGroup(group.id)) {
                        is TeachingResult.Success -> {
                            groups = groups.filterNot { it.id == group.id }
                            if (selectedGroup?.id == group.id) {
                                selectedGroup = null
                                students = emptyList()
                            }
                            message = "Sala eliminada. El historial académico se conservó."
                            pendingDeleteGroup = null
                        }
                        else -> error = result.message()
                    }
                    deletingGroup = false
                }
            }) { Text(if (deletingGroup) "Eliminando…" else "Eliminar", color = Color(0xFFB3261E)) }
        },
        dismissButton = { TextButton(enabled = !deletingGroup, onClick = { pendingDeleteGroup = null }) { Text("Cancelar") } }
    ) }

    if (showCreate) AlertDialog(
        onDismissRequest = { if (!saving) showCreate = false },
        title = { Text("Crear sala o grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Elige un nombre para organizar a tus estudiantes.")
                OutlinedTextField(groupName, { groupName = it.take(80); dialogError = null }, label = { Text("Nombre del grupo") }, singleLine = true, enabled = !saving)
                dialogError?.let { Text(it, color = Color(0xFF9B2525)) }
            }
        },
        confirmButton = { TextButton(enabled = !saving && groupName.trim().isNotEmpty(), onClick = {
            saving = true
            scope.launch {
                when (val result = repository.createGroup(groupName)) {
                    is TeachingResult.Success -> {
                        groups = (groups + result.value).distinctBy { it.id }
                        groupName = ""
                        showCreate = false
                        message = "Sala creada. Comparte su código de unión con las familias."
                        openGroup(result.value)
                    }
                    else -> dialogError = result.message()
                }
                saving = false
            }
        }) { Text(if (saving) "Creando…" else "Crear sala") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { showCreate = false }) { Text("Cancelar") } }
    )

    assignmentStudent?.let { student -> AlertDialog(
        onDismissRequest = { if (!saving) assignmentStudent = null },
        title = { Text("Asignar trabajo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estudiante: ${student.name}")
                Text("Materia: $assignmentSubject")
                Text("Nivel")
                listOf("Inicial", "Medio", "Alto").forEachIndexed { index, label ->
                    OutlinedButton(onClick = { selectedDifficulty = index; selectedOption = null; dialogError = null },
                        modifier = Modifier.fillMaxWidth(), enabled = !saving,
                        border = BorderStroke(1.dp, if (selectedDifficulty == index) KMatInk else Color.LightGray)) {
                        Text(if (selectedDifficulty == index) "✓ $label" else label)
                    }
                }
                if (loadingOptions) Text("Cargando ejercicios…")
                val matching = options.filter { it.axis == assignmentAxis && it.difficulty == selectedDifficulty }
                if (!loadingOptions && selectedDifficulty != null && matching.isEmpty()) Text("No hay ejercicios publicados para esta materia y nivel.")
                matching.forEach { option ->
                    OutlinedButton(
                        onClick = { selectedOption = option; dialogError = null },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (selectedOption?.contentId == option.contentId) KMatInk else Color.LightGray)
                    ) { Text("${if (selectedOption?.contentId == option.contentId) "✓ " else ""}${option.name} · ${option.availableExercises} ejercicios") }
                }
                OutlinedTextField(countText, { countText = it.filter(Char::isDigit).take(2) }, label = { Text("Cantidad de ejercicios") }, singleLine = true)
                OutlinedTextField(note, { note = it.take(250) }, label = { Text("Nota opcional") })
                dialogError?.let { Text(it, color = Color(0xFF9B2525)) }
            }
        },
        confirmButton = { TextButton(enabled = !saving && selectedOption != null, onClick = {
            val option = selectedOption ?: return@TextButton
            val count = countText.toIntOrNull() ?: 0
            val maxCount = minOf(option.availableExercises, 20)
            if (count !in 1..maxCount) {
                dialogError = "Indica entre 1 y $maxCount ejercicios disponibles."
            } else {
                saving = true
                scope.launch {
                    when (val result = repository.assignReinforcementByLevel(student.id, option, count, note)) {
                        is TeachingResult.Success -> {
                            message = "Se asignaron ${result.value} ejercicios a ${student.name}."
                            assignmentStudent = null
                            note = ""
                        }
                        else -> dialogError = result.message()
                    }
                    saving = false
                }
            }
        }) { Text(if (saving) "Asignando…" else "Asignar") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { assignmentStudent = null }) { Text("Cancelar") } }
    ) }

    progressStudent?.let { student ->
        SharedStudentProgressScreen(student.id, student.name, student.educationLevel, onBack = { progressStudent = null },
            onAssignSubject = { axis, subject ->
                assignmentStudent = student
                assignmentAxis = axis
                assignmentSubject = subject
                selectedDifficulty = null
                selectedOption = null
                countText = "1"
                note = ""
                dialogError = null
                message = null
                options = emptyList()
                loadingOptions = true
                scope.launch {
                    when (val result = repository.listReinforcementOptionsByLevel()) {
                        is TeachingResult.Success -> options = result.value
                        else -> dialogError = result.message()
                    }
                    loadingOptions = false
                }
            }, suggestionRepository = repository, notice = message) { id ->
            when (val result = repository.getStudentProgress(id)) {
                is TeachingResult.Success -> result.value
                else -> throw IllegalStateException(result.message())
            }
        }
        return
    }

    report?.let { data -> AlertDialog(
        onDismissRequest = { report = null },
        title = { Text("Progreso de ${data.studentName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val subjects = data.toSubjectProgress()
                ProgressOverview(subjects)
                subjects.forEach { SubjectProgressCard(it, showLatestSession = false) }
                students.firstOrNull { it.id == data.studentId }?.let { student ->
                    Button(onClick = {
                        report = null
                        assignmentStudent = student
                        selectedOption = null
                        countText = "1"
                        dialogError = null
                        scope.launch {
                            when (val result = repository.listReinforcementOptions()) {
                                is TeachingResult.Success -> {
                                    options = result.value
                                    if (result.value.isEmpty()) dialogError = "No hay ejercicios publicados para asignar."
                                }
                                else -> dialogError = result.message()
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Asignar refuerzo") }
                }
                HorizontalDivider()
                Text("Historial de ejercicios", fontWeight = FontWeight.Bold)
                if (data.history.isEmpty()) Text("Todavía no hay ejercicios registrados.")
                data.history.forEach { HistoryRow(it) }
                OutlinedButton(onClick = {
                    pdfExportReport = data.toProgressPdfReport()
                    pdfExportLauncher.launch("reporte-progreso-${fileSafe(data.studentName)}.pdf")
                }, modifier = Modifier.fillMaxWidth()) { Text("Exportar reporte PDF") }
            }
        },
        confirmButton = { TextButton(onClick = { report = null }) { Text("Cerrar") } }
    ) }

    KMatBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val innerWidth = (maxWidth - 40.dp).coerceAtLeast(0.dp).coerceAtMost(1_200.dp)
            val columns = if (innerWidth >= 640.dp) 2 else 1
            val tileWidth = (innerWidth - 16.dp * (columns - 1)) / columns
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)
                    .widthIn(max = 1_200.dp).align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Espacio docente", modifier = Modifier.weight(1f), color = KMatInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(2.dp, KMatInk),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = KMatInk
                        )
                    ) { Text("Cerrar sesión", fontWeight = FontWeight.Bold) }
                }
                Text("Organiza tus salas, acompaña el progreso y asigna actividades de refuerzo.", color = KMatInk, textAlign = TextAlign.Center)
                message?.let { StatusMessage(it, Color(0xFF176B3A)) }
                error?.let { StatusMessage(it, Color(0xFF9B2525)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Mis salas", color = KMatInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    KMatPrimaryButton("Crear sala", { groupName = ""; dialogError = null; showCreate = true })
                }
                if (loading && groups.isEmpty()) CircularProgressIndicator(color = KMatInk)
                if (!loading && groups.isEmpty() && error == null) Text("Aún no tienes salas. Crea una para organizar a tus estudiantes.", color = KMatInk, textAlign = TextAlign.Center)
                FlowRow(
                    modifier = Modifier.width(innerWidth),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groups.forEach { group ->
                        GroupCard(group, selectedGroup?.id == group.id, Modifier.width(tileWidth),
                            onOpen = { openGroup(group) },
                            onDelete = { pendingDeleteGroup = group },
                            onCopy = { clipboard.setText(AnnotatedString(group.joinCode)); message = "Código copiado: ${group.joinCode}" }
                        )
                    }
                }
                selectedGroup?.let { group ->
                    Row(Modifier.fillMaxWidth().bringIntoViewRequester(studentsRequester), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(group.name, color = KMatInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${students.size} estudiantes vinculados", color = KMatInk)
                        }
                        KMatSecondaryButton("Actualizar", { openGroup(group) })
                    }
                    if (loading) Text("Cargando estudiantes…", color = KMatInk)
                    if (students.isEmpty() && !loading && error == null) Text("Comparte el código ${group.joinCode} con las familias para que vinculen a sus estudiantes.", color = KMatInk, textAlign = TextAlign.Center)
                    if (error != null) Text(error.orEmpty(), color = Color(0xFF9B2525), textAlign = TextAlign.Center)
                    students.forEach { student -> StudentCard(student, onProgress = {
                        progressStudent = student
                    }) }
                }
                KMatSecondaryButton("Actualizar salas", ::loadGroups, Modifier.widthIn(max = 440.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TeacherProgressSummary(data: StudentReport) {
    val total = data.correct + data.incorrect
    val percent = if (total == 0) 0 else (data.correct * 100f / total).toInt()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Resumen del avance", color = KMatInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Curso seleccionado: ${data.educationLevel.toDisplayGrade()}  ·  ${data.sessions} sesiones", color = KMatInk.copy(alpha = .75f))
        Text("Nivel de desempeño: ${data.performanceLevel ?: "Sin diagnóstico registrado"}", color = KMatInk.copy(alpha = .75f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeacherMetric("${percent}%", "aciertos", Modifier.weight(1f))
            TeacherMetric("${data.correct}/$total", "ejercicios", Modifier.weight(1f))
            TeacherMetric("${data.sessions}", "sesiones", Modifier.weight(1f))
        }
        TeacherAccuracyBar(percent / 100f)
        if (total < 5) Text("Hay pocos ejercicios registrados para interpretar una tendencia.", color = KMatInk.copy(alpha = .7f), fontSize = 12.sp)
    }
}

private fun StudentReport.toSubjectProgress(): List<StudentSubjectProgress> =
    DiagnosticAssessmentEngine.AXES.map { axis ->
        val entries = history.filter { it.area == axis && it.correct != null }
        val daily = entries.mapNotNull { entry ->
            val instant = runCatching { Instant.parse(entry.date) }.getOrNull() ?: return@mapNotNull null
            val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            date to entry
        }.groupBy({ it.first }, { it.second })
        StudentSubjectProgress(
            subject = DiagnosticAssessmentEngine.AXIS_LABELS.getValue(axis),
            correct = entries.count { it.correct == true },
            incorrect = entries.count { it.correct == false },
            averageResponseMillis = if (entries.isEmpty()) 0 else entries.map { it.durationMillis }.average().toLong(),
            history = daily.map { (date, rows) ->
                SubjectProgressPoint(
                    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    rows.count { it.correct == true }, rows.count { it.correct == false },
                    rows.map { it.durationMillis }.average().toLong()
                )
            }.sortedBy { it.dateEpochMillis }
        )
    }

@Composable
private fun TeacherMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF0F6FC)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = KMatInk, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(label, color = KMatInk.copy(alpha = .72f), fontSize = 12.sp)
    }
}

@Composable
private fun ContentBreakdown(history: List<ExerciseHistoryEntry>) {
    val areas = listOf(
        "NUMEROS_OPERACIONES" to "Números y operaciones",
        "PATRONES_ALGEBRA" to "Patrones y álgebra",
        "GEOMETRIA" to "Geometría",
        "MEDICION" to "Medición",
        "DATOS_PROBABILIDAD" to "Datos y probabilidades"
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("Avance por materia", color = KMatInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        areas.forEach { (code, label) ->
            val attempts = history.filter { it.area == code && it.correct != null }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (attempts.isEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = KMatInk, fontWeight = FontWeight.SemiBold)
                        Text("Sin ejercicios", color = KMatInk.copy(alpha = .65f), fontSize = 13.sp)
                    }
                    TeacherAccuracyBar(0f)
                } else {
                    val correct = attempts.count { it.correct == true }
                    val percent = (correct * 100f / attempts.size).toInt()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = KMatInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("$percent% · $correct de ${attempts.size}", color = KMatInk, fontWeight = FontWeight.Bold)
                    }
                    TeacherAccuracyBar(percent / 100f)
                }
            }
        }
    }
}
@Composable
private fun TeacherAccuracyBar(fraction: Float) {
    Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp))) {
        drawRoundRect(Color(0xFFDCE5EF))
        drawRoundRect(
            color = Color(0xFF00A951),
            size = androidx.compose.ui.geometry.Size(size.width * fraction.coerceIn(0f, 1f), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        )
    }
}
@Composable
private fun SessionProgressChart(points: List<SessionProgressPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Porcentaje de ejercicios correctos por sesión", color = KMatInk.copy(alpha = .75f), fontSize = 13.sp)
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 8.dp, vertical = 12.dp)) {
            if (points.isEmpty()) return@Canvas
            val left = 42.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
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
                drawLine(Color(0xFFDCE5EF), androidx.compose.ui.geometry.Offset(left, y),
                    androidx.compose.ui.geometry.Offset(right, y), 1.dp.toPx())
                native.drawText("$tick%", left - 5.dp.toPx(), y + 4.dp.toPx(), paint)
            }
            fun xAt(index: Int) = left + if (points.size == 1) chartWidth / 2 else chartWidth * index / (points.size - 1)
            val positions = points.mapIndexed { index, point ->
                val y = bottom - chartHeight * (point.percentage / 100f).coerceIn(0f, 1f)
                androidx.compose.ui.geometry.Offset(xAt(index), y)
            }
            val path = Path().apply {
                positions.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
            }
            if (positions.size > 1) drawPath(path, Color(0xFF00A951), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            positions.forEach { drawCircle(Color(0xFF00A951), radius = 5.dp.toPx(), center = it) }
        }
    }
}
@Composable
private fun GroupCard(group: TeachingGroup, selected: Boolean, modifier: Modifier, onOpen: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(Color.White.copy(alpha = .96f)),
        border = BorderStroke(2.dp, if (selected) KMatInk else Color(0xFFD7E7F7)), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(group.name, Modifier.weight(1f), color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDelete, modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFFFE8E6)).semantics { contentDescription = "Eliminar sala ${group.name}" }) {
                    Canvas(Modifier.size(25.dp)) {
                        val red = Color(0xFFC62828)
                        val stroke = 2.4.dp.toPx()
                        drawLine(red, androidx.compose.ui.geometry.Offset(size.width * .28f, size.height * .30f), androidx.compose.ui.geometry.Offset(size.width * .72f, size.height * .30f), stroke, cap = StrokeCap.Round)
                        drawLine(red, androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .19f), androidx.compose.ui.geometry.Offset(size.width * .58f, size.height * .19f), stroke, cap = StrokeCap.Round)
                        drawLine(red, androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .36f), androidx.compose.ui.geometry.Offset(size.width * .40f, size.height * .82f), stroke, cap = StrokeCap.Round)
                        drawLine(red, androidx.compose.ui.geometry.Offset(size.width * .65f, size.height * .36f), androidx.compose.ui.geometry.Offset(size.width * .60f, size.height * .82f), stroke, cap = StrokeCap.Round)
                        drawLine(red, androidx.compose.ui.geometry.Offset(size.width * .40f, size.height * .82f), androidx.compose.ui.geometry.Offset(size.width * .60f, size.height * .82f), stroke, cap = StrokeCap.Round)
                        drawLine(red, androidx.compose.ui.geometry.Offset(size.width * .48f, size.height * .43f), androidx.compose.ui.geometry.Offset(size.width * .48f, size.height * .72f), stroke, cap = StrokeCap.Round)
                        drawLine(red, androidx.compose.ui.geometry.Offset(size.width * .54f, size.height * .43f), androidx.compose.ui.geometry.Offset(size.width * .54f, size.height * .72f), stroke, cap = StrokeCap.Round)
                    }
                }
            }
            Text("${group.studentCount} estudiantes", color = KMatInk)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Código de unión", color = Color(0xFF52657A), fontSize = 12.sp)
                    Text(group.joinCode, color = KMatInk, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
                TextButton(onClick = onCopy) { Text("Copiar") }
            }
            KMatSecondaryButton("Ver estudiantes", onOpen, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StudentCard(student: TeachingStudent, onProgress: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(student.name, color = KMatInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(student.educationLevel, color = Color(0xFF52657A), fontSize = 13.sp)
            OutlinedButton(onClick = onProgress, modifier = Modifier.fillMaxWidth()) { Text("Progreso") }
        }
    }
}

@Composable
private fun HistoryRow(entry: ExerciseHistoryEntry) {
    val result = when (entry.correct) { true -> "Correcta"; false -> "Incorrecta"; null -> "Sin resultado" }
    Text("${entry.activity} · $result\n${entry.type} · ${entry.durationMillis / 1000} s · ${entry.date}")
}

@Composable
private fun StatusMessage(text: String, color: Color) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .92f)).padding(12.dp), color, textAlign = TextAlign.Center)
}

private fun TeachingResult<*>.message(): String = when (this) {
    TeachingResult.Unauthorized -> "La sesión docente no es válida. Ingresa nuevamente."
    TeachingResult.InvalidRequest -> "No se pudo completar la solicitud. Revisa los datos e inténtalo otra vez."
    TeachingResult.NotConfigured -> "La conexión con Supabase no está configurada."
    TeachingResult.NetworkError -> "No se pudo conectar con el servicio docente. Comprueba tu conexión."
    is TeachingResult.Success<*> -> ""
}

private fun StudentReport.toProgressPdfReport(): ProgressPdfReport {
    val areas = listOf(
        "NUMEROS_OPERACIONES" to "Números y operaciones",
        "PATRONES_ALGEBRA" to "Patrones y álgebra",
        "GEOMETRIA" to "Geometría",
        "MEDICION" to "Medición",
        "DATOS_PROBABILIDAD" to "Datos y probabilidades"
    )
    val areaResults = areas.map { (code, label) ->
        val attempts = history.filter { it.area == code && it.correct != null }
        ProgressPdfArea(label, attempts.count { it.correct == true }, attempts.size)
    }
    val trend = sessionProgress.mapIndexed { index, point ->
        ProgressPdfTrendPoint("Sesión ${index + 1} · ${point.date.take(10)}", point.correct, point.total)
    }
    val historyRows = history.map { item ->
        val areaName = areas.firstOrNull { it.first == item.area }?.second ?: "Área no identificada"
        ProgressPdfHistoryRow(
            date = item.date.take(10),
            area = areaName,
            activity = item.activity + " · " + item.type,
            result = when (item.correct) {
                true -> "Correcta"
                false -> "Incorrecta"
                null -> "Sin resultado"
            },
            answer = item.answer,
            isCorrect = item.correct,
            durationMillis = item.durationMillis
        )
    }
    return ProgressPdfReport(
        studentName = studentName,
        selectedGrade = educationLevel.toDisplayGrade(),
        performanceLevel = performanceLevel ?: "Sin diagnóstico registrado",
        areas = areaResults,
        trend = trend,
        history = historyRows
    )
}

private fun String.toDisplayGrade(): String = when (this) {
    "BASICO_1" -> "1° Básico"
    "BASICO_2" -> "2° Básico"
    else -> this
}
private fun fileSafe(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "reporte" }
