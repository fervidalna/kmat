package cl.kmat.ia.data.repository

import android.content.Context
import androidx.room.withTransaction
import cl.kmat.ia.data.local.ExerciseAttemptEntity
import cl.kmat.ia.data.local.HandwritingStrokeEntity
import cl.kmat.ia.data.local.LearningSessionEntity
import cl.kmat.ia.data.local.LocalExerciseEntity
import cl.kmat.ia.data.local.OfflineDatabase
import cl.kmat.ia.data.local.PendingSyncEntity
import cl.kmat.ia.data.local.PerformanceMetricEntity
import cl.kmat.ia.data.sync.LearningSyncScheduler
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.repository.SavedAttempt
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class RoomLearningSessionRepository(
    private val database: OfflineDatabase,
    private val applicationContext: Context
) : LearningSessionRepository {
    private val dao = database.learningDao()

    override suspend fun getDailySessionId(studentId: String): String = database.withTransaction {
        val today = LocalDate.now().toString()
        dao.getActiveSession(studentId, today)?.id ?: LearningSessionEntity(
            id = UUID.randomUUID().toString(),
            studentId = studentId,
            localDate = today,
            startedAt = System.currentTimeMillis()
        ).also { session ->
            dao.insertSession(session)
            dao.enqueueSync(
                PendingSyncEntity(
                    id = UUID.randomUUID().toString(),
                    operation = "SESSION_STARTED",
                    payload = JSONObject()
                        .put("local_session_id", session.id)
                        .put("student_local_id", session.studentId)
                        .put("local_date", session.localDate)
                        .put("started_at", session.startedAt)
                        .toString(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }.id
    }.also { LearningSyncScheduler.enqueueWhenConnected(applicationContext) }

    override suspend fun getOfflineExercise(): Exercise {
        val exercise = dao.getCurrentExercise() ?: LocalExerciseEntity(
            id = "offline-sum-1-1",
            content = "Suma",
            statement = "1 + 1 = ?",
            expectedAnswer = "2",
            difficulty = 1,
            updatedAt = System.currentTimeMillis()
        ).also { dao.upsertExercise(it) }
        return exercise.toDomain()
    }

    override suspend fun saveAttempt(
        sessionId: String,
        exercise: Exercise,
        answer: String,
        elapsedMillis: Long,
        strokes: List<HandwritingStroke>
    ): SavedAttempt = database.withTransaction {
        val session = requireNotNull(dao.getSession(sessionId)) { "La sesión local no existe" }
        val attemptNumber = dao.countAttempts(sessionId, exercise.id) + 1
        val isCorrect = answer.trim() == exercise.expectedAnswer.trim()
        val now = System.currentTimeMillis()
        val attempt = ExerciseAttemptEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            exerciseId = exercise.id,
            answer = answer,
            isCorrect = isCorrect,
            attemptNumber = attemptNumber,
            elapsedMillis = elapsedMillis.coerceAtLeast(0),
            createdAt = now
        )
        dao.insertAttempt(attempt)

        val strokeEntities = strokes.mapIndexed { index, stroke ->
            HandwritingStrokeEntity(
                id = UUID.randomUUID().toString(),
                attemptId = attempt.id,
                strokeNumber = index + 1,
                points = stroke.encode(),
                createdAt = now
            )
        }
        if (strokeEntities.isNotEmpty()) dao.insertStrokes(strokeEntities)

        dao.insertMetrics(
            listOf(
                PerformanceMetricEntity(UUID.randomUUID().toString(), attempt.id, "elapsed_millis", attempt.elapsedMillis.toDouble(), now),
                PerformanceMetricEntity(UUID.randomUUID().toString(), attempt.id, "attempt_number", attemptNumber.toDouble(), now),
                PerformanceMetricEntity(UUID.randomUUID().toString(), attempt.id, "is_correct", if (isCorrect) 1.0 else 0.0, now)
            )
        )
        dao.enqueueSync(
            PendingSyncEntity(
                id = UUID.randomUUID().toString(),
                operation = "ATTEMPT_RECORDED",
                payload = attemptPayload(session, exercise, attempt, strokeEntities),
                createdAt = now
            )
        )
        SavedAttempt(attempt.id, isCorrect)
    }.also { LearningSyncScheduler.enqueueWhenConnected(applicationContext) }

    private fun attemptPayload(
        session: LearningSessionEntity,
        exercise: Exercise,
        attempt: ExerciseAttemptEntity,
        strokes: List<HandwritingStrokeEntity>
    ): String = JSONObject()
        .put("local_session_id", session.id)
        .put("local_attempt_id", attempt.id)
        .put("student_local_id", session.studentId)
        .put("exercise_local_id", exercise.id)
        .put("answer", attempt.answer)
        .put("is_correct", attempt.isCorrect)
        .put("attempt_number", attempt.attemptNumber)
        .put("elapsed_millis", attempt.elapsedMillis)
        .put("recorded_at", attempt.createdAt)
        .put("strokes", JSONArray(strokes.map { it.points }))
        .toString()

    private fun LocalExerciseEntity.toDomain() = Exercise(id, content, statement, expectedAnswer, difficulty)

    private fun HandwritingStroke.encode(): String = points.joinToString(";") { point ->
        "${point.x},${point.y},${point.recordedAt}"
    }
}
