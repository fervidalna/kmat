package cl.kmat.ia.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import cl.kmat.ia.BuildConfig
import cl.kmat.ia.KMatApplication
import cl.kmat.ia.data.local.OfflineDatabase
import cl.kmat.ia.data.local.PendingSyncEntity
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

sealed interface RemoteSyncResult {
    data object Success : RemoteSyncResult
    data object RetryableFailure : RemoteSyncResult
    data object Disabled : RemoteSyncResult
}

class SupabaseSyncClient(private val authRepository: AuthRepository) {
    suspend fun upload(items: List<PendingSyncEntity>): RemoteSyncResult = withContext(Dispatchers.IO) {
        if (
            BuildConfig.SUPABASE_URL.isBlank() ||
            BuildConfig.SUPABASE_ANON_KEY.isBlank() ||
            BuildConfig.SUPABASE_EXERCISE_VERSION_ID.isBlank()
        ) {
            return@withContext RemoteSyncResult.Disabled
        }
        runCatching {
            val token = authRepository.validAccessTokenOrNull() ?: return@withContext RemoteSyncResult.Disabled
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/sync-learning-data"
            require(URL(endpoint).protocol.equals("https", ignoreCase = true)) { "Supabase debe usar HTTPS" }
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }
            val events = JSONArray(items.map { item ->
                JSONObject()
                    .put("id", item.id)
                    .put("operation", item.operation)
                    .put("payload", JSONObject(item.payload).let { payload ->
                        payload.put("student_local_id", payload.optString("student_local_id"))
                            .put(
                                "exercise_version_remote_id",
                                payload.optString("exercise_version_remote_id").ifBlank { BuildConfig.SUPABASE_EXERCISE_VERSION_ID }
                            )
                    })
            })
            connection.outputStream.bufferedWriter().use { it.write(JSONObject().put("events", events).toString()) }
            val responseCode = connection.responseCode
            connection.disconnect()
            if (responseCode in 200..299) RemoteSyncResult.Success else RemoteSyncResult.RetryableFailure
        }.getOrElse { RemoteSyncResult.RetryableFailure }
    }

    suspend fun downloadProgress(): List<RemoteProgressRecord>? = withContext(Dispatchers.IO) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return@withContext null
        runCatching {
            val token = authRepository.validAccessTokenOrNull() ?: return@withContext null
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/sync-learning-data"
            require(URL(endpoint).protocol.equals("https", ignoreCase = true))
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2_000
                readTimeout = 2_500
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                connection.outputStream.bufferedWriter().use {
                    it.write(JSONObject().put("action", "download_progress").toString())
                }
                if (connection.responseCode !in 200..299) return@withContext null
                val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val progress = response.getJSONArray("progress")
                List(progress.length()) { index ->
                    val row = progress.getJSONObject(index)
                    RemoteProgressRecord(
                        exerciseType = row.optString("exercise_local_id").ifBlank { row.getString("exercise_type") },
                        startedAt = java.time.Instant.parse(row.getString("started_at")).toEpochMilli(),
                        isCorrect = row.getBoolean("is_correct"),
                        elapsedMillis = row.getLong("elapsed_millis")
                    )
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    suspend fun downloadTeacherAssignments(studentId: String): List<Exercise>? = withContext(Dispatchers.IO) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return@withContext null
        runCatching {
            val token = authRepository.validAccessTokenOrNull() ?: return@withContext null
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/sync-learning-data"
            require(URL(endpoint).protocol.equals("https", ignoreCase = true))
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2_000
                readTimeout = 2_500
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                connection.outputStream.bufferedWriter().use {
                    it.write(JSONObject().put("action", "download_teacher_assignments")
                        .put("student_local_id", studentId).toString())
                }
                if (connection.responseCode !in 200..299) return@withContext null
                val assignments = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("assignments")
                List(assignments.length()) { index ->
                    val row = assignments.getJSONObject(index)
                    Exercise(
                        id = row.getString("assignment_id"),
                        content = row.getString("activity"),
                        statement = row.getString("statement"),
                        expectedAnswer = row.optString("solution"),
                        difficulty = row.optInt("difficulty", 1),
                        teacherAssignmentId = row.getString("assignment_id"),
                        remoteExerciseVersionId = row.getString("exercise_version_id")
                    )
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

data class RemoteProgressRecord(
    val exerciseType: String,
    val startedAt: Long,
    val isCorrect: Boolean,
    val elapsedMillis: Long
)

class OfflineSyncCoordinator(
    private val database: OfflineDatabase,
    private val client: SupabaseSyncClient
) {
    suspend fun syncPending(): RemoteSyncResult {
        val items = database.learningDao().pendingSync(limit = 50)
        if (items.isEmpty()) return RemoteSyncResult.Success
        return client.upload(items).also { result ->
            when (result) {
                RemoteSyncResult.Success -> database.learningDao().deleteSyncItems(items.map { it.id })
                RemoteSyncResult.RetryableFailure -> database.learningDao().increaseRetryCount(items.map { it.id })
                RemoteSyncResult.Disabled -> Unit
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
