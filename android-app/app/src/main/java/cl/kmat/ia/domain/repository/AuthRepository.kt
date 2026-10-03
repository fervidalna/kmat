package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.PasswordResetResult
import cl.kmat.ia.domain.model.SignInResult
import cl.kmat.ia.domain.model.SignUpResult

interface AuthRepository {
    suspend fun signIn(email: String, password: String): SignInResult
    suspend fun signUp(displayName: String, email: String, password: String): SignUpResult
    suspend fun requestPasswordReset(email: String): PasswordResetResult
    suspend fun validAccessTokenOrNull(): String?
    fun hasStoredSession(): Boolean
    suspend fun signOut()
}
