package cl.kmat.ia.data.admin

import cl.kmat.ia.BuildConfig
import cl.kmat.ia.domain.model.ManagedAccount
import cl.kmat.ia.domain.model.ManagedAccountActionResult
import cl.kmat.ia.domain.model.ManagedAccountsResult
import cl.kmat.ia.domain.model.PasswordResetResult
import cl.kmat.ia.domain.repository.AuthRepository
import cl.kmat.ia.domain.repository.ManagedAccountsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseManagedAccountsRepository(
    private val authRepository: AuthRepository
) : ManagedAccountsRepository {
    override suspend fun listAccounts(): ManagedAccountsResult = withContext(Dispatchers.IO) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            return@withContext ManagedAccountsResult.NotConfigured
        }
        val token = authRepository.validAccessTokenOrNull()
            ?: return@withContext ManagedAccountsResult.Unauthorized
        runCatching {
            val response = request("/rest/v1/rpc/listar_cuentas_admin", "{}", token)
            when (response.code) {
                in 200..299 -> {
                    val rows = JSONArray(response.body)
                    ManagedAccountsResult.Success(List(rows.length()) { index ->
                        val row = rows.getJSONObject(index)
                        ManagedAccount(
                            id = row.getString("id_usuario"),
                            displayName = row.getString("nombre_mostrado"),
                            email = row.optString("correo"),
                            roles = row.optString("roles"),
                            status = row.getString("estado")
                        )
                    })
                }
                401, 403 -> ManagedAccountsResult.Unauthorized
                else -> ManagedAccountsResult.NetworkError
            }
        }.getOrElse { ManagedAccountsResult.NetworkError }
    }

    override suspend fun deleteAccount(accountId: String): ManagedAccountActionResult {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            return ManagedAccountActionResult.NotConfigured
        }
        val token = authRepository.validAccessTokenOrNull() ?: return ManagedAccountActionResult.Unauthorized
        return runCatching {
            val response = request(
                "/rest/v1/rpc/eliminar_cuenta_admin",
                JSONObject().put("p_id_usuario", accountId).toString(),
                token
            )
            when {
                response.code in 200..299 && response.body.trim() == "true" -> ManagedAccountActionResult.Success
                response.code in 200..299 && response.body.trim() == "false" -> ManagedAccountActionResult.NotFound
                response.code == 401 || response.code == 403 -> ManagedAccountActionResult.Unauthorized
                else -> ManagedAccountActionResult.NetworkError
            }
        }.getOrElse { ManagedAccountActionResult.NetworkError }
    }

    override suspend fun requestPasswordReset(email: String): PasswordResetResult =
        if (email.isBlank()) PasswordResetResult.NetworkError
        else authRepository.requestPasswordReset(email)

    private fun request(path: String, body: String, token: String): HttpResponse {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(val code: Int, val body: String)
}
