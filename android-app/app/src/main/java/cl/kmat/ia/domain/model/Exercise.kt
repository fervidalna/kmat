package cl.kmat.ia.domain.model

data class Exercise(
    val id: String,
    val content: String,
    val statement: String,
    val expectedAnswer: String,
    val difficulty: Int
)
