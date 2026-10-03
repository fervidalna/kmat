package cl.kmat.ia.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.kmat.ia.domain.usecase.RequestPasswordResetUseCase
import cl.kmat.ia.domain.usecase.SignInUseCase

class LoginViewModelFactory(
    private val signIn: SignInUseCase,
    private val requestPasswordReset: RequestPasswordResetUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LoginViewModel(signIn, requestPasswordReset) as T
}
