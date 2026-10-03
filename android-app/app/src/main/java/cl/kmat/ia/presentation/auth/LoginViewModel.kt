package cl.kmat.ia.presentation.auth

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.kmat.ia.domain.model.PasswordResetResult
import cl.kmat.ia.domain.model.SignInResult
import cl.kmat.ia.domain.model.UserArea
import cl.kmat.ia.domain.usecase.RequestPasswordResetUseCase
import cl.kmat.ia.domain.usecase.SignInUseCase
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showResetDialog: Boolean = false,
    val resetEmail: String = "",
    val isResetLoading: Boolean = false,
    val resetMessage: String? = null
)

class LoginViewModel(
    private val signIn: SignInUseCase,
    private val requestPasswordReset: RequestPasswordResetUseCase
) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    fun updateEmail(value: String) {
        uiState = uiState.copy(email = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        uiState = uiState.copy(password = value, errorMessage = null)
    }

    fun togglePasswordVisibility() {
        uiState = uiState.copy(passwordVisible = !uiState.passwordVisible)
    }

    fun openPasswordReset() {
        uiState = uiState.copy(
            showResetDialog = true,
            resetEmail = uiState.email,
            resetMessage = null
        )
    }

    fun closePasswordReset() {
        if (!uiState.isResetLoading) uiState = uiState.copy(showResetDialog = false, resetMessage = null)
    }

    fun updateResetEmail(value: String) {
        uiState = uiState.copy(resetEmail = value, resetMessage = null)
    }

    fun signIn(onSuccess: (UserArea) -> Unit) {
        val email = uiState.email.trim()
        val password = uiState.password
        when {
            email.isBlank() || password.isBlank() -> {
                uiState = uiState.copy(errorMessage = "Completa el correo y la contraseña.")
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                uiState = uiState.copy(errorMessage = "Escribe un correo electrónico válido.")
                return
            }
        }

        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = signIn(email, password)
            // Nunca se conserva la contraseña después de enviarla.
            uiState = uiState.copy(isLoading = false, password = "")
            when (result) {
                is SignInResult.Success -> onSuccess(result.area)
                SignInResult.RoleNotValidated -> {
                    uiState = uiState.copy(
                        errorMessage = "No se pudo validar el perfil. Verifica que el usuario tenga un rol asignado en Supabase."
                    )
                }
                SignInResult.InvalidCredentials -> {
                    uiState = uiState.copy(errorMessage = "Correo o contraseña incorrectos.")
                }
                SignInResult.NotConfigured -> {
                    uiState = uiState.copy(errorMessage = "El acceso seguro aún no está configurado.")
                }
                SignInResult.NetworkError -> {
                    uiState = uiState.copy(errorMessage = "No fue posible iniciar sesión. Revisa tu conexión e inténtalo otra vez.")
                }
            }
        }
    }

    fun requestPasswordReset() {
        val email = uiState.resetEmail.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            uiState = uiState.copy(resetMessage = "Escribe un correo electrónico válido.")
            return
        }
        uiState = uiState.copy(isResetLoading = true, resetMessage = null)
        viewModelScope.launch {
            val result = requestPasswordReset(email)
            uiState = uiState.copy(
                isResetLoading = false,
                resetMessage = when (result) {
                    PasswordResetResult.Requested -> "Si el correo está registrado, recibirás instrucciones para restablecer la contraseña."
                    PasswordResetResult.NotConfigured -> "El acceso seguro aún no está configurado."
                    PasswordResetResult.NetworkError -> "No fue posible solicitar el restablecimiento. Inténtalo más tarde."
                }
            )
        }
    }
}
