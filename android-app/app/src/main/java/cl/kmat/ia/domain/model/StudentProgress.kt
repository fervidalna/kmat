package cl.kmat.ia.domain.model

import cl.kmat.ia.domain.usecase.SubjectMastery

data class StudentProgressSnapshot(val subjects: List<StudentSubjectProgress>, val diagnosticLevel: Int?)

data class StudentSubjectProgress(
    val subject: String,
    val correct: Int,
    val incorrect: Int,
    val averageResponseMillis: Long,
    val history: List<SubjectProgressPoint>,
    val latestPractice: LatestPracticeSummary? = null,
    val mastery: SubjectMastery? = null
)

data class LatestPracticeSummary(
    val correct: Int,
    val total: Int,
    val averageResponseMillis: Long,
    val averageExpectedMillis: Long
)

data class SubjectProgressPoint(
    val dateEpochMillis: Long,
    val correct: Int,
    val incorrect: Int,
    val averageResponseMillis: Long
)
