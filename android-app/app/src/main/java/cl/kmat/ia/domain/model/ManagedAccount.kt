package cl.kmat.ia.domain.model

data class ManagedAccount(
    val id: String,
    val displayName: String,
    val email: String,
    val roles: String,
    val status: String
)

sealed interface ManagedAccountsResult {
    data class Success(val accounts: List<ManagedAccount>) : ManagedAccountsResult
    data object NotConfigured : ManagedAccountsResult
    data object Unauthorized : ManagedAccountsResult
    data object NetworkError : ManagedAccountsResult
}

sealed interface ManagedAccountActionResult {
    data object Success : ManagedAccountActionResult
    data object Unauthorized : ManagedAccountActionResult
    data object NotFound : ManagedAccountActionResult
    data object NotConfigured : ManagedAccountActionResult
    data object NetworkError : ManagedAccountActionResult
}
