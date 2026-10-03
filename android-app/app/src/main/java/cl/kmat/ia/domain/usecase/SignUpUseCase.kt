package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.SignUpResult
import cl.kmat.ia.domain.repository.AuthRepository

class SignUpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(displayName: String, email: String, password: String): SignUpResult =
        repository.signUp(displayName.trim(), email.trim(), password)
}
