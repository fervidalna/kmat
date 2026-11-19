package cl.kmat.ia.data.sync

import cl.kmat.ia.data.local.LocalExerciseEntity
import cl.kmat.ia.data.local.StudentDiagnosticResultEntity
import cl.kmat.ia.domain.model.Exercise
import org.json.JSONArray
import org.json.JSONObject

internal fun Exercise.toSyncJson(): JSONObject = JSONObject()
    .put("id", id).put("content", content).put("statement", statement)
    .put("expectedAnswer", expectedAnswer).put("difficulty", difficulty)
    .put("teacherAssignmentId", teacherAssignmentId).put("remoteExerciseVersionId", remoteExerciseVersionId)
    .put("expectedTimeMillis", expectedTimeMillis).put("axis", axis)
    .put("contentCode", contentCode).put("educationLevel", educationLevel).put("visualSpec", visualSpec)

internal fun JSONObject.toSyncExercise(): Exercise = Exercise(
    id = getString("id"), content = getString("content"), statement = getString("statement"),
    expectedAnswer = getString("expectedAnswer"), difficulty = getInt("difficulty"),
    teacherAssignmentId = optString("teacherAssignmentId").takeIf { it.isNotBlank() && it != "null" },
    remoteExerciseVersionId = optString("remoteExerciseVersionId").takeIf { it.isNotBlank() && it != "null" },
    expectedTimeMillis = optLong("expectedTimeMillis", 30_000),
    axis = optString("axis", "NUMEROS_OPERACIONES"), contentCode = optString("contentCode", "GENERAL"),
    educationLevel = optString("educationLevel", "AMBOS"), visualSpec = optString("visualSpec", "")
)

internal fun Exercise.toLocalExercise() = LocalExerciseEntity(
    id, content, statement, expectedAnswer, difficulty, System.currentTimeMillis(),
    expectedTimeMillis, axis, contentCode, educationLevel, visualSpec
)

internal fun StudentDiagnosticResultEntity.toSyncJson(): JSONObject = JSONObject()
    .put("overallLevel", overallLevel).put("axes", JSONArray(axisResultsJson))
    .put("questionCount", questionCount).put("completedAt", completedAt)

internal fun JSONObject.toDiagnosticResult(studentId: String) = StudentDiagnosticResultEntity(
    studentId, getInt("overallLevel"), getJSONArray("axes").toString(), getInt("questionCount"), getLong("completedAt")
)
