package cl.kmat.ia.domain.model

data class Exercise(
    val id: String,
    val content: String,
    val statement: String,
    val expectedAnswer: String,
    val difficulty: Int,
    val teacherAssignmentId: String? = null,
    val remoteExerciseVersionId: String? = null,
    val expectedTimeMillis: Long = 30_000L,
    val axis: String = "NUMEROS_OPERACIONES",
    val contentCode: String = "GENERAL",
    val educationLevel: String = "AMBOS",
    val visualSpec: String = ""
)
