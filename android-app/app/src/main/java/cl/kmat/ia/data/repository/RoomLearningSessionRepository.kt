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
import cl.kmat.ia.data.local.StudentDiagnosticResultEntity
import cl.kmat.ia.data.sync.LearningSyncScheduler
import cl.kmat.ia.data.sync.toSyncJson
import cl.kmat.ia.data.sync.toSyncExercise
import cl.kmat.ia.data.sync.toLocalExercise
import cl.kmat.ia.domain.repository.SavedExerciseAnswer
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.ExercisePerformance
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.model.DiagnosticAnswer
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import cl.kmat.ia.domain.model.DiagnosticAxisResult
import cl.kmat.ia.domain.model.SubjectProgressPoint
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.repository.SavedAttempt
import cl.kmat.ia.domain.usecase.AdaptiveLearningEngine
import cl.kmat.ia.domain.usecase.AdaptivePracticeAssigner
import cl.kmat.ia.domain.usecase.MasteryAttempt
import cl.kmat.ia.domain.usecase.MasterySession
import cl.kmat.ia.domain.usecase.SubjectMastery
import cl.kmat.ia.domain.usecase.SubjectMasteryEngine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex

class RoomLearningSessionRepository(
    private val database: OfflineDatabase,
    private val applicationContext: Context,
    private val syncBeforeUse: suspend (String) -> Unit = {},
    private val claimRemoteSession: suspend (String, String, String, Long) -> String? = { _, _, _, _ -> null },
    private val downloadTeacherAssignments: suspend (String) -> List<Exercise>? = { null },
    private val adaptiveLearningEngine: AdaptiveLearningEngine = AdaptiveLearningEngine(),
    private val adaptivePracticeAssigner: AdaptivePracticeAssigner = AdaptivePracticeAssigner()
) : LearningSessionRepository {
    private val dao = database.learningDao()
    private val catalogMutex = Mutex()
    private var cachedExerciseCatalog: List<Exercise>? = null

    private suspend fun calculateMastery(studentId: String, diagnostic: DiagnosticReport): Map<String, SubjectMastery> {
        val sessions = dao.getCompletedPracticeSessions(studentId, diagnostic.completedAt).map { session ->
            val plan = runCatching {
                val json = JSONArray(session.planJson)
                List(json.length()) { json.getJSONObject(it).toSyncExercise() }.associateBy { it.id }
            }.getOrDefault(emptyMap())
            MasterySession(dao.getSessionAttempts(session.id).mapNotNull { attempt ->
                val exercise = plan[attempt.exerciseId] ?: return@mapNotNull null
                // Older assignments lack verified subject and difficulty metadata.
                if (exercise.teacherAssignmentId != null && exercise.contentCode != "DOCENTE_CLASIFICADO") return@mapNotNull null
                MasteryAttempt(exercise.axis, exercise.difficulty, attempt.isCorrect)
            })
        }
        val engine = SubjectMasteryEngine()
        return diagnostic.axes.associate { axis -> axis.axis to engine.calculate(axis.axis, axis.level, sessions) }
    }

    override fun observeSessionSyncState(sessionId: String) = dao.observeSessionSyncState(sessionId)

    override suspend fun synchronize(studentId: String) {
        // A previously saved report might predate report synchronization.
        database.withTransaction {
            dao.getFirstCompletedDiagnosticSession(studentId)?.let { session ->
                if (dao.getDiagnosticResult(studentId) != null) enqueueDiagnosticReport(studentId, session.id)
            }
        }
        syncBeforeUse(studentId)
    }

    override suspend fun getSessionPlan(sessionId: String): List<Exercise> {
        val json = dao.getSession(sessionId)?.planJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        val rows = JSONArray(json)
        return List(rows.length()) { rows.getJSONObject(it).toSyncExercise() }
    }

    override suspend fun saveSessionPlan(sessionId: String, exercises: List<Exercise>) {
        database.withTransaction {
            val session = requireNotNull(dao.getSession(sessionId))
            if (session.planJson.isNotBlank()) return@withTransaction
            val plan = JSONArray(exercises.map { it.toSyncJson() })
            exercises.forEach { dao.upsertExercise(it.toLocalExercise()) }
            dao.saveSessionPlan(sessionId, plan.toString())
            dao.enqueueSync(PendingSyncEntity(UUID.randomUUID().toString(), "SESSION_PLANNED",
                JSONObject().put("local_session_id", sessionId).put("student_local_id", session.studentId)
                    .put("exercises", plan).toString(), System.currentTimeMillis()))
        }
        LearningSyncScheduler.enqueueWhenConnected(applicationContext)
    }

    override suspend fun getSessionAnswers(sessionId: String): List<SavedExerciseAnswer> {
        val plan = getSessionPlan(sessionId).associateBy { it.id }
        return dao.getSessionAttempts(sessionId).distinctBy { it.exerciseId }.mapNotNull { attempt ->
            val exercise = plan[attempt.exerciseId] ?: dao.getExercise(attempt.exerciseId)?.toDomain()
            exercise?.let { SavedExerciseAnswer(it, attempt.answer, attempt.isCorrect, attempt.elapsedMillis) }
        }
    }

    private suspend fun enqueueDiagnosticReport(studentId: String, sessionId: String) {
        val report = dao.getDiagnosticResult(studentId) ?: return
        val payload = JSONObject().put("student_local_id", studentId).put("local_session_id", sessionId)
            .put("report", report.toSyncJson()).toString()
        dao.enqueueSyncIfAbsent(PendingSyncEntity("diagnostic:$sessionId", "DIAGNOSTIC_RECORDED", payload, System.currentTimeMillis()))
    }

    override suspend fun getDailySessionId(studentId: String): String = getSessionId(studentId, "PRACTICA")

    override suspend fun getDiagnosticSessionId(studentId: String): String = getSessionId(studentId, "DIAGNOSTICO")

    private suspend fun getSessionId(studentId: String, sessionType: String): String {
        dao.getResumableSession(studentId, sessionType)?.let { return it.id }
        val proposedId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val claimed = withTimeoutOrNull(1_500L) {
            claimRemoteSession(studentId, sessionType, proposedId, startedAt)
        }
        if (claimed != null) {
            syncBeforeUse(studentId)
            dao.getSession(claimed)?.let {
                return if (it.status == "ACTIVE") it.id else getSessionId(studentId, sessionType)
            }
        }
        return database.withTransaction {
            val today = LocalDate.now().toString()
            dao.getResumableSession(studentId, sessionType)?.id ?: LearningSessionEntity(
                id = claimed ?: proposedId,
                studentId = studentId,
                localDate = today,
                startedAt = startedAt,
                sessionType = sessionType
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
                            .put("session_type", session.sessionType)
                            .put("started_at", session.startedAt)
                            .toString(),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }.id
        }.also { LearningSyncScheduler.enqueueWhenConnected(applicationContext) }
    }

    override suspend fun getOfflineExercise(): Exercise {
        return getOfflineExercises(LOCAL_STUDENT_ID).first()
    }

    override suspend fun getOfflineExercises(studentId: String, diagnosticBank: Boolean): List<Exercise> {
        val now = System.currentTimeMillis()
        val catalog = listOf(
            LocalExerciseEntity("offline-count-1-10", "Conteo hasta 100", "Completa: 1, 2, __, 4, 5, 6, __, 8, 9, 10", "3,7", 0, now, 45_000),
            LocalExerciseEntity("offline-count-11-20", "Conteo hasta 100", "Completa: 11, 12, __, 14, 15, __, 17, 18, 19, 20", "13,16", 0, now, 45_000),
            LocalExerciseEntity("offline-count-20-30", "Conteo hasta 100", "Completa: 20, 21, __, 23, 24, 25, 26, 27, 28, 29, 30", "22", 0, now, 35_000),
            LocalExerciseEntity("offline-count-28-32", "Conteo hasta 100", "Completa: 28, __, 30, __, 32", "29,31", 1, now, 35_000),
            LocalExerciseEntity("offline-count-46-49", "Conteo hasta 100", "Completa: 46, 47, __, 49", "48", 1, now, 25_000),
            LocalExerciseEntity("offline-count-55-70", "Conteo hasta 100", "Cuenta de 5 en 5: 55, 60, __, 70", "65", 1, now, 30_000),
            LocalExerciseEntity("offline-count-77-82", "Conteo hasta 100", "Completa: 77, 78, __, 80, __, 82", "79,81", 2, now, 40_000),
            LocalExerciseEntity("offline-count-91-96", "Conteo hasta 100", "Completa: 91, __, 93, 94, __, 96", "92,95", 2, now, 40_000),
            LocalExerciseEntity("offline-count-96-100", "Conteo hasta 100", "Completa: 96, 97, __, 99, 100", "98", 2, now, 20_000),
            LocalExerciseEntity("offline-count-1-6", "Conteo · completa la serie", "Completa: 1, 2, 3, __, __, 6", "4,5", 0, now, 25_000),
            LocalExerciseEntity("offline-count-8-11", "Conteo · completa la serie", "Completa: 8, 9, __, 11", "10", 0, now, 15_000),
            LocalExerciseEntity("offline-count-stars-5", "Conteo · cuenta y responde", "¿Cuántas estrellas hay?", "5", 0, now, 20_000),
            LocalExerciseEntity("offline-count-by-2-20", "Conteo · saltos de 2", "Completa: 20, 22, 24, __, __", "26,28", 1, now, 30_000),
            LocalExerciseEntity("offline-count-back-15", "Conteo · hacia atrás", "Completa hacia atrás: 15, 14, __, __, 11", "13,12", 1, now, 30_000),
            LocalExerciseEntity("offline-count-by-5-35", "Conteo · saltos de 5", "Completa: 35, 40, 45, __, __", "50,55", 1, now, 30_000),
            LocalExerciseEntity("offline-count-by-10-30", "Conteo · saltos de 10", "Completa: 30, 40, __, 60, __, 80", "50,70", 2, now, 35_000),
            LocalExerciseEntity("offline-count-forward-18", "Conteo · siete pasos", "Escribe los 7 números que siguen después del 18: __, __, __, __, __, __, __", "19,20,21,22,23,24,25", 2, now, 50_000),
            LocalExerciseEntity("offline-count-by-2-86", "Conteo · saltos de 2", "Completa: 86, 88, __, __, __, __", "90,92,94,96", 2, now, 40_000),
            LocalExerciseEntity("offline-sum-1-1", "Sumas de un dígito", "1 + 1 = ?", "2", 0, now, 15_000),
            LocalExerciseEntity("offline-sum-2-3", "Sumas de un dígito", "2 + 3 = ?", "5", 0, now, 15_000),
            LocalExerciseEntity("offline-sum-4-1", "Sumas de un dígito", "4 + 1 = ?", "5", 0, now, 15_000),
            LocalExerciseEntity("offline-sum-6-2", "Sumas de un dígito", "6 + 2 = ?", "8", 1, now, 20_000),
            LocalExerciseEntity("offline-sum-5-4", "Sumas de un dígito", "5 + 4 = ?", "9", 1, now, 20_000),
            LocalExerciseEntity("offline-sum-7-1", "Sumas de un dígito", "7 + 1 = ?", "8", 1, now, 20_000),
            LocalExerciseEntity("offline-sum-8-7", "Sumas de un dígito", "8 + 7 = ?", "15", 2, now, 25_000),
            LocalExerciseEntity("offline-sum-9-6", "Sumas de un dígito", "9 + 6 = ?", "15", 2, now, 25_000),
            LocalExerciseEntity("offline-sum-9-9", "Sumas de un dígito", "9 + 9 = ?", "18", 2, now, 25_000),
            LocalExerciseEntity("trace-number-1", "Trazado de números", "Sigue los puntos para trazar el número 1", "1", 0, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-2", "Trazado de números", "Sigue los puntos para trazar el número 2", "2", 0, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-3", "Trazado de números", "Sigue los puntos para trazar el número 3", "3", 0, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-4", "Trazado de números", "Sigue los puntos para trazar el número 4", "4", 0, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-5", "Trazado de números", "Sigue los puntos para trazar el número 5", "5", 1, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-6", "Trazado de números", "Sigue los puntos para trazar el número 6", "6", 1, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-7", "Trazado de números", "Sigue los puntos para trazar el número 7", "7", 1, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-8", "Trazado de números", "Sigue los puntos para trazar el número 8", "8", 2, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-9", "Trazado de números", "Sigue los puntos para trazar el número 9", "9", 2, now, 25_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("trace-number-10", "Trazado de números", "Sigue los puntos para trazar el número 10", "10", 2, now, 30_000, "NUMEROS_OPERACIONES", "TRAZADO_NUMEROS", "BASICO_1"),
            LocalExerciseEntity("diag-pattern-1-1", "Patrones numéricos", "Completa la secuencia: 1, 2, 3, __", "4", 0, now, 15_000, "PATRONES_ALGEBRA", "PATRONES_NUMERICOS", "BASICO_1"),
            LocalExerciseEntity("diag-pattern-1-2", "Patrones numéricos", "Completa la secuencia: 2, 4, 6, __", "8", 0, now, 20_000, "PATRONES_ALGEBRA", "PATRONES_NUMERICOS", "BASICO_1"),
            LocalExerciseEntity("diag-pattern-2-1", "Patrones numéricos", "Completa la secuencia: 5, 10, 15, __", "20", 1, now, 20_000, "PATRONES_ALGEBRA", "PATRONES_NUMERICOS", "BASICO_1"),
            LocalExerciseEntity("diag-pattern-2-2", "Patrones numéricos", "Completa la secuencia: 20, 18, 16, __", "14", 1, now, 20_000, "PATRONES_ALGEBRA", "PATRONES_NUMERICOS", "BASICO_2"),
            LocalExerciseEntity("diag-pattern-3-1", "Patrones numéricos", "Completa: 17, 15, 13, __", "11", 2, now, 25_000, "PATRONES_ALGEBRA", "PATRONES_NUMERICOS", "BASICO_2"),
            LocalExerciseEntity("diag-pattern-3-2", "Patrones numéricos", "¿Qué número falta? 10, 15, 20, __, 30", "25", 2, now, 25_000, "PATRONES_ALGEBRA", "PATRONES_NUMERICOS", "BASICO_2"),
            LocalExerciseEntity("diag-geometry-1-1", "Figuras 2D", "¿Cuántos lados tiene un triángulo?", "3", 0, now, 15_000, "GEOMETRIA", "FIGURAS_2D", "BASICO_1"),
            LocalExerciseEntity("diag-geometry-1-2", "Figuras 2D", "¿Cuántos lados tiene un cuadrado?", "4", 0, now, 15_000, "GEOMETRIA", "FIGURAS_2D", "BASICO_1"),
            LocalExerciseEntity("diag-geometry-2-1", "Figuras 2D", "Un pentágono tiene 5 lados. ¿Cuántos lados tienen 2 pentágonos?", "10", 1, now, 25_000, "GEOMETRIA", "FIGURAS_2D", "BASICO_2"),
            LocalExerciseEntity("diag-geometry-2-2", "Figuras 2D", "¿Cuántos vértices tiene un rectángulo?", "4", 1, now, 20_000, "GEOMETRIA", "FIGURAS_2D", "BASICO_2"),
            LocalExerciseEntity("diag-geometry-3-1", "Figuras 2D", "Una figura tiene 4 lados y otro triángulo tiene 3. ¿Cuántos lados suman?", "7", 2, now, 25_000, "GEOMETRIA", "FIGURAS_2D", "BASICO_2"),
            LocalExerciseEntity("diag-geometry-3-2", "Figuras 2D", "¿Cuántos lados tienen en total un pentágono y un triángulo?", "8", 2, now, 25_000, "GEOMETRIA", "FIGURAS_2D", "BASICO_2"),
            LocalExerciseEntity("diag-measure-1-1", "Comparación de longitudes", "Una cinta mide 5 cm y otra 8 cm. ¿Cuántos centímetros más mide la larga?", "3", 0, now, 20_000, "MEDICION", "LONGITUD", "BASICO_1"),
            LocalExerciseEntity("diag-measure-1-2", "Comparación de longitudes", "¿Cuántos centímetros hay entre 4 cm y 7 cm?", "3", 0, now, 20_000, "MEDICION", "LONGITUD", "BASICO_1"),
            LocalExerciseEntity("diag-measure-2-1", "Unidades de longitud", "Una cinta mide 8 clips y otra 13 clips iguales. ¿Cuántos clips más mide la segunda?", "5", 1, now, 25_000, "MEDICION", "LONGITUD", "BASICO_2"),
            LocalExerciseEntity("diag-measure-2-2", "Comparación de duración", "Una canción dura 3 minutos y otra 5. ¿Cuántos minutos más dura la segunda?", "2", 1, now, 25_000, "MEDICION", "DURACION", "BASICO_2"),
            LocalExerciseEntity("diag-measure-3-1", "Comparación de longitudes", "Una mesa mide 12 bloques y una silla 8 bloques iguales. ¿Cuál es la diferencia?", "4", 2, now, 25_000, "MEDICION", "LONGITUD", "BASICO_2"),
            LocalExerciseEntity("diag-measure-3-2", "Secuencias temporales", "Una actividad dura 15 minutos y otra 8. ¿Cuántos minutos más dura la primera?", "7", 2, now, 25_000, "MEDICION", "DURACION", "BASICO_2"),
            LocalExerciseEntity("diag-data-1-1", "Lectura de datos", "Hay 3 votos por manzana y 2 por pera. ¿Cuántos votos hay?", "5", 0, now, 20_000, "DATOS_PROBABILIDAD", "TABLAS_CONTEO", "BASICO_1"),
            LocalExerciseEntity("diag-data-1-2", "Lectura de pictogramas", "Cada estrella representa 1 voto. ¿Cuántos votos representan?", "4", 0, now, 20_000, "DATOS_PROBABILIDAD", "PICTOGRAMAS", "BASICO_1"),
            LocalExerciseEntity("diag-data-2-1", "Interpretación de datos", "Una tabla registra 8 perros y 5 gatos. ¿Cuántas mascotas registra?", "13", 1, now, 25_000, "DATOS_PROBABILIDAD", "TABLAS_CONTEO", "BASICO_2"),
            LocalExerciseEntity("diag-data-2-2", "Interpretación de datos", "Manzana tiene 8 votos y naranja 4. ¿Cuántos votos más tiene manzana?", "4", 1, now, 25_000, "DATOS_PROBABILIDAD", "INTERPRETACION", "BASICO_2"),
            LocalExerciseEntity("diag-data-3-1", "Interpretación de datos", "Hay 6 votos rojos y 4 azules. Se agregan 2 azules. ¿Cuántos votos azules hay ahora?", "6", 2, now, 30_000, "DATOS_PROBABILIDAD", "INTERPRETACION", "BASICO_2"),
            LocalExerciseEntity("diag-data-3-2", "Interpretación de datos", "Una tabla tiene 7 votos por fútbol y 5 por básquetbol. Llegan 3 votos nuevos por básquetbol. ¿Cuántos votos hay en total ahora?", "15", 2, now, 35_000, "DATOS_PROBABILIDAD", "INTERPRETACION", "BASICO_2")
        ).map { exercise ->
            if (exercise.axis != "NUMEROS_OPERACIONES") exercise
            else exercise.copy(
                axis = "NUMEROS_OPERACIONES",
                contentCode = when {
                    exercise.id.startsWith(AdaptiveLearningEngine.COUNT_PREFIX) -> "CONTEO"
                    exercise.id.startsWith(AdaptiveLearningEngine.SUM_PREFIX) -> "ADICION"
                    exercise.id.startsWith("trace-number-") -> "TRAZADO_NUMEROS"
                    else -> "GENERAL"
                },
                educationLevel = if (exercise.difficulty >= 2) "BASICO_2" else "BASICO_1"
            )
        }.map { exercise -> exercise.copy(visualSpec = visualSpecFor(exercise)) }
        catalogMutex.lock()
        val exerciseCatalog = try {
            var preparedCatalog = cachedExerciseCatalog
            if (preparedCatalog == null) {
                for (exercise in catalog) {
                    val stored = dao.getExercise(exercise.id)
                    if (stored == null || stored.expectedAnswer != exercise.expectedAnswer ||
                        stored.statement != exercise.statement || stored.difficulty != exercise.difficulty ||
                        stored.expectedTimeMillis != exercise.expectedTimeMillis || stored.axis != exercise.axis ||
                        stored.contentCode != exercise.contentCode || stored.educationLevel != exercise.educationLevel ||
                        stored.visualSpec != exercise.visualSpec
                    ) dao.upsertExercise(exercise)
                }
                preparedCatalog = catalog.map { it.toDomain() }
                cachedExerciseCatalog = preparedCatalog
            }
            requireNotNull(preparedCatalog)
        } finally {
            catalogMutex.unlock()
        }

        if (diagnosticBank) return exerciseCatalog

        // Bring a small number of unresolved mistakes back in the next practice session.
        // They are kept in the attempt history; a later correct answer becomes the current result.
        val retryExercises = dao.getIncorrectPracticeExercises(studentId)
            .take(MAX_RETRIES_PER_SESSION)
            .map { it.toDomain() }
        val retryIds = retryExercises.map { it.id }.toSet()
        val assignedExercises = withTimeoutOrNull(3_000L) {
            runCatching { downloadTeacherAssignments(studentId) }.getOrNull()
        }.orEmpty().filter { it.id !in dao.getAnsweredExerciseIds(studentId) && it.id !in retryIds }
            .distinctBy { it.id }.take((AdaptiveLearningEngine.SESSION_EXERCISE_COUNT - retryExercises.size).coerceAtLeast(0))
        assignedExercises.forEach { exercise -> dao.upsertExercise(exercise.toLocalExercise()) }
        val fixedExercises = (retryExercises + assignedExercises).distinctBy { it.id }
        val remainingCount = AdaptiveLearningEngine.SESSION_EXERCISE_COUNT - fixedExercises.size
        if (remainingCount == 0) return fixedExercises

        val diagnosticReport = dao.getDiagnosticResult(studentId)?.toDiagnosticReport()
        if (diagnosticReport != null) {
            val recentPractice = dao.getLatestCompletedPracticePerformances(studentId).map { row ->
                ExercisePerformance(row.exerciseId, row.isCorrect, row.elapsedMillis, row.axis, row.expectedTimeMillis, row.difficulty)
            }
            val selected = adaptivePracticeAssigner.chooseNextSession(
                exerciseCatalog, diagnosticReport, recentPractice,
                count = remainingCount,
                previouslyAnswered = dao.getAnsweredExerciseIds(studentId).toSet(),
                masteryByAxis = calculateMastery(studentId, diagnosticReport)
            )
            val combined = (fixedExercises + selected).distinctBy { it.id }
            if (combined.size == AdaptiveLearningEngine.SESSION_EXERCISE_COUNT) return combined
        }

        // La selección de ejercicios debe seguir funcionando si falla el backend remoto.
        val previousSession = dao.getLatestCompletedSessionAttempts(studentId).map {
            ExercisePerformance(it.exerciseId, it.isCorrect, it.elapsedMillis)
        }
        val selectedIds = adaptiveLearningEngine.chooseNextExerciseIds(previousSession)
        val catalogById = exerciseCatalog.associateBy { it.id }
        val fixedIds = fixedExercises.map { it.id }.toSet()
        val practice = (selectedIds.mapNotNull(catalogById::get) + exerciseCatalog.shuffled())
            .distinctBy { it.id }
            .filterNot { it.id in fixedIds }
            .take(remainingCount)
        val combined = (fixedExercises + practice).distinctBy { it.id }
        check(combined.size == AdaptiveLearningEngine.SESSION_EXERCISE_COUNT) { "No hay suficientes ejercicios para completar la sesión" }
        return combined
    }

    override suspend fun saveDiagnosticReport(studentId: String, report: DiagnosticReport) {
        val axisJson = org.json.JSONArray().apply {
            report.axes.forEach { axis ->
                put(JSONObject()
                    .put("axis", axis.axis)
                    .put("label", axis.label)
                    .put("correct", axis.correct)
                    .put("total", axis.total)
                    .put("status", axis.status)
                    .put("level", axis.level)
                    .put("withinExpectedTime", axis.withinExpectedTime))
            }
        }
        dao.saveDiagnosticResult(StudentDiagnosticResultEntity(
            studentId, report.overallLevel, axisJson.toString(), report.totalQuestions, report.completedAt
        ))
    }

    override suspend fun hasCompletedDiagnostic(studentId: String): Boolean {
        // Seed metadata before recovering reports created by older app versions.
        getOfflineExercises(studentId, diagnosticBank = true)
        return database.withTransaction {
            if (dao.getDiagnosticResult(studentId) != null) return@withTransaction true
            // Older builds could mark the session completed before persisting its report.
            // Recover only a completed diagnostic with sufficient evidence, never an abandoned test.
            val session = dao.getFirstCompletedDiagnosticSession(studentId) ?: return@withTransaction false
            val answers = dao.getDiagnosticPerformances(session.id).map {
                DiagnosticAnswer(it.exerciseId, it.axis, it.difficulty, it.isCorrect, it.elapsedMillis, it.expectedTimeMillis)
            }
            if (answers.size < DiagnosticAssessmentEngine.INITIAL_QUESTIONS ||
                !DiagnosticAssessmentEngine.AXES.all { axis -> answers.count { it.axis == axis } >= 2 }
            ) return@withTransaction false
            saveDiagnosticReport(studentId, DiagnosticAssessmentEngine().buildReport(answers).copy(
                completedAt = session.endedAt ?: session.startedAt
            ))
            enqueueDiagnosticReport(studentId, session.id)
            true
        }
    }

    override suspend fun getDiagnosticReport(studentId: String): DiagnosticReport? {
        hasCompletedDiagnostic(studentId)
        return dao.getDiagnosticResult(studentId)?.toDiagnosticReport()
    }

    override suspend fun completeDiagnosticSession(sessionId: String, studentId: String, report: DiagnosticReport) {
        database.withTransaction {
            val session = requireNotNull(dao.getSession(sessionId))
            require(session.studentId == studentId && session.sessionType == "DIAGNOSTICO")
            saveDiagnosticReport(studentId, report)
            completeSessionLocally(sessionId, System.currentTimeMillis())
            enqueueDiagnosticReport(studentId, sessionId)
        }
        LearningSyncScheduler.enqueueWhenConnected(applicationContext)
    }

    private fun StudentDiagnosticResultEntity.toDiagnosticReport(): DiagnosticReport {
        val rows = org.json.JSONArray(axisResultsJson)
        val axes = List(rows.length()) { index ->
            rows.getJSONObject(index).let { row ->
                DiagnosticAxisResult(
                    axis = row.getString("axis"), label = row.getString("label"),
                    correct = row.getInt("correct"), total = row.getInt("total"),
                    status = row.getString("status"), level = row.getInt("level"),
                    withinExpectedTime = row.optInt("withinExpectedTime")
                )
            }
        }
        return DiagnosticReport(overallLevel, questionCount, axes, completedAt)
    }

    override suspend fun completeSession(sessionId: String) {
        val endedAt = System.currentTimeMillis()
        database.withTransaction {
            completeSessionLocally(sessionId, endedAt)
        }
        LearningSyncScheduler.enqueueWhenConnected(applicationContext)
    }

    private suspend fun completeSessionLocally(sessionId: String, endedAt: Long) {
        val session = requireNotNull(dao.getSession(sessionId)) { "La sesión local no existe" }
        if (session.status == "COMPLETED") return
        dao.completeSession(sessionId, endedAt)
        dao.enqueueSync(
            PendingSyncEntity(
                id = UUID.randomUUID().toString(),
                operation = "SESSION_COMPLETED",
                payload = JSONObject()
                    .put("local_session_id", session.id)
                    .put("student_local_id", session.studentId)
                    .put("ended_at", endedAt)
                    .toString(),
                createdAt = endedAt
            )
        )
    }

    override suspend fun getPracticeProgress(studentId: String): List<StudentSubjectProgress> {
        LearningSyncScheduler.enqueueWhenConnected(applicationContext)
        getOfflineExercises(studentId, diagnosticBank = true)
        val rows = dao.getPracticeProgress(studentId)
        val recentByAxis = dao.getLatestCompletedPracticePerformances(studentId).groupBy { it.axis }
        val masteryByAxis = dao.getDiagnosticResult(studentId)?.toDiagnosticReport()?.let { calculateMastery(studentId, it) }.orEmpty()
        return buildProgress(rows.map { row ->
            Triple(row.axis, row.startedAt, row.isCorrect to row.elapsedMillis)
        }).mapIndexed { index, progress ->
            val recent = recentByAxis[DiagnosticAssessmentEngine.AXES[index]].orEmpty()
            progress.copy(mastery = masteryByAxis[DiagnosticAssessmentEngine.AXES[index]], latestPractice = recent.takeIf { it.isNotEmpty() }?.let {
                cl.kmat.ia.domain.model.LatestPracticeSummary(
                    correct = it.count { row -> row.isCorrect }, total = it.size,
                    averageResponseMillis = it.map { row -> row.elapsedMillis }.average().toLong(),
                    averageExpectedMillis = it.map { row -> row.expectedTimeMillis }.average().toLong()
                )
            })
        }
    }

    private fun buildProgress(rows: List<Triple<String, Long, Pair<Boolean, Long>>>): List<StudentSubjectProgress> =
        DiagnosticAssessmentEngine.AXES.map { axis ->
            val subjectRows = rows.filter { it.first == axis }
            val dailyRows = subjectRows.groupBy { row ->
                LocalDate.ofInstant(
                    java.time.Instant.ofEpochMilli(row.second),
                    java.time.ZoneId.systemDefault()
                )
            }
            StudentSubjectProgress(
                subject = DiagnosticAssessmentEngine.AXIS_LABELS.getValue(axis),
                correct = subjectRows.count { it.third.first },
                incorrect = subjectRows.count { !it.third.first },
                averageResponseMillis = subjectRows.map { it.third.second }.average().takeIf { subjectRows.isNotEmpty() }?.toLong() ?: 0L,
                history = dailyRows.map { (date, dateRows) ->
                    SubjectProgressPoint(
                        dateEpochMillis = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        correct = dateRows.count { it.third.first },
                        incorrect = dateRows.count { !it.third.first },
                        averageResponseMillis = dateRows.map { it.third.second }.average().toLong()
                    )
                }.sortedBy { it.dateEpochMillis }
            )
        }
    override suspend fun saveAttempt(
        sessionId: String,
        exercise: Exercise,
        answer: String,
        elapsedMillis: Long,
        strokes: List<HandwritingStroke>
    ): SavedAttempt = database.withTransaction {
        val session = requireNotNull(dao.getSession(sessionId)) { "La sesión local no existe" }
        dao.getSessionAttempts(sessionId).firstOrNull { it.exerciseId == exercise.id }?.let {
            return@withTransaction SavedAttempt(it.id, it.isCorrect)
        }
        require(session.status == "ACTIVE") { "Session already completed on another device" }
        val attemptNumber = 1
        val isCorrect = answer.trim() == exercise.expectedAnswer.trim()
        val now = System.currentTimeMillis()
        val attempt = ExerciseAttemptEntity(
            id = UUID.nameUUIDFromBytes("$sessionId:${exercise.id}".toByteArray(Charsets.UTF_8)).toString(),
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
        .put("exercise", exercise.toSyncJson())
        .put("teacher_assignment_id", exercise.teacherAssignmentId ?: JSONObject.NULL)
        .put("exercise_version_remote_id", exercise.remoteExerciseVersionId ?: JSONObject.NULL)
        .put("answer", attempt.answer)
        .put("is_correct", attempt.isCorrect)
        .put("attempt_number", attempt.attemptNumber)
        .put("elapsed_millis", attempt.elapsedMillis)
        .put("recorded_at", attempt.createdAt)
        .put("strokes", JSONArray(strokes.map { it.points }))
        .toString()

    private fun LocalExerciseEntity.toDomain() = Exercise(
        id, content, statement, expectedAnswer, difficulty, expectedTimeMillis = expectedTimeMillis,
        axis = axis, contentCode = contentCode, educationLevel = educationLevel, visualSpec = visualSpec
    )

    private fun visualSpecFor(exercise: LocalExerciseEntity): String {
        fun group(symbol: String, label: String, count: Int) = JSONObject()
            .put("symbol", symbol).put("label", label).put("count", count)
        fun groups(vararg values: JSONObject) = JSONObject().put("type", "OBJECTS")
            .put("groups", JSONArray().apply { values.forEach { put(it) } }).toString()
        return when {
            exercise.id.startsWith(AdaptiveLearningEngine.SUM_PREFIX) -> {
                val operands = exercise.id.removePrefix(AdaptiveLearningEngine.SUM_PREFIX).split('-').mapNotNull(String::toIntOrNull)
                if (operands.size == 2) JSONObject().put("type", "EQUATION_OBJECTS")
                    .put("symbol", "🍎").put("label", "Manzanas").put("left", operands[0])
                    .put("right", operands[1]).put("operation", "+").toString() else ""
            }
            exercise.id == "offline-count-stars-5" || exercise.id == "diag-data-1-2" ->
                groups(group("⭐", "Estrellas", if (exercise.id == "offline-count-stars-5") 5 else 4))
            exercise.id.startsWith("diag-pattern-") -> {
                val sequence = Regex("\\d+|__").findAll(exercise.statement).map { it.value }
                    .map { if (it == "__") "?" else it }.toList()
                JSONObject().put("type", "SEQUENCE")
                    .put("items", JSONArray(sequence)).toString()
            }
            exercise.id.startsWith(AdaptiveLearningEngine.COUNT_PREFIX) -> ""
            exercise.axis == "GEOMETRIA" -> {
                val shapes = when (exercise.id) {
                    "diag-geometry-1-1" -> listOf("triangle" to 1)
                    "diag-geometry-1-2" -> listOf("square" to 1)
                    "diag-geometry-2-1" -> listOf("pentagon" to 2)
                    "diag-geometry-2-2" -> listOf("rectangle" to 1)
                    "diag-geometry-3-1" -> listOf("square" to 1, "triangle" to 1)
                    else -> listOf("pentagon" to 1, "triangle" to 1)
                }
                JSONObject().put("type", "SHAPES").put("groups", JSONArray().apply {
                    shapes.forEach { (shape, count) -> put(JSONObject().put("shape", shape).put("count", count)) }
                }).toString()
            }
            exercise.axis == "MEDICION" -> {
                val values = when (exercise.id) {
                    "diag-measure-1-1" -> listOf("Cinta A · 5 cm" to 5, "Cinta B · 8 cm" to 8)
                    "diag-measure-1-2" -> listOf("4 cm" to 4, "7 cm" to 7)
                    "diag-measure-2-1" -> listOf("Cinta A · clips" to 8, "Cinta B · clips" to 13)
                    "diag-measure-2-2" -> listOf("Canción A · min" to 3, "Canción B · min" to 5)
                    "diag-measure-3-1" -> listOf("Mesa · bloques" to 12, "Silla · bloques" to 8)
                    else -> listOf("Actividad A · min" to 15, "Actividad B · min" to 8)
                }
                JSONObject().put("type", "MEASURE").put("groups", JSONArray().apply {
                    values.forEach { (label, count) -> put(JSONObject().put("label", label).put("count", count)) }
                }).toString()
            }
            exercise.id == "diag-data-1-1" -> groups(group("🍎", "Manzanas", 3), group("🍐", "Peras", 2))
            exercise.id == "diag-data-2-1" -> groups(group("🐶", "Perros", 8), group("🐱", "Gatos", 5))
            exercise.id == "diag-data-2-2" -> groups(group("🍎", "Manzana", 8), group("🍊", "Naranja", 4))
            exercise.id == "diag-data-3-1" -> groups(group("🔴", "Votos rojos", 6), group("🔵", "Votos azules", 4))
            exercise.id == "diag-data-3-2" -> groups(group("⚽", "Fútbol", 7), group("🏀", "Básquetbol", 5))
            else -> ""
        }
    }

    private fun HandwritingStroke.encode(): String = points.joinToString(";") { point ->
        "${point.x},${point.y},${point.recordedAt}"
    }

    private companion object {
        const val LOCAL_STUDENT_ID = "local-tomas"
        const val MAX_RETRIES_PER_SESSION = 2
    }
}
