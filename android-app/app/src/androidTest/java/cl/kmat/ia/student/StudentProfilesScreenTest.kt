package cl.kmat.ia.student

import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.MainActivity
import cl.kmat.ia.domain.model.CreateStudentProfileResult
import cl.kmat.ia.domain.model.DeleteStudentProfileResult
import cl.kmat.ia.domain.model.JoinTeachingGroupResult
import cl.kmat.ia.domain.model.StudentProfile
import cl.kmat.ia.domain.model.StudentProfilesResult
import cl.kmat.ia.domain.repository.StudentProfilesRepository
import cl.kmat.ia.presentation.design.KMatTheme
import cl.kmat.ia.presentation.student.StudentProfilesScreen
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudentProfilesScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val savedProfiles = (1..5).map { StudentProfile("student-$it", "Perfil $it", "BASICO_1") }

    @Test
    fun reopeningScreenLoadsEverySavedProfileWithoutCreatingAny() {
        val repository = FakeProfilesRepository(StudentProfilesResult.Success(savedProfiles))
        val entry = mutableStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme {
                    key(entry.value) { StudentProfilesScreen(repository, {}, {}) }
                }
            }
        }
        savedProfiles.forEach { compose.onNodeWithText(it.name).performScrollTo().assertIsDisplayed() }
        compose.runOnIdle { entry.value++ }
        savedProfiles.forEach { compose.onNodeWithText(it.name).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Crear perfil de estudiante").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, repository.listCalls)
            assertEquals(0, repository.createCalls)
        }
    }

    @Test
    fun permissionFailureShowsRetryAndNeverOffersCreation() {
        val repository = FakeProfilesRepository(StudentProfilesResult.PermissionDenied)
        show(repository)
        compose.onNodeWithText("problema de permisos", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Agregar").assertDoesNotExist()
        compose.onNodeWithText("Crear perfil de estudiante").assertDoesNotExist()
        compose.runOnIdle { repository.result = StudentProfilesResult.Success(savedProfiles) }
        compose.onNodeWithText("Reintentar carga").performScrollTo().performClick()
        savedProfiles.forEach { compose.onNodeWithText(it.name).performScrollTo().assertIsDisplayed() }
        compose.runOnIdle { assertEquals(0, repository.createCalls) }
    }

    @Test
    fun confirmedEmptyListRequiresExplicitChoiceToCreate() {
        val repository = FakeProfilesRepository(StudentProfilesResult.Success(emptyList()))
        show(repository)
        compose.onNodeWithText("Esta cuenta aún no tiene perfiles de estudiante vinculados.").assertIsDisplayed()
        compose.onNodeWithText("Crear perfil de estudiante").assertDoesNotExist()
        compose.onNodeWithText("Agregar").performScrollTo().performClick()
        compose.onNodeWithText("Crear perfil de estudiante").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        compose.onNodeWithText("Crear perfil de estudiante").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, repository.createCalls) }
    }

    @Test
    fun trashButtonsAreHiddenUntilDeleteProfilesModeIsEnabled() {
        show(FakeProfilesRepository(StudentProfilesResult.Success(savedProfiles)))
        compose.onNodeWithTag("delete-profile-student-1").assertDoesNotExist()
        enterDeletionMode()
        compose.onNodeWithTag("delete-profile-student-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancelar eliminación").assertIsDisplayed()
    }

    @Test
    fun cancellingDeletionDoesNotRemoveOrSelectTheStudent() {
        val repository = FakeProfilesRepository(StudentProfilesResult.Success(savedProfiles))
        var selected = false
        show(repository) { selected = true }
        enterDeletionMode()
        compose.onNodeWithTag("delete-profile-student-1").performScrollTo().performClick()
        compose.onNodeWithText("¿Estás seguro de que quieres eliminar el perfil de Perfil 1?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Perfil 1").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), repository.deletedIds)
            assertEquals(false, selected)
        }
    }

    @Test
    fun confirmingDeletionRemovesOnlyTheChosenProfileAndReloads() {
        val repository = FakeProfilesRepository(StudentProfilesResult.Success(savedProfiles))
        show(repository)
        enterDeletionMode()
        compose.onNodeWithTag("delete-profile-student-2").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-delete-profile").performClick()
        compose.onNodeWithText("Perfil 2").assertDoesNotExist()
        savedProfiles.filterNot { it.id == "student-2" }.forEach {
            compose.onNodeWithText(it.name).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle {
            assertEquals(listOf("student-2"), repository.deletedIds)
            assertEquals(2, repository.listCalls)
        }
    }

    @Test
    fun deniedDeletionKeepsTheProfileAndShowsTheError() {
        val repository = FakeProfilesRepository(StudentProfilesResult.Success(savedProfiles))
        repository.deleteResult = DeleteStudentProfileResult.PermissionDenied
        show(repository)
        enterDeletionMode()
        compose.onNodeWithTag("delete-profile-student-1").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-delete-profile").performClick()
        compose.onNodeWithText("No tienes permiso para eliminar este perfil o ya fue borrado. Actualiza la lista.").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Perfil 1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deletionWaitsForServerConfirmationAndLastProfileReturnsToEmptyList() {
        val repository = FakeProfilesRepository(StudentProfilesResult.Success(savedProfiles.take(1)))
        val response = CompletableDeferred<DeleteStudentProfileResult>()
        repository.deleteResponse = response
        show(repository)
        enterDeletionMode()
        compose.onNodeWithTag("delete-profile-student-1").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-delete-profile").performClick()
        compose.onNodeWithTag("confirm-delete-profile").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsNotEnabled()
        compose.runOnIdle { response.complete(DeleteStudentProfileResult.Success) }
        compose.onNodeWithText("Esta cuenta aún no tiene perfiles de estudiante vinculados.").assertIsDisplayed()
        compose.onNodeWithText("Crear perfil de estudiante").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("student-1"), repository.deletedIds) }
    }

    private fun show(repository: StudentProfilesRepository, onSelect: (StudentProfile) -> Unit = {}) {
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme { StudentProfilesScreen(repository, onSelect, {}) }
            }
        }
    }

    private fun enterDeletionMode() {
        compose.onNodeWithText("Eliminar perfiles").performScrollTo().performClick()
    }

    private class FakeProfilesRepository(var result: StudentProfilesResult) : StudentProfilesRepository {
        var listCalls = 0
        var createCalls = 0
        val deletedIds = mutableListOf<String>()
        var deleteResult: DeleteStudentProfileResult = DeleteStudentProfileResult.Success
        var deleteResponse: CompletableDeferred<DeleteStudentProfileResult>? = null
        override suspend fun listMine(): StudentProfilesResult {
            listCalls++
            return result
        }
        override suspend fun createMine(name: String, educationLevel: String): CreateStudentProfileResult {
            createCalls++
            error("These flows must not create any students")
        }
        override suspend fun deleteMine(studentId: String): DeleteStudentProfileResult {
            deletedIds += studentId
            val outcome = deleteResponse?.await() ?: deleteResult
            if (outcome == DeleteStudentProfileResult.Success) {
                val current = result as StudentProfilesResult.Success
                result = current.copy(profiles = current.profiles.filterNot { it.id == studentId })
            }
            return outcome
        }
        override suspend fun joinTeachingGroup(studentId: String, code: String): JoinTeachingGroupResult =
            JoinTeachingGroupResult.Success("Sala prueba")
    }
}
