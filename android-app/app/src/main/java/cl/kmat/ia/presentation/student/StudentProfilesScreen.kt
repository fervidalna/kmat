package cl.kmat.ia.presentation.student

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.R
import cl.kmat.ia.domain.model.CreateStudentProfileResult
import cl.kmat.ia.domain.model.DeleteStudentProfileResult
import cl.kmat.ia.domain.model.JoinTeachingGroupResult
import cl.kmat.ia.domain.model.StudentProfile
import cl.kmat.ia.domain.model.StudentProfilesResult
import cl.kmat.ia.domain.repository.StudentProfilesRepository
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentProfilesScreen(
    repository: StudentProfilesRepository,
    onSelect: (StudentProfile) -> Unit,
    onBack: () -> Unit,
    backLabel: String = "Volver"
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<StudentProfile>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("BASICO_1") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasLoadedProfiles by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }
    var showAddForm by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<StudentProfile?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deletionMode by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var pendingGroupLink by remember { mutableStateOf<StudentProfile?>(null) }
    var joinCode by remember { mutableStateOf("") }
    var joiningGroup by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository, reloadToken) {
            loading = true
            error = null
            when (val result = repository.listMine()) {
                is StudentProfilesResult.Success -> {
                    profiles = result.profiles
                    hasLoadedProfiles = true
                    showAddForm = false
                }
                StudentProfilesResult.NotConfigured -> error = "La conexión segura con Supabase aún no está configurada."
                StudentProfilesResult.Unauthorized -> error = "Inicia sesión nuevamente para gestionar los perfiles."
                StudentProfilesResult.PermissionDenied -> error = "No se pudieron leer tus perfiles por un problema de permisos. Tus perfiles guardados no se han eliminado."
                StudentProfilesResult.ServiceUnavailable -> error = "El servicio de perfiles no está disponible. Inténtalo nuevamente más tarde."
                StudentProfilesResult.InvalidResponse -> error = "No se pudo interpretar la lista de perfiles. Intenta cargarla nuevamente."
                StudentProfilesResult.NetworkError -> error = "No se pudieron cargar los perfiles. Comprueba tu conexión."
            }
            loading = false
    }

    pendingDeletion?.let { profile ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDeletion = null },
            title = { Text("¿Estás seguro de que quieres eliminar el perfil de ${profile.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Se borrarán definitivamente este perfil y sus datos de aprendizaje. Esta acción no se puede deshacer.")
                    deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    modifier = Modifier.testTag("confirm-delete-profile"),
                    onClick = {
                        if (!deleting) {
                            deleting = true
                            deletionError = null
                            scope.launch {
                                try {
                                    when (repository.deleteMine(profile.id)) {
                                        DeleteStudentProfileResult.Success -> {
                                            profiles = profiles.filterNot { it.id == profile.id }
                                            pendingDeletion = null
                                            successMessage = "El perfil de ${profile.name} y sus datos se borraron definitivamente."
                                            reloadToken++
                                        }
                                        DeleteStudentProfileResult.NotConfigured -> deletionError = "El servicio no está configurado. Inténtalo más tarde."
                                        DeleteStudentProfileResult.Unauthorized -> deletionError = "Tu sesión venció. Vuelve a ingresar para eliminar el perfil."
                                        DeleteStudentProfileResult.PermissionDenied -> deletionError = "No tienes permiso para eliminar este perfil o ya fue borrado. Actualiza la lista."
                                        DeleteStudentProfileResult.SharedProfile -> deletionError = "Este perfil está compartido con otro responsable y no se puede borrar mientras siga vinculado."
                                        DeleteStudentProfileResult.LocalCleanupFailed -> {
                                            profiles = profiles.filterNot { it.id == profile.id }
                                            pendingDeletion = null
                                            successMessage = "El perfil se borró de Supabase, pero no se pudieron limpiar sus datos en este dispositivo."
                                            reloadToken++
                                        }
                                        DeleteStudentProfileResult.ServiceUnavailable -> deletionError = "El servicio no pudo confirmar la eliminación. Inténtalo más tarde."
                                        DeleteStudentProfileResult.NetworkError -> deletionError = "No se pudo confirmar la eliminación. Revisa tu conexión y vuelve a intentarlo."
                                    }
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    }
                ) {
                    Text(if (deleting) "Eliminando…" else "Eliminar perfil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { pendingDeletion = null }) { Text("Cancelar") }
            }
        )
    }

    pendingGroupLink?.let { profile ->
        AlertDialog(
            onDismissRequest = { if (!joiningGroup) pendingGroupLink = null },
            title = { Text("Vincular a una sala") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ingresa el código que compartió el docente para ${profile.name}.")
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.take(20).uppercase(); joinError = null },
                        label = { Text("Código de unión") },
                        singleLine = true,
                        enabled = !joiningGroup
                    )
                    joinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !joiningGroup && joinCode.trim().isNotEmpty(),
                    onClick = {
                        joiningGroup = true
                        joinError = null
                        scope.launch {
                            when (val result = repository.joinTeachingGroup(profile.id, joinCode)) {
                                is JoinTeachingGroupResult.Success -> {
                                    successMessage = "${profile.name} se vinculó a la sala ${result.groupName}."
                                    joinCode = ""
                                    pendingGroupLink = null
                                }
                                JoinTeachingGroupResult.AlreadyJoined -> joinError = "Este perfil ya pertenece a esa sala."
                                JoinTeachingGroupResult.InvalidCode -> joinError = "El código no corresponde a una sala activa."
                                JoinTeachingGroupResult.Unauthorized -> joinError = "Tu sesión venció. Vuelve a ingresar."
                                JoinTeachingGroupResult.NotConfigured,
                                JoinTeachingGroupResult.NetworkError -> joinError = "No se pudo vincular el perfil. Revisa la conexión e inténtalo nuevamente."
                            }
                            joiningGroup = false
                        }
                    }
                ) { Text(if (joiningGroup) "Vinculando…" else "Vincular") }
            },
            dismissButton = {
                TextButton(enabled = !joiningGroup, onClick = { pendingGroupLink = null; joinCode = "" }) {
                    Text("Cancelar")
                }
            }
        )
    }

    KMatBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val compact = maxWidth < 400.dp || maxHeight < 500.dp
            val cardMaxWidth = if (landscape) 520.dp else 600.dp
            val buttonMaxWidth = if (landscape) 340.dp else 420.dp
            val levelRowMaxWidth = if (landscape) 360.dp else 460.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (compact) 12.dp else 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = cardMaxWidth).fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.96f))
                        .padding(if (compact) 14.dp else 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                ) {
                    Text(
                        if (showAddForm) "Crear perfil de estudiante" else "¿Quién va a aprender?",
                        color = KMatInk,
                        fontSize = if (compact) 24.sp else 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    if (!showAddForm && error != null) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        if (profiles.isEmpty()) {
                            Text(
                                "No crees otro perfil todavía. Primero vuelve a cargar los que ya guardaste.",
                                color = KMatInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (!showAddForm) {
                        successMessage?.let { Text(it, color = KMatInk, textAlign = TextAlign.Center) }
                    }
                    if (!showAddForm && !loading) {
                        if (profiles.isNotEmpty()) {
                            Text(
                                if (deletionMode) "Elige la papelera del perfil que quieres eliminar."
                                else "Selecciona un perfil para continuar.",
                                color = KMatInk,
                                textAlign = TextAlign.Center
                            )
                        } else if (hasLoadedProfiles && error == null) {
                            Text("Esta cuenta aún no tiene perfiles de estudiante vinculados.", color = KMatInk, textAlign = TextAlign.Center)
                        }
                        FlowRow(
                            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            maxItemsInEachRow = 4
                        ) {
                            profiles.forEach { profile ->
                                ProfileTile(
                                    profile = profile,
                                    deletionMode = deletionMode,
                                    onClick = { if (!deletionMode) onSelect(profile) },
                                    onJoinGroup = {
                                        joinCode = ""
                                        joinError = null
                                        pendingGroupLink = profile
                                    },
                                    onDelete = {
                                        deletionError = null
                                        successMessage = null
                                        pendingDeletion = profile
                                    },
                                    showDelete = deletionMode
                                )
                            }
                            if (error == null && hasLoadedProfiles) {
                                AddProfileTile(onClick = {
                                    name = ""
                                    error = null
                                    deletionMode = false
                                    showAddForm = true
                                })
                            }
                        }
                        if (compact) {
                            Column(
                                modifier = Modifier.widthIn(max = buttonMaxWidth).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KMatSecondaryButton(
                                    if (error == null) "Actualizar perfiles" else "Reintentar carga",
                                    { reloadToken++ },
                                    Modifier.fillMaxWidth()
                                )
                                if (profiles.isNotEmpty()) {
                                    KMatSecondaryButton(
                                        if (deletionMode) "Cancelar eliminación" else "Eliminar perfiles",
                                        { deletionMode = !deletionMode },
                                        Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.widthIn(max = buttonMaxWidth).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                KMatSecondaryButton(
                                    if (error == null) "Actualizar perfiles" else "Reintentar carga",
                                    { reloadToken++ },
                                    Modifier.weight(1f)
                                )
                                if (profiles.isNotEmpty()) {
                                    KMatSecondaryButton(
                                        if (deletionMode) "Cancelar eliminación" else "Eliminar perfiles",
                                        { deletionMode = !deletionMode },
                                        Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    if (loading) {
                        CircularProgressIndicator(color = KMatInk)
                        Text("Cargando perfiles…", color = KMatInk)
                    }
                    if (showAddForm) {
                        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        if (profiles.isEmpty()) {
                            Text("Crea el primer perfil para que tu hijo o hija comience. Cada perfil guarda su propio avance.", color = KMatInk, textAlign = TextAlign.Center)
                        }
                        Text("Datos del estudiante", color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(100); error = null },
                            modifier = Modifier.widthIn(max = levelRowMaxWidth).fillMaxWidth(),
                            label = { Text("Nombre para mostrar") },
                            singleLine = true,
                            enabled = !saving
                        )
                        Text("Nivel educativo", color = KMatInk, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.widthIn(max = levelRowMaxWidth).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf("BASICO_1" to "1° Básico", "BASICO_2" to "2° Básico").forEach { (code, label) ->
                                if (level == code) KMatPrimaryButton(label, { level = code }, Modifier.weight(1f))
                                else KMatSecondaryButton(label, { level = code }, Modifier.weight(1f))
                            }
                        }
                        Text(
                            "Al crear el perfil confirmas que eres responsable del estudiante y autorizas el uso de K-Mat IA para apoyar su aprendizaje.",
                            color = KMatInk, fontSize = 13.sp, textAlign = TextAlign.Center
                        )
                        KMatPrimaryButton(
                            if (saving) "Guardando…" else "Crear perfil",
                            onClick = {
                                if (name.trim().isEmpty()) {
                                    error = "Escribe el nombre para mostrar."
                                } else {
                                    scope.launch {
                                        saving = true
                                        error = null
                                        when (val result = repository.createMine(name, level)) {
                                            is CreateStudentProfileResult.Success -> {
                                                name = ""
                                                profiles = (profiles + result.profile).distinctBy { it.id }
                                                showAddForm = false
                                                reloadToken++
                                            }
                                            CreateStudentProfileResult.NotConfigured -> error = "La conexión segura con Supabase aún no está configurada."
                                            CreateStudentProfileResult.Unauthorized -> error = "Tu sesión no tiene permiso para crear perfiles. Vuelve a ingresar."
                                            CreateStudentProfileResult.InvalidRequest -> error = "No se pudo validar el perfil. Revisa el nombre y nivel."
                                            CreateStudentProfileResult.NetworkError -> error = "No se pudo guardar el perfil. Comprueba tu conexión e inténtalo otra vez."
                                        }
                                        saving = false
                                    }
                                }
                            },
                            Modifier.widthIn(max = buttonMaxWidth).fillMaxWidth(),
                            enabled = !saving && !loading
                        )
                        if (!saving) {
                            KMatSecondaryButton(
                                "Cancelar",
                                { error = null; showAddForm = false },
                                Modifier.widthIn(max = buttonMaxWidth).fillMaxWidth()
                            )
                        }
                    }
                    KMatSecondaryButton(backLabel, onBack, Modifier.widthIn(max = buttonMaxWidth).fillMaxWidth())
                }
            }
        }
    }
}
@Composable
private fun ProfileTile(
    profile: StudentProfile,
    deletionMode: Boolean,
    onClick: () -> Unit,
    onJoinGroup: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean
) {
    Column(
        modifier = Modifier.width(128.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .clickable(enabled = !deletionMode, onClick = onClick).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(82.dp).clip(CircleShape).background(Color(0xFFE0F3FF)),
                contentAlignment = Alignment.Center
            ) {
                val initials = profile.name.trim().split(Regex("\\s+"))
                    .take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                Text(initials, color = KMatInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(profile.name, color = KMatInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(profile.educationLevel.toDisplayLevel(), color = KMatInk, fontSize = 12.sp, maxLines = 1)
        }
        if (!deletionMode) {
            TextButton(onClick = onJoinGroup, modifier = Modifier.fillMaxWidth()) {
                Text("Unir a sala", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (showDelete) OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth().testTag("delete-profile-${profile.id}")
                .semantics { contentDescription = "Eliminar perfil de ${profile.name}" },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.error
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_outline),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Eliminar", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(112.dp).clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(82.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = KMatInk, fontSize = 46.sp, fontWeight = FontWeight.Light)
        }
        Text("Agregar", color = KMatInk, fontWeight = FontWeight.Bold)
        Text("estudiante", color = KMatInk, fontSize = 12.sp)
    }
}

private fun String.toDisplayLevel(): String = when (this) {
    "BASICO_1" -> "1° Básico"
    "BASICO_2" -> "2° Básico"
    else -> this
}
