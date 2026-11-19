package cl.kmat.ia.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.*
import cl.kmat.ia.BuildConfig
import cl.kmat.ia.KMatApplication
import cl.kmat.ia.data.local.*
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

sealed interface RemoteSyncResult {
    data object Success : RemoteSyncResult
    data object RetryableFailure : RemoteSyncResult
    data object Disabled : RemoteSyncResult
}

interface LearningSyncTransport {
    suspend fun connect(): LearningSyncConnection?
}

interface LearningSyncConnection {
    suspend fun studentIds(): List<String>
    suspend fun upload(items: List<PendingSyncEntity>)
    suspend fun download(studentId: String, after: String?): JSONArray
}

class SupabaseSyncClient(private val authRepository: AuthRepository) : LearningSyncTransport {
    suspend fun claimSession(studentId: String, type: String, proposedId: String, startedAt: Long): String? = try {
        val token = authRepository.validAccessTokenOrNull()
        if (token == null || BuildConfig.SUPABASE_URL.isBlank()) null else
            request(token, JSONObject().put("action", "claim_learning_session")
                .put("student_local_id", studentId).put("session_type", type)
                .put("session_id", proposedId).put("started_at", startedAt)).getString("session_id")
    } catch (error: CancellationException) { throw error }
      catch (_: Exception) { null }

    override suspend fun connect(): LearningSyncConnection? {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return null
        // Capture one credential for the entire cycle, even if the user changes accounts.
        val token = authRepository.validAccessTokenOrNull() ?: return null
        return object : LearningSyncConnection {
            override suspend fun studentIds(): List<String> {
                val rows = request(token, JSONObject().put("action", "sync_profiles")).getJSONArray("student_ids")
                return List(rows.length()) { rows.getString(it) }
            }
            override suspend fun upload(items: List<PendingSyncEntity>) {
                val events = JSONArray(items.map { item ->
                    val payload = JSONObject(item.payload)
                    if (item.operation == "ATTEMPT_RECORDED") payload.withDefaultExerciseVersion(BuildConfig.SUPABASE_EXERCISE_VERSION_ID)
                    JSONObject().put("id", item.id).put("operation", item.operation).put("payload", payload)
                })
                val response = request(token, JSONObject().put("events", events))
                check(response.getInt("synchronized") == items.size) { "Incomplete synchronization acknowledgement" }
            }
            override suspend fun download(studentId: String, after: String?): JSONArray =
                request(token, JSONObject().put("action", "download_learning_state")
                    .put("student_local_id", studentId).put("after", after)).getJSONArray("sessions")
        }
    }

    private suspend fun request(token: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/sync-learning-data"
        require(URL(endpoint).protocol.equals("https", ignoreCase = true))
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val details = runCatching { JSONObject(connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()) }.getOrNull()
                val code = details?.optString("error_code").orEmpty().takeIf { it.matches(Regex("[A-Z0-9]{5}")) }.orEmpty()
                val kind = details?.optString("error_kind").orEmpty().takeIf { it in setOf("El perfil no pertenece a esta cuenta", "La sesion no corresponde al perfil", "Informe de diagnostico invalido", "Actividad docente no disponible", "Plan de ejercicios invalido", "Lote de sincronizacion invalido", "otro") }.orEmpty()
                Log.w("KMatSync", "Servidor de sincronización: HTTP $status, código=$code, causa=$kind")
            }
            check(status in 200..299) { "Learning sync HTTP $status" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadTeacherAssignments(studentId: String): List<Exercise>? = try {
        val token = authRepository.validAccessTokenOrNull()
        if (token == null || BuildConfig.SUPABASE_URL.isBlank()) null else {
            val rows = request(token, JSONObject().put("action", "download_teacher_assignments")
                .put("student_local_id", studentId)).getJSONArray("assignments")
            (0 until rows.length()).mapNotNull { i -> rows.getJSONObject(i).let { row ->
                val axis = row.optString("axis").takeIf { it in cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine.AXES }
                    ?: return@mapNotNull null
                Exercise(id = row.getString("assignment_id"), content = row.getString("activity"),
                    statement = row.getString("statement"), expectedAnswer = row.optString("solution"),
                    difficulty = row.optInt("difficulty", 1), axis = axis, contentCode = "DOCENTE_CLASIFICADO", teacherAssignmentId = row.getString("assignment_id"),
                    remoteExerciseVersionId = row.getString("exercise_version_id"))
            } }
        }
    } catch (error: CancellationException) { throw error }
      catch (_: Exception) { null }
}

internal fun JSONObject.withDefaultExerciseVersion(defaultVersionId: String): JSONObject = apply {
    if (isNull("exercise_version_remote_id") || optString("exercise_version_remote_id").isBlank()) {
        put("exercise_version_remote_id", defaultVersionId)
    }
}

class OfflineSyncCoordinator(
    private val database: OfflineDatabase,
    private val client: LearningSyncTransport
) {
    private val mutex = Mutex()

    suspend fun syncPending(selectedStudentId: String? = null): RemoteSyncResult = mutex.withLock {
        try {
            val connection = client.connect() ?: return@withLock RemoteSyncResult.Disabled
            val dao = database.learningDao()
            var failed = false
            // Filter BEFORE batching. Another account's pending work must neither leak
            // nor prevent this account's queue from making progress.
            val studentIds = connection.studentIds()
            // An empty or unrelated profile list is not a successful sync for the selected student.
            if (selectedStudentId != null && selectedStudentId !in studentIds) {
                Log.w("KMatSync", "El perfil seleccionado no está asociado a esta cuenta.")
                return@withLock RemoteSyncResult.RetryableFailure
            }
            for (studentId in studentIds.filter { selectedStudentId == null || it == selectedStudentId }) {
                try {
                    while (true) {
                        val items = dao.pendingSyncForStudent(studentId, 50)
                        if (items.isEmpty()) break
                        try {
                            connection.upload(items)
                            dao.deleteSyncItems(items.map { it.id })
                        } catch (error: CancellationException) { throw error }
                          catch (error: Exception) {
                            dao.increaseRetryCount(items.map { it.id })
                            throw error
                        }
                    }
                } catch (error: CancellationException) { throw error }
                  catch (error: Exception) { Log.w("KMatSync", "No se pudieron enviar los cambios: ${error.javaClass.simpleName}"); failed = true }
                // Downloads remain useful even if one of this device's uploads failed.
                try {
                    var after: String? = null
                    do {
                        val page = connection.download(studentId, after)
                        mergePage(studentId, page)
                        val next = if (page.length() == 0) null else page.getJSONObject(page.length() - 1).getString("id")
                        check(next == null || next != after) { "Non-advancing sync page" }
                        after = next
                    } while (page.length() == 50)
                } catch (error: CancellationException) { throw error }
                  catch (error: Exception) { Log.w("KMatSync", "No se pudo descargar el progreso: ${error.javaClass.simpleName}"); failed = true }
            }
            if (failed) RemoteSyncResult.RetryableFailure else RemoteSyncResult.Success
        } catch (error: CancellationException) { throw error }
          catch (error: Exception) { Log.w("KMatSync", "No se pudo iniciar la sincronización: ${error.javaClass.simpleName}"); RemoteSyncResult.RetryableFailure }
    }

    internal suspend fun mergePage(studentId: String, page: JSONArray) = database.withTransaction {
        val dao = database.learningDao()
        for (i in 0 until page.length()) {
            val row = page.getJSONObject(i)
            require(row.getString("studentId") == studentId)
            val id = row.getString("id")
            val local = dao.getSession(id)
            require(local == null || local.studentId == studentId)
            val startedAt = row.getLong("startedAt")
            val remoteCompleted = row.getString("status") == "COMPLETED"
            val plan = row.optJSONArray("plan")
            val session = LearningSessionEntity(
                id, studentId, Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                startedAt,
                if (local?.status == "COMPLETED") local.endedAt else if (row.isNull("endedAt")) null else row.getLong("endedAt"),
                if (remoteCompleted || local?.status == "COMPLETED") "COMPLETED" else "ACTIVE",
                row.getString("sessionType"), plan?.toString() ?: local?.planJson.orEmpty()
            )
            // Completion is monotonic: a delayed ACTIVE snapshot never reopens it.
            dao.mergeSession(session)
            if (plan != null) for (j in 0 until plan.length()) {
                dao.upsertExercise(plan.getJSONObject(j).toSyncExercise().toLocalExercise())
            }
            val attempts = row.getJSONArray("attempts")
            for (j in 0 until attempts.length()) {
                val a = attempts.getJSONObject(j)
                a.optJSONObject("exercise")?.let { dao.upsertExercise(it.toSyncExercise().toLocalExercise()) }
                dao.importAttempt(ExerciseAttemptEntity(a.getString("id"), id, a.getString("exerciseId"),
                    a.getString("answer"), a.getBoolean("isCorrect"), a.getInt("attemptNumber"),
                    a.getLong("elapsedMillis"), a.getLong("createdAt")))
            }
            row.optJSONObject("report")?.let { report ->
                require(remoteCompleted && session.sessionType == "DIAGNOSTICO")
                val incoming = report.toDiagnosticResult(studentId)
                val current = dao.getDiagnosticResult(studentId)
                // The earliest finished initial diagnosis is canonical across devices.
                if (current == null || incoming.completedAt <= current.completedAt) {
                    dao.replaceDiagnosticResult(incoming)
                }
            }
        }
    }
}

class LearningSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = when (
        (applicationContext as KMatApplication).container.syncCoordinator.syncPending()
    ) {
        RemoteSyncResult.Success, RemoteSyncResult.Disabled -> Result.success()
        RemoteSyncResult.RetryableFailure -> Result.retry()
    }
}

object LearningSyncScheduler {
    private const val PERIODIC_WORK = "kmat_learning_sync_periodic"
    private const val IMMEDIATE_WORK = "kmat_learning_sync_immediate"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<LearningSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueWhenConnected(context: Context) {
        val request = OneTimeWorkRequestBuilder<LearningSyncWorker>()
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
