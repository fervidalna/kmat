package cl.kmat.ia.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.kmat.ia.KMatApplication
import cl.kmat.ia.domain.model.UserArea
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import androidx.compose.ui.platform.LocalContext

@Composable
fun LoginScreen(onAuthenticated: (UserArea) -> Unit, onRegister: () -> Unit) {
    val application = LocalContext.current.applicationContext as KMatApplication
    val loginViewModel: LoginViewModel = viewModel(
        factory = remember(application) {
            LoginViewModelFactory(application.container.signIn, application.container.requestPasswordReset)
        }
    )
    val state = loginViewModel.uiState

    BoxWithConstraints {
        val showMascot = maxWidth >= 920.dp && maxHeight >= 500.dp
        KMatBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.widthIn(max = 1_080.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showMascot) KMatMascot(Modifier.size(260.dp))
                    Column(
                        modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.96f))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                KMatLogo()
                Text(
                    "Ingresar",
                    color = KMatInk,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Accede de forma segura para acompañar el aprendizaje.",
                    color = KMatInk,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = state.email,
                    onValueChange = loginViewModel::updateEmail,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = loginViewModel::updatePassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contraseña") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = loginViewModel::togglePasswordVisibility, enabled = !state.isLoading) {
                            Text(if (state.passwordVisible) "Ocultar" else "Mostrar")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )

                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                KMatPrimaryButton(
                    text = if (state.isLoading) "Iniciando sesión…" else "Iniciar sesión",
                    onClick = { loginViewModel.signIn(onAuthenticated) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                )
                if (state.isLoading) CircularProgressIndicator(color = KMatInk)

                TextButton(onClick = loginViewModel::openPasswordReset, enabled = !state.isLoading) {
                    Text("¿Olvidaste tu contraseña?", color = KMatInk, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onRegister, enabled = !state.isLoading) {
                    Text("¿No tienes cuenta? Crear cuenta", color = KMatInk, fontWeight = FontWeight.Bold)
                }
                    }
                }
            }
        }
    }

    if (state.showResetDialog) {
        PasswordResetDialog(
            state = state,
            onEmailChange = loginViewModel::updateResetEmail,
            onConfirm = loginViewModel::requestPasswordReset,
            onDismiss = loginViewModel::closePasswordReset
        )
    }
}

@Composable
private fun PasswordResetDialog(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restablecer contraseña", color = KMatInk, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ingresa tu correo y te enviaremos instrucciones si existe una cuenta asociada.")
                OutlinedTextField(
                    value = state.resetEmail,
                    onValueChange = onEmailChange,
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    enabled = !state.isResetLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                state.resetMessage?.let { Text(it, color = KMatInk) }
                if (state.isResetLoading) CircularProgressIndicator()
            }
        },
        confirmButton = {
            KMatPrimaryButton(
                text = "Enviar instrucciones",
                onClick = onConfirm,
                modifier = Modifier.widthIn(min = 180.dp),
                enabled = !state.isResetLoading
            )
        },
        dismissButton = {
            KMatSecondaryButton("Cancelar", onDismiss)
        }
    )
}
