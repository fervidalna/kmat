package cl.kmat.ia.student

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.kmat.ia.MainActivity
import cl.kmat.ia.domain.model.GroupStudentReport
import cl.kmat.ia.domain.model.ReinforcementOption
import cl.kmat.ia.domain.model.StudentReport
import cl.kmat.ia.domain.model.TeachingGroup
import cl.kmat.ia.domain.model.TeachingResult
import cl.kmat.ia.domain.model.TeachingStudent
import cl.kmat.ia.domain.repository.TeachingRepository
import cl.kmat.ia.presentation.design.KMatTheme
import cl.kmat.ia.presentation.teacher.TeacherWorkspaceScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeacherWorkspaceScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun teacherCanSeeStudentsFromDifferentLevelsInTheSameRoom() {
        compose.runOnUiThread {
            compose.activity.setContent {
                KMatTheme { TeacherWorkspaceScreen(MultiLevelTeachingRepository(), {}) }
            }
        }

        compose.onNodeWithText("Ver estudiantes").performScrollTo().performClick()
        compose.onNodeWithText("Ana nivel uno").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("BASICO_1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ana nivel dos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("BASICO_2").performScrollTo().assertIsDisplayed()
    }

    private class MultiLevelTeachingRepository : TeachingRepository {
        private val group = TeachingGroup("group-1", "Sala mixta", "KMAT-AB12345", 2)
        override suspend fun listGroups() = TeachingResult.Success(listOf(group))
        override suspend fun createGroup(name: String) = TeachingResult.Success(group.copy(name = name))
        override suspend fun listStudents(groupId: String) = TeachingResult.Success(
            listOf(
                TeachingStudent("student-1", "Ana nivel uno", "BASICO_1"),
                TeachingStudent("student-2", "Ana nivel dos", "BASICO_2")
            )
        )
        override suspend fun listReinforcementOptions() = TeachingResult.Success(emptyList<ReinforcementOption>())
        override suspend fun assignReinforcement(studentId: String, contentId: String, count: Int, note: String) =
            TeachingResult.Success(count)
        override suspend fun getStudentReport(studentId: String) = TeachingResult.Success(
            StudentReport(studentId, "Estudiante", "BASICO_1", 0, 0, 0, emptyList(), emptyList())
        )
        override suspend fun getGroupReport(groupId: String) = TeachingResult.Success(emptyList<GroupStudentReport>())
    }
}
