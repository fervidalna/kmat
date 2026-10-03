package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.SignInResult
import cl.kmat.ia.domain.repository.AuthRepository

class SignInUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): SignInResult =
        repository.signIn(email.trim(), password)
}
