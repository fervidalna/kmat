package cl.kmat.ia.domain.model

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String
)

enum class UserArea {
    ADMINISTRATION,
    TEACHING,
    LEARNING
}

sealed interface SignInResult {
    data class Success(val area: UserArea) : SignInResult
    data object RoleNotValidated : SignInResult
    data object InvalidCredentials : SignInResult
    data object NotConfigured : SignInResult
    data object NetworkError : SignInResult
}

sealed interface SignUpResult {
    data class Authenticated(val area: UserArea) : SignUpResult
    data class Failed(val reason: String) : SignUpResult
    data object ConfirmationRequired : SignUpResult
    data object InvalidRequest : SignUpResult
    data object NotConfigured : SignUpResult
    data object NetworkError : SignUpResult
}

sealed interface PasswordResetResult {
    data object Requested : PasswordResetResult
    data object RateLimited : PasswordResetResult
    data object NotConfigured : PasswordResetResult
    data object NetworkError : PasswordResetResult
}

sealed interface PasswordUpdateResult {
    data object Updated : PasswordUpdateResult
    data object Failed : PasswordUpdateResult
}

