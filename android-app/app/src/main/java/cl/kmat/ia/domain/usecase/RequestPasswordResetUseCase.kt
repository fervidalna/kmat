package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.PasswordResetResult
import cl.kmat.ia.domain.repository.AuthRepository

class RequestPasswordResetUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): PasswordResetResult =
        repository.requestPasswordReset(email.trim())
}
