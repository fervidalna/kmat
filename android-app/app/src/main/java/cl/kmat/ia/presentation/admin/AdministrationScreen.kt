package cl.kmat.ia.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.domain.model.ManagedAccount
import cl.kmat.ia.domain.model.ManagedAccountActionResult
import cl.kmat.ia.domain.model.ManagedAccountsResult
import cl.kmat.ia.domain.model.PasswordResetResult
import cl.kmat.ia.domain.repository.ManagedAccountsRepository
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdministrationScreen(repository: ManagedAccountsRepository, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<ManagedAccount>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<ManagedAccount?>(null) }
    var busyAccountId by remember { mutableStateOf<String?>(null) }

    fun loadAccounts() {
        scope.launch {
            loading = true
            error = null
            when (val result = repository.listAccounts()) {
                is ManagedAccountsResult.Success -> accounts = result.accounts
                ManagedAccountsResult.NotConfigured -> error = "La conexion segura con Supabase no esta configurada."
                ManagedAccountsResult.Unauthorized -> error = "No se pudo verificar el permiso de administrador. Vuelve a ingresar."
                ManagedAccountsResult.NetworkError -> error = "No se pudieron cargar las cuentas. Comprueba la conexion y la migracion de Supabase."
            }
            loading = false
        }
    }

    LaunchedEffect(repository) { loadAccounts() }

    pendingDeletion?.let { account ->
        AlertDialog(
            onDismissRequest = { if (busyAccountId == null) pendingDeletion = null },
            title = { Text("¿Eliminar la cuenta de ${account.displayName}?") },
            text = { Text("Se eliminarán la cuenta y sus datos asociados de K-Mat IA. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    enabled = busyAccountId == null,
                    onClick = {
                        busyAccountId = account.id
                        scope.launch {
                            when (repository.deleteAccount(account.id)) {
                                ManagedAccountActionResult.Success -> {
                                    accounts = accounts.filterNot { it.id == account.id }
                                    message = "La cuenta de ${account.displayName} fue eliminada."
                                    pendingDeletion = null
                                }
                                ManagedAccountActionResult.Unauthorized -> error = "Tu sesión de administrador venció. Ingresa nuevamente."
                                ManagedAccountActionResult.NotFound -> error = "La cuenta ya no existe. Actualiza la lista."
                                ManagedAccountActionResult.NotConfigured,
                                ManagedAccountActionResult.NetworkError -> error = "No se pudo eliminar la cuenta. Comprueba la conexión e inténtalo nuevamente."
                            }
                            busyAccountId = null
                        }
                    }
                ) { Text(if (busyAccountId == account.id) "Eliminando…" else "Eliminar cuenta") }
            },
            dismissButton = {
                TextButton(enabled = busyAccountId == null, onClick = { pendingDeletion = null }) { Text("Cancelar") }
            }
        )
    }

    KMatBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = (maxWidth - 40.dp).coerceAtLeast(0.dp).coerceAtMost(1_280.dp)
            val columns = when {
                contentWidth >= 900.dp -> 3
                contentWidth >= 620.dp -> 2
                else -> 1
            }
            val cardWidth = (contentWidth - 16.dp * (columns - 1)) / columns

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = 1_280.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Cuentas de usuario",
                        modifier = Modifier.weight(1f),
                        color = KMatInk,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    TextButton(onClick = onSignOut) { Text("Salir") }
                }
                Text(
                    "Administra las cuentas registradas. Puedes eliminarlas o enviar instrucciones para cambiar su contraseña.",
                    modifier = Modifier.widthIn(max = 680.dp),
                    color = KMatInk,
                    textAlign = TextAlign.Center
                )
                message?.let { Text(it, color = Color(0xFF176B3A), textAlign = TextAlign.Center) }
                if (loading) CircularProgressIndicator(color = KMatInk)
                error?.let { Text(it, color = Color(0xFF9B2525), textAlign = TextAlign.Center) }
                if (!loading && error == null && accounts.isEmpty()) {
                    Text("No hay cuentas registradas para mostrar.", color = KMatInk)
                }

                FlowRow(
                    modifier = Modifier.width(contentWidth),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    accounts.forEach { account ->
                        ManagedAccountCard(
                            account = account,
                            modifier = Modifier.width(cardWidth),
                            busy = busyAccountId != null,
                            onRequestPasswordReset = {
                                busyAccountId = account.id
                                message = null
                                error = null
                                scope.launch {
                                    message = when (repository.requestPasswordReset(account.email)) {
                                        PasswordResetResult.Requested -> "Se enviaron instrucciones de cambio a ${account.email}."
                                        PasswordResetResult.NotConfigured -> "El servicio de correo no está configurado."
                                        PasswordResetResult.NetworkError -> "No se pudo enviar el correo de cambio de contraseña. Inténtalo nuevamente."
                                    }
                                    if (message?.startsWith("No se pudo") == true || message?.startsWith("El servicio") == true) {
                                        error = message
                                        message = null
                                    }
                                    busyAccountId = null
                                }
                            },
                            onDelete = { error = null; message = null; pendingDeletion = account }
                        )
                    }
                }

                KMatSecondaryButton(
                    "Actualizar lista",
                    ::loadAccounts,
                    Modifier.widthIn(max = 440.dp).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ManagedAccountCard(
    account: ManagedAccount,
    modifier: Modifier = Modifier,
    busy: Boolean,
    onRequestPasswordReset: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        border = BorderStroke(1.dp, Color(0xFFD7E7F7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE5F3FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        account.displayName.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull() }
                            .take(2).joinToString("").uppercase().ifBlank { "U" },
                        color = KMatInk,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.displayName,
                        color = KMatInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        account.email.ifBlank { "Sin correo registrado" },
                        color = Color(0xFF52657A),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE7EEF5))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Rol", color = Color(0xFF52657A), fontSize = 12.sp)
                Text(account.roles.ifBlank { "Sin rol" }, color = KMatInk, fontWeight = FontWeight.SemiBold)
                Text("Estado: ${account.status}", color = Color(0xFF52657A), fontSize = 13.sp)
            }

            androidx.compose.material3.Button(
                enabled = !busy && account.email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestPasswordReset
            ) { Text("Enviar enlace de cambio") }
            OutlinedButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDelete,
                border = BorderStroke(1.dp, Color(0xFF9B2525)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9B2525))
            ) { Text("Eliminar cuenta") }
        }
    }
}
