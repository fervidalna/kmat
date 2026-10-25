package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.ManagedAccountsResult
import cl.kmat.ia.domain.model.ManagedAccountActionResult
import cl.kmat.ia.domain.model.PasswordResetResult

interface ManagedAccountsRepository {
    suspend fun listAccounts(): ManagedAccountsResult
    suspend fun deleteAccount(accountId: String): ManagedAccountActionResult
    suspend fun requestPasswordReset(email: String): PasswordResetResult
}
