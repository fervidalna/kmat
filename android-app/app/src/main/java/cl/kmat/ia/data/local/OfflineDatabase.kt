package cl.kmat.ia.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "local_exercise")
data class LocalExerciseEntity(
    @PrimaryKey val id: String,
    val content: String,
    val statement: String,
    val expectedAnswer: String,
    val difficulty: Int,
    val updatedAt: Long
)

@Entity(tableName = "learning_session")
data class LearningSessionEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val localDate: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "ACTIVE"
)

@Entity(tableName = "exercise_attempt")
data class ExerciseAttemptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val answer: String,
    val isCorrect: Boolean,
    val attemptNumber: Int,
    val elapsedMillis: Long,
    val createdAt: Long
)

@Entity(tableName = "handwriting_stroke")
data class HandwritingStrokeEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val strokeNumber: Int,
    val points: String,
    val createdAt: Long
)

@Entity(tableName = "performance_metric")
data class PerformanceMetricEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val name: String,
    val value: Double,
    val recordedAt: Long
)

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey val id: String,
    val operation: String,
    val payload: String,
    val createdAt: Long,
    val retryCount: Int = 0
)

@Dao
interface OfflineLearningDao {
    @Query("select * from local_exercise order by updatedAt desc limit 1")
    suspend fun getCurrentExercise(): LocalExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(exercise: LocalExerciseEntity)

    @Query("select * from learning_session where studentId = :studentId and localDate = :localDate and status = 'ACTIVE' limit 1")
    suspend fun getActiveSession(studentId: String, localDate: String): LearningSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: LearningSessionEntity)

    @Query("select * from learning_session where id = :sessionId limit 1")
    suspend fun getSession(sessionId: String): LearningSessionEntity?

    @Query("select count(*) from exercise_attempt where sessionId = :sessionId and exerciseId = :exerciseId")
    suspend fun countAttempts(sessionId: String, exerciseId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(attempt: ExerciseAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStrokes(strokes: List<HandwritingStrokeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMetrics(metrics: List<PerformanceMetricEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueueSync(item: PendingSyncEntity)

    @Query("select * from pending_sync order by createdAt asc limit :limit")
    suspend fun pendingSync(limit: Int): List<PendingSyncEntity>

    @Query("delete from pending_sync where id in (:ids)")
    suspend fun deleteSyncItems(ids: List<String>)

    @Query("update pending_sync set retryCount = retryCount + 1 where id in (:ids)")
    suspend fun increaseRetryCount(ids: List<String>)
}

@Database(
    entities = [
        LocalExerciseEntity::class,
        LearningSessionEntity::class,
        ExerciseAttemptEntity::class,
        HandwritingStrokeEntity::class,
        PerformanceMetricEntity::class,
        PendingSyncEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun learningDao(): OfflineLearningDao
}
