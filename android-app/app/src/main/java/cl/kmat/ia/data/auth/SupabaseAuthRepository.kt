package cl.kmat.ia.data.auth

import cl.kmat.ia.BuildConfig
import cl.kmat.ia.domain.model.AuthSession
import cl.kmat.ia.domain.model.PasswordResetResult
import cl.kmat.ia.domain.model.PasswordUpdateResult
import cl.kmat.ia.domain.model.SignInResult
import cl.kmat.ia.domain.model.SignUpResult
import cl.kmat.ia.domain.model.UserArea
import cl.kmat.ia.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Cliente mínimo de Supabase Auth. Las contraseñas solo viven en memoria durante la solicitud HTTPS. */
class SupabaseAuthRepository(
    private val sessionStore: SecureSessionStore
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): SignInResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext SignInResult.NotConfigured

        // Cada dispositivo mantiene su perfil activo local; renovar la sesión no lo cambia.

        runCatching {
            val response = post("/auth/v1/token?grant_type=password", JSONObject()
                .put("email", email)
                .put("password", password))
            when (response.code) {
                in 200..299 -> parseSession(response.body)?.let { session ->
                    sessionStore.save(session)
                    sessionStore.clearActiveStudentId()
                    resolveUserArea(session)?.let { area ->
                        sessionStore.saveUserArea(area)
                        resolveDisplayName(session)?.let(sessionStore::saveDisplayName)
                            ?: sessionStore.clearDisplayName()
                        SignInResult.Success(area = area)
                    } ?: run {
                        // Sin un rol confirmado no se concede acceso a ninguna vista.
                        sessionStore.clear()
                        SignInResult.RoleNotValidated
                    }
                } ?: SignInResult.NetworkError
                400, 401, 403, 422 -> SignInResult.InvalidCredentials
                else -> SignInResult.NetworkError
            }
        }.getOrElse { SignInResult.NetworkError }
    }

    override suspend fun requestPasswordReset(email: String): PasswordResetResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext PasswordResetResult.NotConfigured
        runCatching {
            val verifier = createCodeVerifier()
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            )
            sessionStore.savePasswordRecoveryVerifier(verifier)
            val redirectTo = java.net.URLEncoder.encode("kmat://auth/reset-password", Charsets.UTF_8.name())
            val response = post(
                "/auth/v1/recover?redirect_to=$redirectTo",
                JSONObject()
                    .put("email", email)
                    .put("code_challenge", challenge)
                    .put("code_challenge_method", "s256")
            )
            when (response.code) {
                in 200..299 -> PasswordResetResult.Requested
                429 -> {
                    sessionStore.clearPasswordRecoveryVerifier()
                    PasswordResetResult.RateLimited
                }
                else -> {
                    sessionStore.clearPasswordRecoveryVerifier()
                    PasswordResetResult.NetworkError
                }
            }
        }.getOrElse {
            sessionStore.clearPasswordRecoveryVerifier()
            PasswordResetResult.NetworkError
        }
    }

    override suspend fun updatePassword(authCode: String, newPassword: String): PasswordUpdateResult = withContext(Dispatchers.IO) {
        val verifier = sessionStore.loadPasswordRecoveryVerifier()
            ?: return@withContext PasswordUpdateResult.Failed
        if (!isConfigured() || authCode.isBlank() || newPassword.isBlank()) {
            sessionStore.clearPasswordRecoveryVerifier()
            return@withContext PasswordUpdateResult.Failed
        }

        val exchange = runCatching {
            post(
                "/auth/v1/token?grant_type=pkce",
                JSONObject().put("auth_code", authCode).put("code_verifier", verifier)
            )
        }.getOrElse {
            sessionStore.clearPasswordRecoveryVerifier()
            return@withContext PasswordUpdateResult.Failed
        }
        val session = if (exchange.code in 200..299) parseSession(exchange.body) else null
        if (session == null) {
            sessionStore.clearPasswordRecoveryVerifier()
            return@withContext PasswordUpdateResult.Failed
        }
        val update = runCatching {
            request("PUT", "/auth/v1/user", JSONObject().put("password", newPassword), session.accessToken)
        }.getOrElse {
            sessionStore.clearPasswordRecoveryVerifier()
            return@withContext PasswordUpdateResult.Failed
        }
        sessionStore.clearPasswordRecoveryVerifier()
        if (update.code in 200..299) PasswordUpdateResult.Updated else PasswordUpdateResult.Failed
    }

    override suspend fun signUp(displayName: String, email: String, password: String, area: UserArea): SignUpResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext SignUpResult.NotConfigured
        val role = when (area) {
            UserArea.LEARNING -> "RESPONSABLE"
            UserArea.TEACHING -> "DOCENTE"
            UserArea.ADMINISTRATION -> return@withContext SignUpResult.InvalidRequest
        }

        runCatching {
            val response = post(
                "/auth/v1/signup",
                JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .put(
                        "data",
                        JSONObject()
                            .put("nombre_mostrado", displayName)
                            .put("auto_rol", role)
                    )
            )
            when (response.code) {
                in 200..299 -> parseSession(response.body)?.let { session ->
                    sessionStore.save(session)
                    sessionStore.clearActiveStudentId()
                    resolveUserArea(session)?.let {
                        sessionStore.saveUserArea(it)
                        sessionStore.saveDisplayName(resolveDisplayName(session) ?: displayName)
                        SignUpResult.Authenticated(it)
                    }
                        ?: run {
                            sessionStore.clear()
                            SignUpResult.Failed(
                                "Supabase creó la cuenta, pero no devolvió un rol para esta sesión. " +
                                    "Revisa el trigger trg_auth_usuario_registrado y la función mi_area_usuario."
                            )
                        }
                } ?: SignUpResult.ConfirmationRequired
                else -> SignUpResult.Failed(
                    "Supabase respondió HTTP ${response.code}: ${describeError(response.body)}"
                )
            }
        }.getOrElse { error ->
            SignUpResult.Failed(
                "No se pudo completar la solicitud (${error.javaClass.simpleName}): " +
                    (error.message?.takeIf { it.isNotBlank() } ?: "error sin detalle")
            )
        }
    }

    override suspend fun validAccessTokenOrNull(): String? = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: return@withContext null
        if (session.expiresAtEpochSeconds > nowEpochSeconds() + EXPIRY_MARGIN_SECONDS) {
            return@withContext session.accessToken
        }
        refresh(session)
    }

    override fun hasStoredSession(): Boolean = sessionStore.hasSession()

    override fun storedUserAreaOrNull() = sessionStore.loadUserArea()

    override fun storedUserIdOrNull(): String? = sessionStore.load()?.userId

    override suspend fun validateStoredUserArea(): UserArea? = withContext(Dispatchers.IO) {
        val accessToken = validAccessTokenOrNull() ?: return@withContext null
        val session = sessionStore.load() ?: return@withContext null
        val area = resolveUserArea(session.copy(accessToken = accessToken))
        if (area == null) {
            sessionStore.clear()
        } else {
            sessionStore.saveUserArea(area)
        }
        area
    }

    override fun storedDisplayNameOrNull() = sessionStore.loadDisplayName()

    override fun saveDisplayName(displayName: String) = sessionStore.saveDisplayName(displayName)

    override fun saveActiveStudentId(studentId: String) = sessionStore.saveActiveStudentId(studentId)

    override fun activeStudentIdOrNull() = sessionStore.loadActiveStudentId()

    override fun clearActiveStudentId() {
        sessionStore.clearActiveStudentId()
        sessionStore.clearDisplayName()
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        val accessToken = sessionStore.load()?.accessToken
        // Se borra primero para que el cierre sea efectivo aun sin conexión.
        sessionStore.clear()
        if (accessToken != null && isConfigured()) {
            runCatching { post("/auth/v1/logout", JSONObject(), accessToken) }
        }
    }

    private fun refresh(session: AuthSession): String? = runCatching {
        val response = post(
            "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", session.refreshToken)
        )
        if (response.code !in 200..299) {
            sessionStore.clear()
            return null
        }
        parseSession(response.body)?.also(sessionStore::save)?.accessToken
    }.getOrElse {
        // Ante un error de red se preserva la sesión cifrada para reintentar al recuperar conexión.
        null
    }

    /** El rol se consulta con el JWT recién emitido y las políticas RLS del propio usuario. */
    private fun resolveUserArea(session: AuthSession): UserArea? = runCatching {
        val response = post("/rest/v1/rpc/mi_area_usuario", JSONObject(), session.accessToken)
        if (response.code !in 200..299) return@runCatching null
        when (JSONTokener(response.body).nextValue() as? String) {
            "ADMINISTRADOR" -> UserArea.ADMINISTRATION
            "DOCENTE" -> UserArea.TEACHING
            "RESPONSABLE" -> UserArea.LEARNING
            else -> null
        }
    }.getOrNull()

    private fun resolveDisplayName(session: AuthSession): String? = runCatching {
        val response = post("/rest/v1/rpc/mi_nombre_usuario", JSONObject(), session.accessToken)
        if (response.code !in 200..299) return@runCatching null
        (JSONTokener(response.body).nextValue() as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun post(path: String, body: JSONObject, bearerToken: String? = null): HttpResponse {
        return request("POST", path, body, bearerToken)
    }

    private fun request(method: String, path: String, body: JSONObject, bearerToken: String? = null): HttpResponse {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = URL(baseUrl + path)
        require(url.protocol.equals("https", ignoreCase = true)) { "Supabase debe usar HTTPS" }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSession(body: String): AuthSession? = runCatching {
        val json = JSONObject(body)
        AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtEpochSeconds = nowEpochSeconds() + json.getLong("expires_in"),
            userId = json.getJSONObject("user").getString("id")
        )
    }.getOrNull()

    private fun describeError(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "el servidor no entregó detalles."
        val parsed = runCatching {
            val json = JSONObject(trimmed)
            listOf("message", "msg", "error_description", "details", "hint", "code")
                .mapNotNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                .joinToString(" | ")
        }.getOrNull()
        return (parsed?.takeIf { it.isNotBlank() } ?: trimmed).take(600)
    }

    private fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000

    private data class HttpResponse(val code: Int, val body: String)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val EXPIRY_MARGIN_SECONDS = 60L
    }

    private fun createCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
