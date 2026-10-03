package cl.kmat.ia.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton

@Composable
fun RegistrationScreen(onAuthenticated: (UserArea) -> Unit, onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as KMatApplication
    val viewModel: RegistrationViewModel = viewModel(
        factory = remember(application) { RegistrationViewModelFactory(application.container.signUp) }
    )
    val state = viewModel.uiState

    KMatBackground {
        Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.96f))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KMatLogo(compact = true)
                Text("Crear cuenta", color = KMatInk, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Registra una cuenta de responsable para acompañar el aprendizaje.",
                    color = KMatInk,
                    textAlign = TextAlign.Center
                )
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::updateDisplayName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre para mostrar") },
                    singleLine = true,
                    enabled = !state.isLoading
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::updateEmail,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                PasswordField(
                    value = state.password,
                    onValueChange = viewModel::updatePassword,
                    label = "Contraseña (mínimo 10 caracteres)",
                    visible = state.passwordVisible,
                    onVisibilityChange = viewModel::togglePasswordVisibility,
                    enabled = !state.isLoading
                )
                PasswordField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::updateConfirmPassword,
                    label = "Repite la contraseña",
                    visible = state.passwordVisible,
                    onVisibilityChange = viewModel::togglePasswordVisibility,
                    enabled = !state.isLoading
                )
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                state.confirmationMessage?.let {
                    Text(it, color = KMatInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                KMatPrimaryButton(
                    text = if (state.isLoading) "Creando cuenta…" else "Crear cuenta",
                    onClick = { viewModel.register(onAuthenticated) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                )
                if (state.isLoading) CircularProgressIndicator(color = KMatInk)
                TextButton(onClick = onBack, enabled = !state.isLoading) {
                    Text("Ya tengo una cuenta", color = KMatInk, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: () -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onVisibilityChange, enabled = enabled) {
                Text(if (visible) "Ocultar" else "Mostrar")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}
