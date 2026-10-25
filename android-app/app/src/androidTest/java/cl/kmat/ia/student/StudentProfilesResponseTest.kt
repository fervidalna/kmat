package cl.kmat.ia.student

import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.data.student.decodeStudentProfilesResponse
import cl.kmat.ia.data.student.decodeDeleteStudentProfileResponse
import cl.kmat.ia.domain.model.DeleteStudentProfileResult
import cl.kmat.ia.domain.model.StudentProfilesResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudentProfilesResponseTest {
    @Test
    fun deletionRequiresExplicitServerConfirmation() {
        assertEquals(DeleteStudentProfileResult.Success, decodeDeleteStudentProfileResponse(200, "true"))
        assertEquals(DeleteStudentProfileResult.ServiceUnavailable, decodeDeleteStudentProfileResponse(200, "false"))
        assertEquals(DeleteStudentProfileResult.ServiceUnavailable, decodeDeleteStudentProfileResponse(200, "{}"))
        assertEquals(DeleteStudentProfileResult.PermissionDenied, decodeDeleteStudentProfileResponse(403, "{}"))
        assertEquals(DeleteStudentProfileResult.Unauthorized, decodeDeleteStudentProfileResponse(401, "{}"))
        assertEquals(DeleteStudentProfileResult.SharedProfile,
            decodeDeleteStudentProfileResponse(400, """{"code":"KM001"}"""))
    }
    @Test
    fun parsesAllSavedProfiles() {
        val body = (1..5).joinToString(prefix = "[", postfix = "]") {
            """{"id_estudiante":"student-$it","nombre_mostrado":"Perfil $it","codigo_nivel_educativo":"BASICO_1"}"""
        }
        val result = decodeStudentProfilesResponse(200, body)
        assertTrue(result is StudentProfilesResult.Success)
        assertEquals((1..5).map { "student-$it" }, (result as StudentProfilesResult.Success).profiles.map { it.id })
    }

    @Test
    fun realPermissionErrorIsNotAnExpiredSessionOrAnEmptyList() {
        val result = decodeStudentProfilesResponse(403,
            """{"code":"42501","message":"permission denied for table estudiante"}""")
        assertEquals(StudentProfilesResult.PermissionDenied, result)
        assertEquals(StudentProfilesResult.Unauthorized, decodeStudentProfilesResponse(401, "{}"))
    }

    @Test
    fun onlyAValidEmptyArrayMeansNoProfiles() {
        assertEquals(StudentProfilesResult.Success(emptyList()), decodeStudentProfilesResponse(200, "[]"))
        assertEquals(StudentProfilesResult.InvalidResponse, decodeStudentProfilesResponse(200, "{}"))
        assertEquals(StudentProfilesResult.InvalidResponse, decodeStudentProfilesResponse(200, "[{\"id_estudiante\":\"one\"}]"))
        assertEquals(StudentProfilesResult.ServiceUnavailable, decodeStudentProfilesResponse(404, "{}"))
    }
}
