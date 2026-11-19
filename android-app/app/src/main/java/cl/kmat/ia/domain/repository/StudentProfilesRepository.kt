package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.CreateStudentProfileResult
import cl.kmat.ia.domain.model.DeleteStudentProfileResult
import cl.kmat.ia.domain.model.StudentProfilesResult
import cl.kmat.ia.domain.model.JoinTeachingGroupResult
import cl.kmat.ia.domain.model.LeaveTeachingGroupResult
import cl.kmat.ia.domain.model.StudentTeachingGroupsResult

interface StudentProfilesRepository {
    /** Returns the last profile snapshot for the currently authenticated account, or null if none was cached. */
    suspend fun cachedMine(): List<cl.kmat.ia.domain.model.StudentProfile>? = null
    suspend fun listMine(): StudentProfilesResult
    suspend fun createMine(name: String, educationLevel: String): CreateStudentProfileResult
    suspend fun deleteMine(studentId: String): DeleteStudentProfileResult
    suspend fun joinTeachingGroup(studentId: String, code: String): JoinTeachingGroupResult
    suspend fun listJoinedTeachingGroups(studentId: String): StudentTeachingGroupsResult = StudentTeachingGroupsResult.NotConfigured
    suspend fun leaveTeachingGroup(studentId: String, groupId: String): LeaveTeachingGroupResult = LeaveTeachingGroupResult.NotConfigured
}
