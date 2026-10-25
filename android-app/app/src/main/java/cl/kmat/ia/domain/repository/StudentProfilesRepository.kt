package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.CreateStudentProfileResult
import cl.kmat.ia.domain.model.DeleteStudentProfileResult
import cl.kmat.ia.domain.model.StudentProfilesResult
import cl.kmat.ia.domain.model.JoinTeachingGroupResult

interface StudentProfilesRepository {
    suspend fun listMine(): StudentProfilesResult
    suspend fun createMine(name: String, educationLevel: String): CreateStudentProfileResult
    suspend fun deleteMine(studentId: String): DeleteStudentProfileResult
    suspend fun joinTeachingGroup(studentId: String, code: String): JoinTeachingGroupResult
}
