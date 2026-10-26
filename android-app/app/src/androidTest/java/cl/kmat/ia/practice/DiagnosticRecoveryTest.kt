package cl.kmat.ia.practice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.data.local.OfflineDatabase
import cl.kmat.ia.data.repository.RoomLearningSessionRepository
import cl.kmat.ia.domain.model.DiagnosticAnswer
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticRecoveryTest {
    @Test
    fun recoversAnOlderCompletedDiagnosticWithoutMakingTheChildRepeatIt() = runBlocking {
        withRepository { database, repository ->
            val sessionId = repository.getDiagnosticSessionId("student")
            val questions = DiagnosticAssessmentEngine().initialQuestions(repository.getOfflineExercises("student", true), "BASICO_1")
            questions.forEach { repository.saveAttempt(sessionId, it, it.expectedAnswer, 1000, emptyList()) }
            // Simulate the old write order: completed session, missing report.
            repository.completeSession(sessionId)
            assertNull(database.learningDao().getDiagnosticResult("student"))
            assertTrue(repository.hasCompletedDiagnostic("student"))
            val recovered = requireNotNull(database.learningDao().getDiagnosticResult("student"))
            assertEquals(10, recovered.questionCount)
            assertEquals(database.learningDao().getSession(sessionId)?.endedAt, recovered.completedAt)
            assertTrue(repository.hasCompletedDiagnostic("student"))
            assertEquals(recovered, database.learningDao().getDiagnosticResult("student"))
            assertFalse(repository.hasCompletedDiagnostic("different-student"))
        }
    }

    @Test
    fun abandonedDiagnosticIsNotTreatedAsCompleted() = runBlocking {
        withRepository { _, repository ->
            val sessionId = repository.getDiagnosticSessionId("student")
            DiagnosticAssessmentEngine().initialQuestions(repository.getOfflineExercises("student", true), "BASICO_1")
                .forEach { repository.saveAttempt(sessionId, it, it.expectedAnswer, 1000, emptyList()) }
            assertFalse(repository.hasCompletedDiagnostic("student"))
        }
    }

    @Test
    fun reportAndCompletionAreCommittedTogetherAndCannotOverwriteTheFirstLevel() = runBlocking {
        withRepository { database, repository ->
            val sessionId = repository.getDiagnosticSessionId("student")
            val engine = DiagnosticAssessmentEngine()
            val questions = engine.initialQuestions(repository.getOfflineExercises("student", true), "BASICO_1")
            val report = engine.buildReport(questions.map {
                DiagnosticAnswer(it.id, it.axis, it.difficulty, true, 1000, it.expectedTimeMillis)
            })
            database.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER fail_completion BEFORE UPDATE OF status ON learning_session
                BEGIN SELECT RAISE(ABORT, 'Simulated completion failure'); END
            """.trimIndent())
            try {
                repository.completeDiagnosticSession(sessionId, "student", report)
                fail("The injected database failure must propagate")
            } catch (_: android.database.sqlite.SQLiteException) {
                // No half-written completion may remain.
            }
            assertNull(database.learningDao().getDiagnosticResult("student"))
            assertEquals("ACTIVE", database.learningDao().getSession(sessionId)?.status)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_completion")
            repository.completeDiagnosticSession(sessionId, "student", report)
            val saved = database.learningDao().getDiagnosticResult("student")
            assertNotNull(saved)
            assertEquals("COMPLETED", database.learningDao().getSession(sessionId)?.status)
            repository.completeDiagnosticSession(sessionId, "student", report.copy(overallLevel = 1))
            assertEquals(saved, database.learningDao().getDiagnosticResult("student"))
        }
    }

    private suspend fun withRepository(block: suspend (OfflineDatabase, RoomLearningSessionRepository) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, OfflineDatabase::class.java).build()
        try {
            block(database, RoomLearningSessionRepository(database, context, downloadRemoteProgress = { null }))
        } finally {
            database.close()
        }
    }
}
