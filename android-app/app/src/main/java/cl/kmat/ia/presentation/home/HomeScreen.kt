package cl.kmat.ia.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import cl.kmat.ia.presentation.navigation.Route
import cl.kmat.ia.domain.model.JoinTeachingGroupResult
import cl.kmat.ia.domain.repository.StudentProfilesRepository
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    displayName: String,
    studentId: String?,
    studentProfilesRepository: StudentProfilesRepository,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }
    var joinMessage by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf(false) }

    BoxWithConstraints {
        val isTablet = maxWidth >= 840.dp && maxHeight >= 520.dp
        val phoneLandscape = !isTablet && maxWidth > maxHeight
        val screenWidth = maxWidth
        KMatBackground {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(if (isTablet) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .padding(horizontal = if (isTablet) 54.dp else 16.dp, vertical = if (isTablet) 24.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeTopBar(
                    isTablet,
                    onSettings = { onNavigate(Route.ChildSettings) },
                    onSignOut = onSignOut
                )
                joinMessage?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp), color = Color(0xFF176B3A), textAlign = TextAlign.Center)
                }
                if (isTablet) {
                    TabletHomeContent(screenWidth, displayName, onNavigate, if (studentId != null) ({ showJoinDialog = true }) else null)
                } else if (phoneLandscape) {
                    PhoneLandscapeHomeContent(
                        displayName,
                        onNavigate,
                        if (studentId != null) ({ showJoinDialog = true }) else null
                    )
                } else {
                    PhoneHomeContent(displayName, onNavigate, if (studentId != null) ({ showJoinDialog = true }) else null)
                }
            }
        }
    }

    if (showJoinDialog && studentId != null) {
        AlertDialog(
            onDismissRequest = { if (!joining) showJoinDialog = false },
            title = { Text("Unir este perfil a una sala") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ingresa el código que compartió el docente para ${displayName}.")
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.take(20).uppercase(); joinError = null },
                        label = { Text("Código de unión") },
                        singleLine = true,
                        enabled = !joining
                    )
                    joinError?.let { Text(it, color = Color(0xFF9B2525)) }
                }
            },
            confirmButton = {
                TextButton(enabled = !joining && joinCode.isNotBlank(), onClick = {
                    joining = true
                    joinError = null
                    scope.launch {
                        when (val result = studentProfilesRepository.joinTeachingGroup(studentId, joinCode)) {
                            is JoinTeachingGroupResult.Success -> {
                                joinMessage = "$displayName se unió a la sala ${result.groupName}."
                                joinCode = ""
                                showJoinDialog = false
                            }
                            JoinTeachingGroupResult.AlreadyJoined -> joinError = "Este perfil ya pertenece a esa sala."
                            JoinTeachingGroupResult.InvalidCode -> joinError = "El código no corresponde a una sala activa."
                            JoinTeachingGroupResult.Unauthorized -> joinError = "Tu sesión venció. Vuelve a ingresar."
                            JoinTeachingGroupResult.NotConfigured,
                            JoinTeachingGroupResult.NetworkError -> joinError = "No se pudo unir a la sala. Revisa tu conexión e inténtalo nuevamente."
                        }
                        joining = false
                    }
                }) { Text(if (joining) "Uniendo…" else "Unirse") }
            },
            dismissButton = {
                TextButton(enabled = !joining, onClick = { showJoinDialog = false; joinCode = "" }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun HomeTopBar(isTablet: Boolean, onSettings: () -> Unit, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KMatLogo(compact = !isTablet)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KMatSecondaryButton("Cerrar sesión", onSignOut)
            KMatSecondaryButton("⚙", onSettings, Modifier.size(if (isTablet) 72.dp else 58.dp))
        }
    }
}

@Composable
private fun TabletHomeContent(maxWidth: androidx.compose.ui.unit.Dp, displayName: String, onNavigate: (String) -> Unit, onJoinRoom: (() -> Unit)?) {
    val mascotWidth = (maxWidth * 0.31f).coerceIn(220.dp, 360.dp)
    val contentWidth = (maxWidth * 0.44f).coerceIn(360.dp, 540.dp)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        KMatMascot(Modifier.width(mascotWidth).height(390.dp))
        Column(
            modifier = Modifier.width(contentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WelcomeText(displayName, isTablet = true)
            KMatPrimaryButton("▶  Continuar aprendiendo", { onNavigate(Route.Practice) }, Modifier.fillMaxWidth())
            KMatSecondaryButton("⭐  Mi progreso", { onNavigate(Route.Progress) }, Modifier.fillMaxWidth(0.82f))
            onJoinRoom?.let { KMatSecondaryButton("Unirse a una sala", it, Modifier.fillMaxWidth(0.82f)) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        KMatSecondaryButton("👥  Cambiar perfil", { onNavigate(Route.ProfileSelection) }, Modifier.width(270.dp))
        KMatSecondaryButton("⚙  Acceso adulto", { onNavigate(Route.AdultAuth) }, Modifier.width(270.dp))
    }
}

@Composable
private fun PhoneHomeContent(displayName: String, onNavigate: (String) -> Unit, onJoinRoom: (() -> Unit)?) {
    Spacer(Modifier.height(12.dp))
    KMatMascot(Modifier.size(145.dp))
    Spacer(Modifier.height(6.dp))
    WelcomeText(displayName, isTablet = false)
    Spacer(Modifier.height(16.dp))
    KMatPrimaryButton("▶  Continuar aprendiendo", { onNavigate(Route.Practice) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    KMatSecondaryButton("⭐  Mi progreso", { onNavigate(Route.Progress) }, Modifier.fillMaxWidth(0.9f))
    onJoinRoom?.let { KMatSecondaryButton("Unirse a una sala", it, Modifier.fillMaxWidth()) }
    Spacer(Modifier.height(18.dp))
    KMatSecondaryButton("👥  Cambiar perfil", { onNavigate(Route.ProfileSelection) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    KMatSecondaryButton("⚙  Acceso adulto", { onNavigate(Route.AdultAuth) }, Modifier.fillMaxWidth())
}

@Composable
private fun PhoneLandscapeHomeContent(
    displayName: String,
    onNavigate: (String) -> Unit,
    onJoinRoom: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KMatMascot(Modifier.weight(0.72f).height(205.dp))
        Column(
            modifier = Modifier.weight(1.28f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WelcomeText(displayName, isTablet = false)
            KMatPrimaryButton("▶  Continuar aprendiendo", { onNavigate(Route.Practice) }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KMatSecondaryButton("⭐  Mi progreso", { onNavigate(Route.Progress) }, Modifier.weight(1f))
                onJoinRoom?.let { KMatSecondaryButton("Unirse a sala", it, Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KMatSecondaryButton("👥  Cambiar perfil", { onNavigate(Route.ProfileSelection) }, Modifier.weight(1f))
                KMatSecondaryButton("⚙  Acceso adulto", { onNavigate(Route.AdultAuth) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WelcomeText(displayName: String, isTablet: Boolean) {
    Text(
        "¡Hola, ${displayName.substringBefore(' ')}!",
        color = KMatInk,
        fontWeight = FontWeight.ExtraBold,
        fontSize = if (isTablet) 48.sp else 32.sp,
        textAlign = TextAlign.Center
    )
    Text(
        "¡Listo para seguir aprendiendo!",
        color = KMatInk,
        fontWeight = FontWeight.ExtraBold,
        fontSize = if (isTablet) 27.sp else 19.sp,
        textAlign = TextAlign.Center
    )
}
