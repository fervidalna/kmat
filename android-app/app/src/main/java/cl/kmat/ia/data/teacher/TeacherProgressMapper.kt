package cl.kmat.ia.data.teacher

import cl.kmat.ia.data.sync.toSyncExercise
import cl.kmat.ia.domain.model.Exercise
import cl.kmat.ia.domain.model.LatestPracticeSummary
import cl.kmat.ia.domain.model.StudentProgressSnapshot
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.model.SubjectProgressPoint
import cl.kmat.ia.domain.usecase.DiagnosticAssessmentEngine
import cl.kmat.ia.domain.usecase.MasteryAttempt
import cl.kmat.ia.domain.usecase.MasterySession
import cl.kmat.ia.domain.usecase.SubjectMasteryEngine
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

internal object TeacherProgressMapper {
    private data class Attempt(val exercise: Exercise, val correct: Boolean, val elapsed: Long)
    private data class Session(val start: Long, val end: Long?, val type: String, val completed: Boolean,
                               val report: JSONObject?, val attempts: List<Attempt>)

    fun map(rows: List<JSONObject>): StudentProgressSnapshot {
        val sessions = rows.map { row ->
            val planJson = row.optJSONArray("plan")
            val plan = (0 until (planJson?.length() ?: 0)).map { planJson!!.getJSONObject(it).toSyncExercise() }.associateBy { it.id }
            val attempts = row.getJSONArray("attempts")
            Session(row.getLong("startedAt"), if (row.isNull("endedAt")) null else row.getLong("endedAt"),
                row.getString("sessionType"), row.getString("status") == "COMPLETED", row.optJSONObject("report"),
                (0 until attempts.length()).mapNotNull { index ->
                    val a = attempts.getJSONObject(index)
                    val exercise = a.optJSONObject("exercise")?.let { it.toSyncExercise() } ?: plan[a.getString("exerciseId")]
                    exercise?.let { Attempt(it, a.getBoolean("isCorrect"), a.getLong("elapsedMillis")) }
                })
        }
        val diagnostic = sessions.filter { it.type == "DIAGNOSTICO" && it.completed && it.report != null }
            .minByOrNull { it.report!!.optLong("completedAt", it.end ?: it.start) }?.report
        val after = diagnostic?.optLong("completedAt", 0L) ?: 0L
        val completed = sessions.filter { it.type == "PRACTICA" && it.completed }.sortedWith(compareBy({ it.end }, { it.start }))
        val masterySessions = completed.filter { it.start >= after }.map { session ->
            MasterySession(session.attempts.filter { it.exercise.teacherAssignmentId == null || it.exercise.contentCode == "DOCENTE_CLASIFICADO" }.map {
                MasteryAttempt(it.exercise.axis, it.exercise.difficulty, it.correct)
            })
        }
        val axes = diagnostic?.optJSONArray("axes")
        val levels = (0 until (axes?.length() ?: 0)).associate { index ->
            axes!!.getJSONObject(index).let { it.getString("axis") to it.getInt("level") }
        }
        val subjects = DiagnosticAssessmentEngine.AXES.map { axis ->
            val entries = sessions.flatMap { session -> session.attempts.filter { it.exercise.axis == axis }.map { session.start to it } }
            val daily = entries.groupBy { Instant.ofEpochMilli(it.first).atZone(ZoneId.systemDefault()).toLocalDate() }
            val recent = completed.lastOrNull()?.attempts.orEmpty().filter { it.exercise.axis == axis }
            StudentSubjectProgress(
                subject = DiagnosticAssessmentEngine.AXIS_LABELS.getValue(axis),
                correct = entries.count { it.second.correct }, incorrect = entries.count { !it.second.correct },
                averageResponseMillis = entries.map { it.second.elapsed }.average().takeIf { entries.isNotEmpty() }?.toLong() ?: 0,
                history = daily.map { (date, items) -> SubjectProgressPoint(
                    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    items.count { it.second.correct }, items.count { !it.second.correct }, items.map { it.second.elapsed }.average().toLong()
                ) }.sortedBy { it.dateEpochMillis },
                latestPractice = recent.takeIf { it.isNotEmpty() }?.let { items -> LatestPracticeSummary(
                    items.count { it.correct }, items.size, items.map { it.elapsed }.average().toLong(),
                    items.map { it.exercise.expectedTimeMillis }.average().toLong()
                ) },
                mastery = levels[axis]?.let { SubjectMasteryEngine().calculate(axis, it, masterySessions) }
            )
        }
        return StudentProgressSnapshot(subjects, diagnostic?.optInt("overallLevel"))
    }
}
