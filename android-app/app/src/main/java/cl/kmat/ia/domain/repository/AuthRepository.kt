package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.PasswordResetResult
import cl.kmat.ia.domain.model.PasswordUpdateResult
import cl.kmat.ia.domain.model.SignInResult
import cl.kmat.ia.domain.model.SignUpResult
import cl.kmat.ia.domain.model.UserArea

interface AuthRepository {
    suspend fun signIn(email: String, password: String): SignInResult
    suspend fun signUp(displayName: String, email: String, password: String, area: UserArea): SignUpResult
    suspend fun requestPasswordReset(email: String): PasswordResetResult
    suspend fun updatePassword(authCode: String, newPassword: String): PasswordUpdateResult
    suspend fun validAccessTokenOrNull(): String?
    fun hasStoredSession(): Boolean
    fun storedUserAreaOrNull(): UserArea?
    fun storedUserIdOrNull(): String? = null
    suspend fun validateStoredUserArea(): UserArea?
    fun storedDisplayNameOrNull(): String?
    fun saveDisplayName(displayName: String)
    fun saveActiveStudentId(studentId: String)
    fun activeStudentIdOrNull(): String?
    fun clearActiveStudentId()
    suspend fun signOut()
}
