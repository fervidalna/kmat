package cl.kmat.ia.presentation.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.kmat.ia.domain.model.SignUpResult
import cl.kmat.ia.domain.model.UserArea
import cl.kmat.ia.domain.usecase.SignUpUseCase
import kotlinx.coroutines.launch

data class RegistrationUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val confirmationMessage: String? = null
)

class RegistrationViewModel(private val signUp: SignUpUseCase) : ViewModel() {
    var uiState by mutableStateOf(RegistrationUiState())
        private set

    fun updateDisplayName(value: String) { uiState = uiState.copy(displayName = value, errorMessage = null) }
    fun updateEmail(value: String) { uiState = uiState.copy(email = value, errorMessage = null) }
    fun updatePassword(value: String) { uiState = uiState.copy(password = value, errorMessage = null) }
    fun updateConfirmPassword(value: String) { uiState = uiState.copy(confirmPassword = value, errorMessage = null) }
    fun togglePasswordVisibility() { uiState = uiState.copy(passwordVisible = !uiState.passwordVisible) }

    fun register(onAuthenticated: (UserArea) -> Unit) {
        val state = uiState
        val name = state.displayName.trim()
        val email = state.email.trim()
        when {
            name.isBlank() || email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank() -> {
                uiState = state.copy(errorMessage = "Completa todos los campos.")
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                uiState = state.copy(errorMessage = "Escribe un correo electrónico válido.")
                return
            }
            state.password.length < 10 -> {
                uiState = state.copy(errorMessage = "La contraseña debe tener al menos 10 caracteres.")
                return
            }
            state.password != state.confirmPassword -> {
                uiState = state.copy(errorMessage = "Las contraseñas no coinciden.")
                return
            }
        }

        uiState = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = signUp(name, email, state.password)
            // Nunca se persiste ni mantiene la contraseña luego de enviarla.
            uiState = uiState.copy(isLoading = false, password = "", confirmPassword = "")
            when (result) {
                is SignUpResult.Authenticated -> onAuthenticated(result.area)
                SignUpResult.ConfirmationRequired -> {
                    uiState = uiState.copy(
                        confirmationMessage = "Revisa tu correo y confirma la cuenta antes de iniciar sesión."
                    )
                }
                SignUpResult.InvalidRequest -> {
                    uiState = uiState.copy(errorMessage = "No fue posible crear la cuenta. Revisa los datos e inténtalo nuevamente.")
                }
                SignUpResult.NotConfigured -> {
                    uiState = uiState.copy(errorMessage = "El acceso seguro aún no está configurado.")
                }
                SignUpResult.NetworkError -> {
                    uiState = uiState.copy(errorMessage = "No fue posible crear la cuenta. Revisa tu conexión e inténtalo otra vez.")
                }
            }
        }
    }
}
