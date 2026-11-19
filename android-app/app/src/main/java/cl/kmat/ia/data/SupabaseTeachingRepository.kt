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
import cl.kmat.ia.domain.model.StudentProgressSnapshot
import cl.kmat.ia.domain.model.ReinforcementSuggestion
import cl.kmat.ia.domain.model.SubjectSuggestionState
import cl.kmat.ia.domain.model.SuggestionResult
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

    override suspend fun deleteGroup(groupId: String): TeachingResult<Boolean> = call(
        "docente_eliminar_grupo", JSONObject().put("p_id_grupo", groupId)
    ) { body -> body.trim().equals("true", ignoreCase = true) }

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

    override suspend fun listReinforcementOptionsByLevel(): TeachingResult<List<ReinforcementOption>> = call(
        "docente_catalogo_refuerzos_nivel", JSONObject()
    ) { body ->
        val rows = JSONArray(body)
        List(rows.length()) { index -> rows.getJSONObject(index).let { row ->
            ReinforcementOption(row.getString("id_contenido"), row.getString("nombre"),
                row.getInt("ejercicios_disponibles"), row.getString("eje"), row.getInt("dificultad"))
        } }
    }

    override suspend fun assignReinforcementByLevel(studentId: String, option: ReinforcementOption, count: Int, note: String): TeachingResult<Int> = call(
        "docente_asignar_refuerzo_nivel", JSONObject().put("p_id_estudiante", studentId)
            .put("p_id_contenido", option.contentId).put("p_eje", option.axis)
            .put("p_dificultad", option.difficulty).put("p_cantidad", count)
            .put("p_observacion", note.trim().ifEmpty { JSONObject.NULL })
    ) { body -> body.trim().toInt() }

    override suspend fun getStudentReport(studentId: String): TeachingResult<StudentReport> = call(
        "docente_reporte_estudiante", JSONObject().put("p_id_estudiante", studentId)
    ) { body ->
        val root = JSONObject(body)
        val history = root.optJSONArray("historial") ?: JSONArray()
        val progress = root.optJSONArray("progreso_sesiones") ?: JSONArray()
        StudentReport(
            studentId = root.getString("id_estudiante"), studentName = root.getString("nombre"),
            educationLevel = root.getString("nivel"),
            performanceLevel = root.optString("nivel_desempeno").takeIf { it.isNotBlank() && it != "null" },
            sessions = root.getInt("sesiones"),
            correct = root.getInt("correctas"), incorrect = root.getInt("incorrectas"),
            sessionProgress = List(progress.length()) { index -> progress.getJSONObject(index).let { point ->
                SessionProgressPoint(point.optString("fecha"), point.optInt("correctas"), point.optInt("total"))
            } },
            history = List(history.length()) { index ->
                history.getJSONObject(index).let { item ->
                    ExerciseHistoryEntry(
                        activity = item.optString("actividad"), type = item.optString("tipo"),
                        area = item.optString("area"),
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

    override suspend fun getStudentProgress(studentId: String): TeachingResult<StudentProgressSnapshot> = withContext(Dispatchers.IO) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return@withContext TeachingResult.NotConfigured
        val token = authRepository.validAccessTokenOrNull() ?: return@withContext TeachingResult.Unauthorized
        try {
            val all = mutableListOf<JSONObject>()
            var after: String? = null
            do {
                val body = JSONObject().put("p_student_id", studentId).put("p_after", after ?: JSONObject.NULL)
                val response = request("docente_descargar_aprendizaje", body, token)
                if (response.code == 401 || response.code == 403) return@withContext TeachingResult.Unauthorized
                if (response.code !in 200..299) return@withContext TeachingResult.NetworkError
                val rows = JSONObject(response.body).getJSONArray("sessions")
                for (index in 0 until rows.length()) all.add(rows.getJSONObject(index))
                val next = if (rows.length() == 0) null else rows.getJSONObject(rows.length() - 1).getString("id")
                check(next == null || next != after)
                after = next
            } while (rows.length() == 50)
            TeachingResult.Success(TeacherProgressMapper.map(all))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            TeachingResult.NetworkError
        }
    }

    override suspend fun getReinforcementSuggestions(studentId: String): SuggestionResult = suggestionRequest(studentId, "read", null)

    override suspend fun generateReinforcementSuggestion(studentId: String): SuggestionResult =
        suggestionRequest(studentId, "generate_global", null)

    private suspend fun suggestionRequest(studentId: String, action: String, axis: String?): SuggestionResult = withContext(Dispatchers.IO) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return@withContext SuggestionResult.NotConfigured
        try {
            val token = authRepository.validAccessTokenOrNull() ?: return@withContext SuggestionResult.Unauthorized
            val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/sugerencia-refuerzo")
            require(url.protocol.equals("https", ignoreCase = true))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 65_000
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                val payload = JSONObject().put("action", action).put("student_id", studentId)
                if (axis != null) payload.put("axis", axis)
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
                when (val status = connection.responseCode) {
                    200 -> {
                        val rows = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).getJSONArray("subjects")
                        SuggestionResult.Success(List(rows.length()) { index ->
                            val row = rows.getJSONObject(index)
                            val saved = row.optJSONObject("suggestion")?.let {
                                ReinforcementSuggestion(it.getString("topic"), it.getString("evidence"), it.getString("activity"),
                                    it.getString("generated_at"), it.getInt("practice_count"))
                            }
                            SubjectSuggestionState(row.getString("axis"), row.getInt("practice_count"), row.getInt("minimum"),
                                row.getInt("new_answers"), row.getBoolean("can_generate"), saved)
                        })
                    }
                    else -> {
                        val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        val json = body?.let { runCatching { JSONObject(it) }.getOrNull() }
                        val detail = listOf("error", "message", "msg", "code").firstNotNullOfOrNull { key ->
                            (json?.opt(key) as? String)?.takeIf(String::isNotBlank)
                        } ?: connection.responseMessage.orEmpty()
                        SuggestionResult.Failure("Servicio de sugerencias (HTTP $status). ${detail.take(300)}")
                    }
                }
            } finally { connection.disconnect() }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            val detail = when (error) {
                is java.net.UnknownHostException -> "No se pudo resolver la dirección de Supabase."
                is java.net.SocketTimeoutException -> "Se agotó el tiempo de espera de la solicitud."
                is javax.net.ssl.SSLException -> "Falló la conexión segura con Supabase."
                is java.net.ConnectException -> "No se pudo establecer la conexión con Supabase."
                is org.json.JSONException -> "El servicio devolvió una respuesta con un formato inesperado."
                else -> "Falló la solicitud de sugerencia."
            }
            SuggestionResult.Failure("$detail (${error.javaClass.simpleName})")
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
