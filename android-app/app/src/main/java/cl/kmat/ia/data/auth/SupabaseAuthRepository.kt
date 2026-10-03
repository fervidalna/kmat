package cl.kmat.ia.data.auth

import cl.kmat.ia.BuildConfig
import cl.kmat.ia.domain.model.AuthSession
import cl.kmat.ia.domain.model.PasswordResetResult
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

/** Cliente mínimo de Supabase Auth. Las contraseñas solo viven en memoria durante la solicitud HTTPS. */
class SupabaseAuthRepository(
    private val sessionStore: SecureSessionStore
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): SignInResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext SignInResult.NotConfigured

        runCatching {
            val response = post("/auth/v1/token?grant_type=password", JSONObject()
                .put("email", email)
                .put("password", password))
            when (response.code) {
                in 200..299 -> parseSession(response.body)?.let { session ->
                    sessionStore.save(session)
                    resolveUserArea(session)?.let { area ->
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
            val response = post("/auth/v1/recover", JSONObject().put("email", email))
            if (response.code in 200..299) PasswordResetResult.Requested else PasswordResetResult.NetworkError
        }.getOrElse { PasswordResetResult.NetworkError }
    }

    override suspend fun signUp(displayName: String, email: String, password: String): SignUpResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext SignUpResult.NotConfigured

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
                            .put("auto_rol", "RESPONSABLE")
                    )
            )
            when (response.code) {
                in 200..299 -> parseSession(response.body)?.let { session ->
                    sessionStore.save(session)
                    resolveUserArea(session)?.let { SignUpResult.Authenticated(it) }
                        ?: run {
                            sessionStore.clear()
                            SignUpResult.NetworkError
                        }
                } ?: SignUpResult.ConfirmationRequired
                400, 422 -> SignUpResult.InvalidRequest
                else -> SignUpResult.NetworkError
            }
        }.getOrElse { SignUpResult.NetworkError }
    }

    override suspend fun validAccessTokenOrNull(): String? = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: return@withContext null
        if (session.expiresAtEpochSeconds > nowEpochSeconds() + EXPIRY_MARGIN_SECONDS) {
            return@withContext session.accessToken
        }
        refresh(session)
    }

    override fun hasStoredSession(): Boolean = sessionStore.hasSession()

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

    private fun post(path: String, body: JSONObject, bearerToken: String? = null): HttpResponse {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = URL(baseUrl + path)
        require(url.protocol.equals("https", ignoreCase = true)) { "Supabase debe usar HTTPS" }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
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

    private fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000

    private data class HttpResponse(val code: Int, val body: String)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val EXPIRY_MARGIN_SECONDS = 60L
    }
}
