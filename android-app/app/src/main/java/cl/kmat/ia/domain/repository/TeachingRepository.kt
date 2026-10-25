package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.GroupStudentReport
import cl.kmat.ia.domain.model.ReinforcementOption
import cl.kmat.ia.domain.model.StudentReport
import cl.kmat.ia.domain.model.TeachingGroup
import cl.kmat.ia.domain.model.TeachingResult
import cl.kmat.ia.domain.model.TeachingStudent

interface TeachingRepository {
    suspend fun listGroups(): TeachingResult<List<TeachingGroup>>
    suspend fun createGroup(name: String): TeachingResult<TeachingGroup>
    suspend fun listStudents(groupId: String): TeachingResult<List<TeachingStudent>>
    suspend fun listReinforcementOptions(): TeachingResult<List<ReinforcementOption>>
    suspend fun assignReinforcement(studentId: String, contentId: String, count: Int, note: String): TeachingResult<Int>
    suspend fun getStudentReport(studentId: String): TeachingResult<StudentReport>
    suspend fun getGroupReport(groupId: String): TeachingResult<List<GroupStudentReport>>
}
