package cl.kmat.ia.data.teacher

import cl.kmat.ia.BuildConfig
import cl.kmat.ia.domain.model.ExerciseHistoryEntry
import cl.kmat.ia.domain.model.GroupStudentReport
import cl.kmat.ia.domain.model.ReinforcementOption
import cl.kmat.ia.domain.model.StudentReport
import cl.kmat.ia.domain.model.SessionProgressPoint
import cl.kmat.ia.domain.model.TeachingGroup
import cl.kmat.ia.domain.model.TeachingResult
import cl.kmat.ia.domain.model.TeachingStudent
import cl.kmat.ia.domain.repository.AuthRepository
import cl.kmat.ia.domain.repository.TeachingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseTeachingRepository(
    private val authRepository: AuthRepository
) : TeachingRepository {
    override suspend fun listGroups(): TeachingResult<List<TeachingGroup>> = call("docente_listar_grupos", JSONObject()) { body ->
        val rows = JSONArray(body)
        List(rows.length()) { index -> rows.getJSONObject(index).toGroup() }
    }

    override suspend fun createGroup(name: String): TeachingResult<TeachingGroup> = call(
        "docente_crear_grupo", JSONObject().put("p_nombre", name.trim())
    ) { body ->
        val rows = JSONArray(body)
        require(rows.length() > 0)
        rows.getJSONObject(0).toGroup()
    }

    override suspend fun listStudents(groupId: String): TeachingResult<List<TeachingStudent>> = call(
        "docente_listar_estudiantes", JSONObject().put("p_id_grupo", groupId)
    ) { body ->
        val rows = JSONArray(body)
        List(rows.length()) { index ->
            rows.getJSONObject(index).let { row ->
                TeachingStudent(row.getString("id_estudiante"), row.getString("nombre_mostrado"), row.getString("codigo_nivel_educativo"))
            }
        }
    }

    override suspend fun listReinforcementOptions(): TeachingResult<List<ReinforcementOption>> = call(
        "docente_catalogo_refuerzos", JSONObject()
    ) { body ->
        val rows = JSONArray(body)
        List(rows.length()) { index ->
            rows.getJSONObject(index).let { row ->
                ReinforcementOption(row.getString("id_contenido"), row.getString("nombre"), row.getInt("ejercicios_disponibles"))
            }
        }
    }

    override suspend fun assignReinforcement(studentId: String, contentId: String, count: Int, note: String): TeachingResult<Int> = call(
        "docente_asignar_refuerzo",
        JSONObject().put("p_id_estudiante", studentId).put("p_id_contenido", contentId)
            .put("p_cantidad", count).put("p_observacion", note.trim().ifEmpty { JSONObject.NULL })
    ) { body -> body.trim().toInt() }

    override suspend fun getStudentReport(studentId: String): TeachingResult<StudentReport> = call(
        "docente_reporte_estudiante", JSONObject().put("p_id_estudiante", studentId)
    ) { body ->
        val root = JSONObject(body)
        val history = root.optJSONArray("historial") ?: JSONArray()
        val progress = root.optJSONArray("progreso_sesiones") ?: JSONArray()
        StudentReport(
            studentId = root.getString("id_estudiante"), studentName = root.getString("nombre"),
            educationLevel = root.getString("nivel"), sessions = root.getInt("sesiones"),
            correct = root.getInt("correctas"), incorrect = root.getInt("incorrectas"),
            sessionProgress = List(progress.length()) { index -> progress.getJSONObject(index).let { point ->
                SessionProgressPoint(point.optString("fecha"), point.optInt("correctas"), point.optInt("total"))
            } },
            history = List(history.length()) { index ->
                history.getJSONObject(index).let { item ->
                    ExerciseHistoryEntry(
                        activity = item.optString("actividad"), type = item.optString("tipo"),
                        correct = if (item.isNull("correcta")) null else item.getBoolean("correcta"),
                        answer = item.optString("respuesta"), durationMillis = item.optLong("duracion_ms"),
                        date = item.optString("fecha")
                    )
                }
            }
        )
    }

    override suspend fun getGroupReport(groupId: String): TeachingResult<List<GroupStudentReport>> = call(
        "docente_reporte_grupo", JSONObject().put("p_id_grupo", groupId)
    ) { body ->
        val rows = JSONArray(body)
        List(rows.length()) { index ->
            rows.getJSONObject(index).let { row ->
                GroupStudentReport(
                    studentId = row.getString("id_estudiante"), name = row.getString("nombre"),
                    educationLevel = row.getString("nivel"), sessions = row.getInt("sesiones"),
                    correct = row.getInt("correctas"), incorrect = row.getInt("incorrectas"),
                    lastActivity = if (row.isNull("ultima_actividad")) null else row.getString("ultima_actividad")
                )
            }
        }
    }

    private suspend fun <T> call(function: String, body: JSONObject, decode: (String) -> T): TeachingResult<T> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return@withContext TeachingResult.NotConfigured
            val token = authRepository.validAccessTokenOrNull() ?: return@withContext TeachingResult.Unauthorized
            runCatching {
                val response = request(function, body, token)
                when {
                    response.code in 200..299 -> TeachingResult.Success(decode(response.body))
                    response.code == 401 || response.code == 403 -> TeachingResult.Unauthorized
                    response.code in 400..499 -> TeachingResult.InvalidRequest
                    else -> TeachingResult.NetworkError
                }
            }.getOrElse { TeachingResult.NetworkError }
        }

    private fun request(function: String, body: JSONObject, token: String): HttpResponse {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/$function")
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

    private fun JSONObject.toGroup() = TeachingGroup(
        getString("id_grupo"), getString("nombre"), getString("codigo_union"), getInt("cantidad_estudiantes")
    )

    private data class HttpResponse(val code: Int, val body: String)
}
