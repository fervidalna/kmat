package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.GroupStudentReport
import cl.kmat.ia.domain.model.ReinforcementOption
import cl.kmat.ia.domain.model.StudentReport
import cl.kmat.ia.domain.model.TeachingGroup
import cl.kmat.ia.domain.model.TeachingResult
import cl.kmat.ia.domain.model.TeachingStudent
import cl.kmat.ia.domain.model.StudentProgressSnapshot
import cl.kmat.ia.domain.model.SuggestionResult

interface TeachingRepository {
    suspend fun listGroups(): TeachingResult<List<TeachingGroup>>
    suspend fun createGroup(name: String): TeachingResult<TeachingGroup>
    suspend fun deleteGroup(groupId: String): TeachingResult<Boolean> = TeachingResult.InvalidRequest
    suspend fun listStudents(groupId: String): TeachingResult<List<TeachingStudent>>
    suspend fun listReinforcementOptions(): TeachingResult<List<ReinforcementOption>>
    suspend fun assignReinforcement(studentId: String, contentId: String, count: Int, note: String): TeachingResult<Int>
    suspend fun listReinforcementOptionsByLevel(): TeachingResult<List<ReinforcementOption>> = TeachingResult.InvalidRequest
    suspend fun assignReinforcementByLevel(studentId: String, option: ReinforcementOption, count: Int, note: String): TeachingResult<Int> = TeachingResult.InvalidRequest
    suspend fun getStudentReport(studentId: String): TeachingResult<StudentReport>
    suspend fun getStudentProgress(studentId: String): TeachingResult<StudentProgressSnapshot> = TeachingResult.InvalidRequest
    suspend fun getReinforcementSuggestions(studentId: String): SuggestionResult = SuggestionResult.NotConfigured
    suspend fun generateReinforcementSuggestion(studentId: String): SuggestionResult = SuggestionResult.NotConfigured
    suspend fun getGroupReport(groupId: String): TeachingResult<List<GroupStudentReport>>
}
