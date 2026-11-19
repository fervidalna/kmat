package cl.kmat.ia.practice

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.data.local.*
import cl.kmat.ia.data.repository.RoomLearningSessionRepository
import cl.kmat.ia.data.sync.*
import cl.kmat.ia.domain.model.DiagnosticAnswer
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.repository.HandwritingRecognitionRepository
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.SavePracticeAttemptUseCase
import cl.kmat.ia.presentation.practice.PracticeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MultiDeviceSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, OfflineDatabase::class.java).build()
    private fun repository(db: OfflineDatabase) = RoomLearningSessionRepository(db, context)

    @Test
    fun teacherAssignmentsAreCompletedWithPracticeUntilFiveExercises() = runBlocking {
        for (hasDiagnostic in listOf(false, true)) {
            for (assignedCount in listOf(1, 3, 5)) {
                val db = database()
                try {
                    val child = UUID.randomUUID().toString()
                    val assigned = List(assignedCount) { index ->
                        val id = UUID.randomUUID().toString()
                        Exercise(id = id, content = "Refuerzo $index", statement = "1 + 1", expectedAnswer = "2",
                            difficulty = 0, teacherAssignmentId = id, remoteExerciseVersionId = UUID.randomUUID().toString(),
                            axis = "NUMEROS_OPERACIONES", contentCode = "DOCENTE_CLASIFICADO")
                    }
                    val repo = RoomLearningSessionRepository(db, context, downloadTeacherAssignments = { assigned })
                    if (hasDiagnostic) repo.saveDiagnosticReport(child, DiagnosticAssessmentEngine().buildReport(emptyList()))
                    val plan = repo.getOfflineExercises(child)
                    assertEquals(5, plan.size)
                    assertEquals(5, plan.map { it.id }.distinct().size)
                    assertEquals(assigned.map { it.id }, plan.take(assignedCount).map { it.id })
                    val session = repo.getDailySessionId(child)
                    repo.saveSessionPlan(session, plan)
                    assertEquals(plan, repo.getSessionPlan(session))
                } finally { db.close() }
            }
        }
    }

    @Test
    fun missingAndJsonNullExerciseVersionsUseTheConfiguredVersion() {
        val version = "configured-version"
        assertEquals(version, JSONObject().withDefaultExerciseVersion(version).getString("exercise_version_remote_id"))
        assertEquals(version, JSONObject().put("exercise_version_remote_id", JSONObject.NULL).withDefaultExerciseVersion(version).getString("exercise_version_remote_id"))
        assertEquals("teacher-version", JSONObject().put("exercise_version_remote_id", "teacher-version").withDefaultExerciseVersion(version).getString("exercise_version_remote_id"))
    }

    @Test
    fun databaseUpgradePreservesOfflineSessionsAndAnswers() = runBlocking {
        val template = database()
        val name = "sync-migration-${UUID.randomUUID()}.db"
        try {
            val definitions = template.openHelper.writableDatabase.query(
                "select sql from sqlite_master where type = 'table' and name not in ('android_metadata','room_master_table','sqlite_sequence')"
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
                definitions.forEach { ddl ->
                    old.execSQL(ddl.replace(Regex(",\\s*`?planJson`?\\s+TEXT\\s+NOT NULL"), ""))
                }
                old.execSQL("insert into learning_session(id,studentId,localDate,startedAt,status,sessionType) values('session','child','2026-11-06',1000,'ACTIVE','PRACTICA')")
                old.execSQL("insert into exercise_attempt values('attempt','session','exercise','2',1,1,100,1100)")
                old.version = 4
            }
            val upgraded = Room.databaseBuilder(context, OfflineDatabase::class.java, name)
                .addMigrations(OfflineDatabaseMigrations.MIGRATION_4_5).build()
            try {
                assertEquals("", upgraded.learningDao().getSession("session")?.planJson)
                assertEquals("2", upgraded.learningDao().getSessionAttempts("session").single().answer)
                assertEquals("session", upgraded.learningDao().getResumableSession("child", "PRACTICA")?.id)
            } finally { upgraded.close() }
        } finally { template.close(); context.deleteDatabase(name) }
    }

    @Test
    fun tabletAndPhoneShareDiagnosisPlanAnswersAndNextSession() = runBlocking {
        val tablet = database()
        val phone = database()
        try {
            val child = UUID.randomUUID().toString()
            val server = Server(listOf(child))
            val a = repository(tablet)
            val b = repository(phone)
            val syncA = OfflineSyncCoordinator(tablet, server)
            val syncB = OfflineSyncCoordinator(phone, server)
            val engine = DiagnosticAssessmentEngine()
            val diagnosis = a.getDiagnosticSessionId(child)
            val questions = engine.initialQuestions(a.getOfflineExercises(child, true), "BASICO_1")
            a.saveSessionPlan(diagnosis, questions)
            questions.forEach { a.saveAttempt(diagnosis, it, it.expectedAnswer, 1_000, emptyList()) }
            val report = engine.buildReport(questions.map {
                DiagnosticAnswer(it.id, it.axis, it.difficulty, true, 1_000, it.expectedTimeMillis)
            })
            a.completeDiagnosticSession(diagnosis, child, report)
            val practice = a.getDailySessionId(child)
            val plan = a.getOfflineExercises(child)
            a.saveSessionPlan(practice, plan)
            a.saveAttempt(practice, plan[0], plan[0].expectedAnswer, 1_000, emptyList())
            assertEquals(RemoteSyncResult.Success, syncA.syncPending())
            assertEquals(RemoteSyncResult.Success, syncB.syncPending())
            assertTrue(b.hasCompletedDiagnostic(child))
            assertEquals(tablet.learningDao().getDiagnosticResult(child), phone.learningDao().getDiagnosticResult(child))
            assertEquals(practice, b.getDailySessionId(child))
            assertEquals(plan, b.getSessionPlan(practice))
            assertEquals(a.getSessionAnswers(practice), b.getSessionAnswers(practice))
            assertResumedQuestion(b, child, plan[1].id, 1, diagnostic = false)
            // Continue on the phone; the tablet then receives precisely those answers.
            plan.drop(1).forEach { b.saveAttempt(practice, it, it.expectedAnswer, 2_000, emptyList()) }
            b.completeSession(practice)
            syncB.syncPending()
            syncA.syncPending()
            assertEquals(plan.size, a.getSessionAnswers(practice).size)
            assertEquals("COMPLETED", tablet.learningDao().getSession(practice)?.status)
            assertFalse(a.hasCompletedDiagnostic(UUID.randomUUID().toString()))
            assertTrue(a.getPracticeProgress(UUID.randomUUID().toString()).all { it.correct == 0 && it.incorrect == 0 })
            assertNotEquals(practice, a.getDailySessionId(child))
        } finally { tablet.close(); phone.close() }
    }

    @Test
    fun unfinishedDiagnosticContinuesAtTheNextQuestionOnAnotherDevice() = runBlocking {
        val tablet = database()
        val phone = database()
        try {
            val child = UUID.randomUUID().toString()
            val a = repository(tablet)
            val b = repository(phone)
            val server = Server(listOf(child))
            val session = a.getDiagnosticSessionId(child)
            val questions = DiagnosticAssessmentEngine().initialQuestions(a.getOfflineExercises(child, true), "BASICO_1")
            a.saveSessionPlan(session, questions)
            questions.take(3).forEach { a.saveAttempt(session, it, it.expectedAnswer, 1_000, emptyList()) }
            OfflineSyncCoordinator(tablet, server).syncPending()
            OfflineSyncCoordinator(phone, server).syncPending()
            assertFalse(b.hasCompletedDiagnostic(child))
            assertResumedQuestion(b, child, questions[3].id, 3, diagnostic = true)
        } finally { tablet.close(); phone.close() }
    }

    private suspend fun assertResumedQuestion(
        repository: RoomLearningSessionRepository, child: String, exerciseId: String, answered: Int, diagnostic: Boolean
    ) {
        val store = ViewModelStore()
        try {
            val vm = withContext(Dispatchers.Main) {
                val recognizer = object : HandwritingRecognitionRepository {
                    override suspend fun isReady() = true
                    override suspend fun prepare() = Unit
                    override suspend fun recognize(strokes: List<HandwritingStroke>, width: Float, height: Float) = "0"
                    override fun close() = Unit
                }
                PracticeViewModel(repository, GetDailyOfflineExerciseUseCase(repository), SavePracticeAttemptUseCase(repository),
                    child, recognizer, diagnosticMode = true).also { store.put("resume", it) }
            }
            val state = withTimeout(15_000) { vm.uiState.first { !it.isLoading } }
            assertNull(state.loadError)
            assertEquals(diagnostic, state.isDiagnosticSession)
            assertEquals(answered, state.completedExercises.size)
            assertEquals(exerciseId, state.exercise?.id)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test
    fun offlineQueueSurvivesFailureAndLostAcknowledgementWithoutDuplicateAnswers() = runBlocking {
        val db = database()
        try {
            val child = UUID.randomUUID().toString()
            val repo = repository(db)
            val server = Server(listOf(child))
            val sync = OfflineSyncCoordinator(db, server)
            val session = repo.getDailySessionId(child)
            val exercise = repo.getOfflineExercises(child, true).first()
            repo.saveSessionPlan(session, listOf(exercise))
            repo.saveAttempt(session, exercise, exercise.expectedAnswer, 100, emptyList())
            server.offline = true
            assertEquals(RemoteSyncResult.RetryableFailure, sync.syncPending())
            assertEquals(3, db.learningDao().pendingSync(100).size)
            server.offline = false
            server.loseNextAcknowledgement = true
            assertEquals(RemoteSyncResult.RetryableFailure, sync.syncPending())
            assertEquals(3, db.learningDao().pendingSync(100).size)
            assertEquals(RemoteSyncResult.Success, sync.syncPending())
            assertTrue(db.learningDao().pendingSync(100).isEmpty())
            assertEquals(1, db.learningDao().getSessionAttempts(session).size)
            assertEquals(1, server.sessions.getValue(session).getJSONArray("attempts").length())
        } finally { db.close() }
    }

    @Test
    fun otherAccountsQueueCannotBlockOrLeakAndAllBatchesAreDrained() = runBlocking {
        val db = database()
        try {
            val child = UUID.randomUUID().toString()
            val otherChild = UUID.randomUUID().toString()
            val server = Server(listOf(child))
            val repo = repository(db)
            val session = repo.getDailySessionId(child)
            repo.getDailySessionId(otherChild)
            repeat(75) { index ->
                db.learningDao().enqueueSync(PendingSyncEntity("batch-$index", "SESSION_STARTED",
                    JSONObject().put("student_local_id", child).put("local_session_id", session)
                        .put("session_type", "PRACTICA").put("started_at", 1_000).toString(), 1_000L + index))
            }
            assertEquals(RemoteSyncResult.Success, OfflineSyncCoordinator(db, server).syncPending())
            assertTrue(server.batchSizes.size >= 2)
            assertTrue(server.batchSizes.all { it <= 50 })
            assertEquals(1, db.learningDao().pendingSync(100).size)
            assertEquals(otherChild, JSONObject(db.learningDao().pendingSync(100).single().payload).getString("student_local_id"))
            assertTrue(server.sessions.values.all { it.getString("studentId") == child })
        } finally { db.close() }
    }

    @Test
    fun selectedStudentNotOwnedByAccountDoesNotReportSyncSuccessOrUpload() = runBlocking {
        val db = database()
        try {
            val child = UUID.randomUUID().toString()
            val server = Server(emptyList())
            repository(db).getDailySessionId(child)
            assertEquals(RemoteSyncResult.RetryableFailure, OfflineSyncCoordinator(db, server).syncPending(child))
            assertTrue(server.batchSizes.isEmpty())
            assertEquals(1, db.learningDao().pendingSync(100).size)
        } finally { db.close() }
    }

    @Test
    fun staleRemoteActiveSessionCannotUndoOfflineCompletion() = runBlocking {
        val db = database()
        try {
            val child = UUID.randomUUID().toString()
            val repo = repository(db)
            val server = Server(listOf(child))
            val sync = OfflineSyncCoordinator(db, server)
            val session = repo.getDailySessionId(child)
            sync.syncPending()
            repo.completeSession(session)
            // A stale download arrives while completion has not yet uploaded.
            sync.mergePage(child, JSONArray().put(server.sessions.getValue(session)))
            assertEquals("COMPLETED", db.learningDao().getSession(session)?.status)
            assertEquals(1, db.learningDao().pendingSync(100).size)
            try {
                sync.mergePage(UUID.randomUUID().toString(), JSONArray().put(server.sessions.getValue(session)))
                fail("Cross-profile snapshots must be rejected")
            } catch (_: IllegalArgumentException) { }
        } finally { db.close() }
    }

    /** In-memory service boundary: real Room databases and repositories on both devices. */
    private class Server(private val children: List<String>) : LearningSyncTransport, LearningSyncConnection {
        val sessions = sortedMapOf<String, JSONObject>()
        val batchSizes = mutableListOf<Int>()
        var offline = false
        var loseNextAcknowledgement = false
        override suspend fun connect(): LearningSyncConnection {
            check(!offline) { "No network" }
            return this
        }
        override suspend fun studentIds() = children
        override suspend fun upload(items: List<PendingSyncEntity>) {
            batchSizes += items.size
            items.forEach { event ->
                val p = JSONObject(event.payload)
                check(p.getString("student_local_id") in children)
                val id = p.getString("local_session_id")
                when (event.operation) {
                    "SESSION_STARTED" -> if (id !in sessions) sessions[id] = JSONObject()
                        .put("id", id).put("studentId", p.getString("student_local_id"))
                        .put("sessionType", p.getString("session_type")).put("status", "ACTIVE")
                        .put("startedAt", p.getLong("started_at")).put("attempts", JSONArray())
                    "SESSION_PLANNED" -> sessions.getValue(id).put("plan", p.getJSONArray("exercises"))
                    "SESSION_COMPLETED" -> sessions.getValue(id).put("status", "COMPLETED").put("endedAt", p.getLong("ended_at"))
                    "DIAGNOSTIC_RECORDED" -> sessions.getValue(id).put("report", p.getJSONObject("report"))
                    "ATTEMPT_RECORDED" -> {
                        val attempts = sessions.getValue(id).getJSONArray("attempts")
                        if ((0 until attempts.length()).none { attempts.getJSONObject(it).getString("id") == p.getString("local_attempt_id") }) {
                            attempts.put(JSONObject().put("id", p.getString("local_attempt_id"))
                                .put("exerciseId", p.getString("exercise_local_id")).put("answer", p.getString("answer"))
                                .put("isCorrect", p.getBoolean("is_correct")).put("attemptNumber", p.getInt("attempt_number"))
                                .put("elapsedMillis", p.getLong("elapsed_millis")).put("createdAt", p.getLong("recorded_at"))
                                .put("exercise", p.getJSONObject("exercise")))
                        }
                    }
                }
            }
            if (loseNextAcknowledgement) {
                loseNextAcknowledgement = false
                error("Response lost after commit")
            }
        }
        override suspend fun download(studentId: String, after: String?): JSONArray = JSONArray(
            sessions.values.filter { it.getString("studentId") == studentId && (after == null || it.getString("id") > after) }.take(50)
        )
    }
}
