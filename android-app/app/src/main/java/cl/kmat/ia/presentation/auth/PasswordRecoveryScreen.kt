package cl.kmat.ia.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.domain.model.PasswordUpdateResult
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import kotlinx.coroutines.launch

@Composable
fun PasswordRecoveryScreen(
    authCode: String?,
    callbackError: String? = null,
    onUpdatePassword: suspend (String, String) -> PasswordUpdateResult,
    onBackToLogin: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val hasCode = !authCode.isNullOrBlank()
    var message by remember(authCode, callbackError) {
        mutableStateOf(callbackError ?: if (hasCode) null else "El enlace abrió K-Mat, pero Supabase no entregó el código de recuperación. Solicita un correo nuevo desde esta versión de la app.")
    }
    var isLoading by remember { mutableStateOf(false) }
    var isUpdated by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    KMatBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = 0.96f)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                KMatLogo()
                Text(
                    if (isUpdated) "Contraseña actualizada" else "Crea una contraseña nueva",
                    color = KMatInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                if (isUpdated) {
                    Text("Ya puedes ingresar con tu nueva contraseña.", color = KMatInk, textAlign = TextAlign.Center)
                } else if (!hasCode) {
                    Text(message.orEmpty(), color = KMatInk, textAlign = TextAlign.Center)
                } else {
                    Text("Elige una contraseña de al menos 10 caracteres. El enlace solo funciona en el teléfono donde solicitaste el cambio.", color = KMatInk, textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; message = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nueva contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it; message = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Repite la contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    message?.let { Text(it, color = KMatInk, textAlign = TextAlign.Center) }
                    if (isLoading) CircularProgressIndicator(color = KMatInk)
                    KMatPrimaryButton(
                        text = if (isLoading) "Actualizando…" else "Guardar contraseña",
                        onClick = {
                            when {
                                authCode.isNullOrBlank() -> message = "El enlace no es válido o venció. Solicita otro desde la app."
                                password.length < 10 -> message = "La contraseña debe tener al menos 10 caracteres."
                                password != confirmation -> message = "Las contraseñas no coinciden."
                                else -> {
                                    isLoading = true
                                    message = null
                                    scope.launch {
                                        when (onUpdatePassword(authCode, password)) {
                                            PasswordUpdateResult.Updated -> {
                                                isUpdated = true
                                                password = ""
                                                confirmation = ""
                                            }
                                            PasswordUpdateResult.Failed -> message = "No se pudo actualizar. El enlace pudo vencer o ya se usó; solicita otro desde la app."
                                        }
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                }
                TextButton(onClick = onBackToLogin) { Text("Volver a ingresar", color = KMatInk) }
            }
        }
    }
}
