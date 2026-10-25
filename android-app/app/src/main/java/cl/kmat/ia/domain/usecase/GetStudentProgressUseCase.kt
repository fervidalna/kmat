package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.repository.LearningSessionRepository

class GetStudentProgressUseCase(
    private val repository: LearningSessionRepository
) {
    suspend operator fun invoke(studentId: String): List<StudentSubjectProgress> =
        repository.getPracticeProgress(studentId)
}
