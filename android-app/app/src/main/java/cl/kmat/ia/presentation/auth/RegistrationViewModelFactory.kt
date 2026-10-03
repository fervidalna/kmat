package cl.kmat.ia.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.kmat.ia.domain.usecase.SignUpUseCase

class RegistrationViewModelFactory(private val signUp: SignUpUseCase) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RegistrationViewModel(signUp) as T
}
