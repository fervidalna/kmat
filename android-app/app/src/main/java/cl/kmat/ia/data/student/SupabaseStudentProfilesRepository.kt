package cl.kmat.ia.data.student

import android.util.Log
import cl.kmat.ia.BuildConfig
import androidx.room.withTransaction
import cl.kmat.ia.data.local.OfflineDatabase
import cl.kmat.ia.domain.model.CreateStudentProfileResult
import cl.kmat.ia.domain.model.DeleteStudentProfileResult
import cl.kmat.ia.domain.model.StudentProfile
import cl.kmat.ia.domain.model.StudentProfilesResult
import cl.kmat.ia.domain.model.JoinTeachingGroupResult
import cl.kmat.ia.domain.repository.AuthRepository
import cl.kmat.ia.domain.repository.StudentProfilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseStudentProfilesRepository(
    private val authRepository: AuthRepository,
    private val offlineDatabase: OfflineDatabase
) : StudentProfilesRepository {
    override suspend fun listMine(): StudentProfilesResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext StudentProfilesResult.NotConfigured
        val token = authRepository.validAccessTokenOrNull()
            ?: return@withContext StudentProfilesResult.Unauthorized
        try {
            val response = request("/rest/v1/rpc/listar_mis_estudiantes", JSONObject(), token)
            if (response.code !in 200..299) {
                // No registrar tokens, datos de estudiantes ni el cuerpo de la respuesta.
                val code = runCatching { JSONObject(response.body).optString("code") }.getOrDefault("")
                Log.w("StudentProfiles", "List request failed: HTTP ${response.code}, code=$code")
            }
            decodeStudentProfilesResponse(response.code, response.body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StudentProfilesResult.NetworkError
        }
    }

    override suspend fun createMine(name: String, educationLevel: String): CreateStudentProfileResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext CreateStudentProfileResult.NotConfigured
        val token = authRepository.validAccessTokenOrNull()
            ?: return@withContext CreateStudentProfileResult.Unauthorized
        runCatching {
            val response = request(
                "/rest/v1/rpc/crear_mi_estudiante",
                JSONObject()
                    .put("p_nombre", name.trim())
                    .put("p_codigo_nivel_educativo", educationLevel)
                    .put("p_version_consentimiento", CONSENT_VERSION),
                token
            )
            when (response.code) {
                in 200..299 -> {
                    val rows = JSONArray(response.body)
                    if (rows.length() == 0) CreateStudentProfileResult.NetworkError
                    else CreateStudentProfileResult.Success(rows.getJSONObject(0).toProfile())
                }
                401, 403 -> CreateStudentProfileResult.Unauthorized
                400, 409, 422 -> CreateStudentProfileResult.InvalidRequest
                else -> CreateStudentProfileResult.NetworkError
            }
        }.getOrElse { CreateStudentProfileResult.NetworkError }
    }

    override suspend fun deleteMine(studentId: String): DeleteStudentProfileResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext DeleteStudentProfileResult.NotConfigured
        val token = authRepository.validAccessTokenOrNull()
            ?: return@withContext DeleteStudentProfileResult.Unauthorized
        try {
            val response = request(
                "/rest/v1/rpc/eliminar_mi_estudiante",
                JSONObject().put("p_id_estudiante", studentId),
                token
            )
            val result = decodeDeleteStudentProfileResponse(response.code, response.body)
            if (result == DeleteStudentProfileResult.Success) {
                if (authRepository.activeStudentIdOrNull() == studentId) {
                    authRepository.clearActiveStudentId()
                }
                return@withContext try {
                    offlineDatabase.withTransaction {
                        val dao = offlineDatabase.learningDao()
                        dao.deletePendingSyncForStudent(studentId)
                        dao.deleteMetricsForStudent(studentId)
                        dao.deleteStrokesForStudent(studentId)
                        dao.deleteAttemptsForStudent(studentId)
                        dao.deleteSessionsForStudent(studentId)
                        dao.deleteDiagnosticResult(studentId)
                    }
                    DeleteStudentProfileResult.Success
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Log.e("StudentProfiles", "Remote profile deleted but local cleanup failed")
                    DeleteStudentProfileResult.LocalCleanupFailed
                }
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DeleteStudentProfileResult.NetworkError
        }
    }

    override suspend fun joinTeachingGroup(studentId: String, code: String): JoinTeachingGroupResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext JoinTeachingGroupResult.NotConfigured
        val token = authRepository.validAccessTokenOrNull() ?: return@withContext JoinTeachingGroupResult.Unauthorized
        runCatching {
            val response = request(
                "/rest/v1/rpc/responsable_unirse_grupo",
                JSONObject().put("p_id_estudiante", studentId).put("p_codigo_union", code.trim().uppercase()),
                token
            )
            when {
                response.code in 200..299 -> JoinTeachingGroupResult.Success(
                    org.json.JSONTokener(response.body).nextValue() as? String ?: "grupo"
                )
                response.code == 401 || response.code == 403 -> JoinTeachingGroupResult.Unauthorized
                response.code == 409 || runCatching { JSONObject(response.body).optString("code") }.getOrNull() == "23505" ->
                    JoinTeachingGroupResult.AlreadyJoined
                response.code in 400..499 -> JoinTeachingGroupResult.InvalidCode
                else -> JoinTeachingGroupResult.NetworkError
            }
        }.getOrElse { JoinTeachingGroupResult.NetworkError }
    }

    private fun JSONObject.toProfile() = StudentProfile(
        id = getString("id_estudiante"),
        name = getString("nombre_mostrado"),
        educationLevel = getString("codigo_nivel_educativo")
    )

    private fun request(path: String, body: JSONObject, token: String): HttpResponse {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
        require(url.protocol.equals("https", ignoreCase = true))
        val connection = (url.openConnection() as HttpURLConnection).apply {
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
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun isConfigured() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    private data class HttpResponse(val code: Int, val body: String)

    private companion object {
        // Incrementar si cambia el texto legal presentado al responsable.
        const val CONSENT_VERSION = "kmat-uso-plataforma-v1"
    }
}

/** Un fallo de permisos o una respuesta invalida nunca representan una lista vacia. */
internal fun decodeStudentProfilesResponse(status: Int, body: String): StudentProfilesResult = when (status) {
    in 200..299 -> try {
        val rows = JSONArray(body)
        StudentProfilesResult.Success(List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            StudentProfile(
                id = row.getString("id_estudiante"),
                name = row.getString("nombre_mostrado"),
                educationLevel = row.getString("codigo_nivel_educativo")
            )
        })
    } catch (_: Exception) {
        StudentProfilesResult.InvalidResponse
    }
    401 -> StudentProfilesResult.Unauthorized
    403 -> StudentProfilesResult.PermissionDenied
    else -> StudentProfilesResult.ServiceUnavailable
}

internal fun decodeDeleteStudentProfileResponse(status: Int, body: String): DeleteStudentProfileResult = when {
    status in 200..299 && body.trim() == "true" -> DeleteStudentProfileResult.Success
    status == 401 -> DeleteStudentProfileResult.Unauthorized
    status == 403 -> DeleteStudentProfileResult.PermissionDenied
    status == 400 && runCatching { JSONObject(body).optString("code") }.getOrNull() == "KM001" ->
        DeleteStudentProfileResult.SharedProfile
    else -> DeleteStudentProfileResult.ServiceUnavailable
}
