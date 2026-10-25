package cl.kmat.ia.domain.model

data class StudentProfile(
    val id: String,
    val name: String,
    val educationLevel: String
)

sealed interface StudentProfilesResult {
    data class Success(val profiles: List<StudentProfile>) : StudentProfilesResult
    data object NotConfigured : StudentProfilesResult
    data object Unauthorized : StudentProfilesResult
    data object PermissionDenied : StudentProfilesResult
    data object ServiceUnavailable : StudentProfilesResult
    data object InvalidResponse : StudentProfilesResult
    data object NetworkError : StudentProfilesResult
}

sealed interface CreateStudentProfileResult {
    data class Success(val profile: StudentProfile) : CreateStudentProfileResult
    data object NotConfigured : CreateStudentProfileResult
    data object Unauthorized : CreateStudentProfileResult
    data object InvalidRequest : CreateStudentProfileResult
    data object NetworkError : CreateStudentProfileResult
}

sealed interface DeleteStudentProfileResult {
    data object Success : DeleteStudentProfileResult
    data object NotConfigured : DeleteStudentProfileResult
    data object Unauthorized : DeleteStudentProfileResult
    data object PermissionDenied : DeleteStudentProfileResult
    data object SharedProfile : DeleteStudentProfileResult
    data object LocalCleanupFailed : DeleteStudentProfileResult
    data object NetworkError : DeleteStudentProfileResult
    data object ServiceUnavailable : DeleteStudentProfileResult
}

sealed interface JoinTeachingGroupResult {
    data class Success(val groupName: String) : JoinTeachingGroupResult
    data object AlreadyJoined : JoinTeachingGroupResult
    data object InvalidCode : JoinTeachingGroupResult
    data object Unauthorized : JoinTeachingGroupResult
    data object NotConfigured : JoinTeachingGroupResult
    data object NetworkError : JoinTeachingGroupResult
}
