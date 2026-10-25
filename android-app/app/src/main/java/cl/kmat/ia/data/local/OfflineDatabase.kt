package cl.kmat.ia.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "local_exercise")
data class LocalExerciseEntity(
    @PrimaryKey val id: String,
    val content: String,
    val statement: String,
    val expectedAnswer: String,
    val difficulty: Int,
    val updatedAt: Long,
    val expectedTimeMillis: Long = 30_000L,
    val axis: String = "NUMEROS_OPERACIONES",
    val contentCode: String = "GENERAL",
    val educationLevel: String = "AMBOS",
    val visualSpec: String = ""
)

@Entity(tableName = "learning_session")
data class LearningSessionEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val localDate: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "ACTIVE",
    val sessionType: String = "PRACTICA"
)

@Entity(tableName = "student_diagnostic_result")
data class StudentDiagnosticResultEntity(
    @PrimaryKey val studentId: String,
    val overallLevel: Int,
    val axisResultsJson: String,
    val questionCount: Int,
    val completedAt: Long
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

data class PracticeProgressRow(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val sessionStatus: String,
    val exerciseId: String,
    val exerciseName: String,
    val answer: String,
    val expectedAnswer: String,
    val isCorrect: Boolean,
    val elapsedMillis: Long
)

data class RecentPracticePerformanceRow(
    val exerciseId: String,
    val isCorrect: Boolean,
    val elapsedMillis: Long,
    val axis: String,
    val expectedTimeMillis: Long,
    val difficulty: Int
)

@Dao
interface OfflineLearningDao {
    @Query("select * from local_exercise order by updatedAt desc limit 1")
    suspend fun getCurrentExercise(): LocalExerciseEntity?

    @Query("select * from local_exercise where id = :exerciseId limit 1")
    suspend fun getExercise(exerciseId: String): LocalExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(exercise: LocalExerciseEntity)

    @Query("select * from learning_session where studentId = :studentId and localDate = :localDate and sessionType = :sessionType and status = 'ACTIVE' limit 1")
    suspend fun getActiveSession(studentId: String, localDate: String, sessionType: String): LearningSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: LearningSessionEntity)

    @Query("select * from learning_session where id = :sessionId limit 1")
    suspend fun getSession(sessionId: String): LearningSessionEntity?

    @Query("update learning_session set status = 'COMPLETED', endedAt = :endedAt where id = :sessionId and status = 'ACTIVE'")
    suspend fun completeSession(sessionId: String, endedAt: Long)

    @Query("select count(*) from exercise_attempt where sessionId = :sessionId and exerciseId = :exerciseId")
    suspend fun countAttempts(sessionId: String, exerciseId: String): Int

    @Query("""
        select * from exercise_attempt
        where sessionId = (
            select id from learning_session
            where studentId = :studentId and sessionType = 'PRACTICA' and status = 'COMPLETED'
            order by endedAt desc limit 1
        )
        order by createdAt asc
    """)
    suspend fun getLatestCompletedSessionAttempts(studentId: String): List<ExerciseAttemptEntity>

    @Query("""
        select a.exerciseId as exerciseId, a.isCorrect as isCorrect, a.elapsedMillis as elapsedMillis,
               e.axis as axis, e.expectedTimeMillis as expectedTimeMillis, e.difficulty as difficulty
        from exercise_attempt a
        join local_exercise e on e.id = a.exerciseId
        join learning_session s on s.id = a.sessionId
        where s.studentId = :studentId and s.sessionType = 'PRACTICA' and s.status = 'COMPLETED'
          and s.id = (
            select id from learning_session
            where studentId = :studentId and sessionType = 'PRACTICA' and status = 'COMPLETED'
            order by endedAt desc limit 1
          )
        order by a.createdAt asc
    """)
    suspend fun getLatestCompletedPracticePerformances(studentId: String): List<RecentPracticePerformanceRow>

    @Query("""
        select s.id as sessionId, s.startedAt as startedAt, s.endedAt as endedAt,
               s.status as sessionStatus, e.id as exerciseId, e.content as exerciseName,
               a.answer as answer, e.expectedAnswer as expectedAnswer,
               a.isCorrect as isCorrect, a.elapsedMillis as elapsedMillis
        from learning_session s
        join exercise_attempt a on a.sessionId = s.id
        join local_exercise e on e.id = a.exerciseId
        where s.studentId = :studentId
        order by s.startedAt desc, a.createdAt asc
    """)
    suspend fun getPracticeProgress(studentId: String): List<PracticeProgressRow>

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

    @Query("""delete from pending_sync where instr(payload, '"student_local_id":"' || :studentId || '"') > 0""")
    suspend fun deletePendingSyncForStudent(studentId: String)

    @Query("delete from performance_metric where attemptId in (select a.id from exercise_attempt a join learning_session s on s.id = a.sessionId where s.studentId = :studentId)")
    suspend fun deleteMetricsForStudent(studentId: String)

    @Query("delete from handwriting_stroke where attemptId in (select a.id from exercise_attempt a join learning_session s on s.id = a.sessionId where s.studentId = :studentId)")
    suspend fun deleteStrokesForStudent(studentId: String)

    @Query("delete from exercise_attempt where sessionId in (select id from learning_session where studentId = :studentId)")
    suspend fun deleteAttemptsForStudent(studentId: String)

    @Query("delete from learning_session where studentId = :studentId")
    suspend fun deleteSessionsForStudent(studentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDiagnosticResult(result: StudentDiagnosticResultEntity)

    @Query("select * from student_diagnostic_result where studentId = :studentId limit 1")
    suspend fun getDiagnosticResult(studentId: String): StudentDiagnosticResultEntity?

    @Query("delete from student_diagnostic_result where studentId = :studentId")
    suspend fun deleteDiagnosticResult(studentId: String)
}

@Database(
    entities = [
        LocalExerciseEntity::class,
        LearningSessionEntity::class,
        ExerciseAttemptEntity::class,
        HandwritingStrokeEntity::class,
        PerformanceMetricEntity::class,
        PendingSyncEntity::class,
        StudentDiagnosticResultEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun learningDao(): OfflineLearningDao
}

object OfflineDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE local_exercise ADD COLUMN expectedTimeMillis INTEGER NOT NULL DEFAULT 30000")
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE local_exercise ADD COLUMN axis TEXT NOT NULL DEFAULT 'NUMEROS_OPERACIONES'")
            database.execSQL("ALTER TABLE local_exercise ADD COLUMN contentCode TEXT NOT NULL DEFAULT 'GENERAL'")
            database.execSQL("ALTER TABLE local_exercise ADD COLUMN educationLevel TEXT NOT NULL DEFAULT 'AMBOS'")
            database.execSQL("ALTER TABLE learning_session ADD COLUMN sessionType TEXT NOT NULL DEFAULT 'PRACTICA'")
            database.execSQL("CREATE TABLE IF NOT EXISTS student_diagnostic_result (studentId TEXT NOT NULL PRIMARY KEY, overallLevel INTEGER NOT NULL, axisResultsJson TEXT NOT NULL, questionCount INTEGER NOT NULL, completedAt INTEGER NOT NULL)")
        }
    }
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE local_exercise ADD COLUMN visualSpec TEXT NOT NULL DEFAULT ''")
        }
    }
}
