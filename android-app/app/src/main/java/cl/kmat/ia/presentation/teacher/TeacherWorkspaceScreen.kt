package cl.kmat.ia.presentation.teacher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.domain.model.ExerciseHistoryEntry
import cl.kmat.ia.domain.model.GroupStudentReport
import cl.kmat.ia.domain.model.ReinforcementOption
import cl.kmat.ia.domain.model.StudentReport
import cl.kmat.ia.domain.model.SessionProgressPoint
import cl.kmat.ia.domain.model.TeachingGroup
import cl.kmat.ia.domain.model.TeachingResult
import cl.kmat.ia.domain.model.TeachingStudent
import cl.kmat.ia.domain.repository.TeachingRepository
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeacherWorkspaceScreen(repository: TeachingRepository, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var groups by remember { mutableStateOf<List<TeachingGroup>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<TeachingGroup?>(null) }
    var students by remember { mutableStateOf<List<TeachingStudent>>(emptyList()) }
    var options by remember { mutableStateOf<List<ReinforcementOption>>(emptyList()) }
    var report by remember { mutableStateOf<StudentReport?>(null) }
    var groupReport by remember { mutableStateOf<List<GroupStudentReport>?>(null) }
    var reportTitle by remember { mutableStateOf("") }
    var assignmentStudent by remember { mutableStateOf<TeachingStudent?>(null) }
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
    var exportContent by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(exportContent) }
            } ?: error("No se pudo abrir el archivo")
        }.onSuccess { message = "Reporte exportado correctamente." }
            .onFailure { error = "No se pudo guardar el reporte." }
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
        title = { Text("Asignar refuerzo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estudiante: ${student.name}")
                Text("Actividad")
                options.forEach { option ->
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
            if (count !in 1..option.availableExercises) {
                dialogError = "Indica entre 1 y ${option.availableExercises} ejercicios disponibles."
            } else {
                saving = true
                scope.launch {
                    when (val result = repository.assignReinforcement(student.id, option.contentId, count, note)) {
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

    report?.let { data -> AlertDialog(
        onDismissRequest = { report = null },
        title = { Text("Progreso de ${data.studentName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nivel: ${data.educationLevel} · Sesiones: ${data.sessions}")
                Text("Desempeño por sesión", fontWeight = FontWeight.Bold)
                if (data.sessionProgress.isEmpty()) Text("Aún no hay sesiones con ejercicios registrados.")
                else {
                    SessionProgressChart(data.sessionProgress)
                    data.sessionProgress.forEachIndexed { index, point ->
                        Text("Sesión ${index + 1} · ${point.percentage.toInt()}% correctas · ${point.correct}/${point.total} ejercicios · ${point.date.take(10)}")
                    }
                }
                HorizontalDivider()
                Text("Historial de ejercicios", fontWeight = FontWeight.Bold)
                if (data.history.isEmpty()) Text("Todavía no hay ejercicios registrados.")
                data.history.forEach { HistoryRow(it) }
                OutlinedButton(onClick = {
                    exportContent = individualCsv(data)
                    exportLauncher.launch("progreso-${fileSafe(data.studentName)}.csv")
                }, modifier = Modifier.fillMaxWidth()) { Text("Exportar reporte CSV") }
            }
        },
        confirmButton = { TextButton(onClick = { report = null }) { Text("Cerrar") } }
    ) }

    groupReport?.let { rows -> AlertDialog(
        onDismissRequest = { groupReport = null },
        title = { Text("Reporte: $reportTitle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.isEmpty()) Text("Todavía no hay estudiantes vinculados a esta sala.")
                rows.forEach { row ->
                    Text("${row.name} · ${row.educationLevel}\n${row.sessions} sesiones · ${row.correct} correctas · ${row.incorrect} incorrectas")
                    HorizontalDivider()
                }
                OutlinedButton(onClick = {
                    exportContent = groupCsv(rows)
                    exportLauncher.launch("reporte-${fileSafe(reportTitle)}.csv")
                }, modifier = Modifier.fillMaxWidth()) { Text("Exportar reporte CSV") }
            }
        },
        confirmButton = { TextButton(onClick = { groupReport = null }) { Text("Cerrar") } }
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
                    Text("Espacio docente", color = KMatInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    TextButton(onClick = onSignOut) { Text("Cerrar sesión") }
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
                            onCopy = { clipboard.setText(AnnotatedString(group.joinCode)); message = "Código copiado: ${group.joinCode}" },
                            onReport = {
                                reportTitle = group.name
                                scope.launch {
                                    when (val result = repository.getGroupReport(group.id)) {
                                        is TeachingResult.Success -> groupReport = result.value
                                        else -> error = result.message()
                                    }
                                }
                            }
                        )
                    }
                }
                selectedGroup?.let { group ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(group.name, color = KMatInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("${students.size} estudiantes vinculados", color = KMatInk)
                        }
                        KMatSecondaryButton("Actualizar", { openGroup(group) })
                    }
                    if (students.isEmpty() && !loading) Text("Comparte el código ${group.joinCode} con las familias para que vinculen a sus estudiantes.", color = KMatInk, textAlign = TextAlign.Center)
                    students.forEach { student -> StudentCard(student, onProgress = {
                        scope.launch {
                            when (val result = repository.getStudentReport(student.id)) {
                                is TeachingResult.Success -> report = result.value
                                else -> error = result.message()
                            }
                        }
                    }, onAssign = {
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
                    }) }
                }
                KMatSecondaryButton("Actualizar salas", ::loadGroups, Modifier.widthIn(max = 440.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SessionProgressChart(points: List<SessionProgressPoint>) {
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 8.dp, vertical = 12.dp)) {
        if (points.isEmpty()) return@Canvas
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val chartHeight = bottom - top
        for (step in 0..4) {
            val y = bottom - chartHeight * step / 4f
            drawLine(Color(0xFFDCE5EF), androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), 1.dp.toPx())
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) (left + right) / 2 else left + (right - left) * index / (points.size - 1)
            val y = bottom - chartHeight * (point.percentage / 100f).coerceIn(0f, 1f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, KMatInk, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) (left + right) / 2 else left + (right - left) * index / (points.size - 1)
            val y = bottom - chartHeight * (point.percentage / 100f).coerceIn(0f, 1f)
            drawCircle(KMatInk, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
private fun GroupCard(group: TeachingGroup, selected: Boolean, modifier: Modifier, onOpen: () -> Unit, onCopy: () -> Unit, onReport: () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(Color.White.copy(alpha = .96f)),
        border = BorderStroke(2.dp, if (selected) KMatInk else Color(0xFFD7E7F7)), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.name, color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.studentCount} estudiantes", color = KMatInk)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Código de unión", color = Color(0xFF52657A), fontSize = 12.sp)
                    Text(group.joinCode, color = KMatInk, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
                TextButton(onClick = onCopy) { Text("Copiar") }
            }
            KMatSecondaryButton("Ver estudiantes", onOpen, Modifier.fillMaxWidth())
            OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text("Reporte grupal") }
        }
    }
}

@Composable
private fun StudentCard(student: TeachingStudent, onProgress: () -> Unit, onAssign: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(student.name, color = KMatInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(student.educationLevel, color = Color(0xFF52657A), fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onProgress, modifier = Modifier.weight(1f)) { Text("Progreso") }
                Button(onClick = onAssign, modifier = Modifier.weight(1f)) { Text("Asignar refuerzo") }
            }
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

private fun individualCsv(data: StudentReport): String = buildString {
    appendLine("Estudiante,Nivel,Sesiones,Correctas,Incorrectas")
    appendLine(listOf(data.studentName, data.educationLevel, data.sessions, data.correct, data.incorrect).joinToString(",") { csvCell(it.toString()) })
    appendLine("Actividad,Tipo,Resultado,Respuesta,Duración (ms),Fecha")
    data.history.forEach { entry ->
        appendLine(listOf(entry.activity, entry.type, entry.correct?.let { if (it) "Correcta" else "Incorrecta" } ?: "Sin resultado", entry.answer, entry.durationMillis, entry.date).joinToString(",") { csvCell(it.toString()) })
    }
}

private fun groupCsv(rows: List<GroupStudentReport>): String = buildString {
    appendLine("Estudiante,Nivel,Sesiones,Correctas,Incorrectas,Última actividad")
    rows.forEach { row -> appendLine(listOf(row.name, row.educationLevel, row.sessions, row.correct, row.incorrect, row.lastActivity.orEmpty()).joinToString(",") { csvCell(it.toString()) }) }
}

private fun csvCell(value: String) = "\"${value.replace("\"", "\"\"")}\""
private fun fileSafe(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "reporte" }
