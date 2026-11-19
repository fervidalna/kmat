package cl.kmat.ia.student

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
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
import cl.kmat.ia.domain.model.StudentProgressSnapshot
import cl.kmat.ia.domain.model.StudentSubjectProgress
import cl.kmat.ia.domain.model.ReinforcementSuggestion
import cl.kmat.ia.domain.model.SubjectSuggestionState
import cl.kmat.ia.domain.model.SuggestionResult
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
        compose.onNodeWithText("2 estudiantes vinculados").assertIsDisplayed()
        compose.onNodeWithText("Ana nivel uno").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("BASICO_1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ana nivel dos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("BASICO_2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun teacherOpensSelectedStudentsSharedProgressPage() {
        compose.runOnUiThread {
            compose.activity.setContent { KMatTheme { TeacherWorkspaceScreen(MultiLevelTeachingRepository(), {}) } }
        }
        compose.onNodeWithText("Ver estudiantes").performScrollTo().performClick()
        compose.onAllNodesWithText("Progreso")[0].performScrollTo().performClick()
        compose.onNodeWithText("Mi progreso").assertIsDisplayed()
        compose.onNodeWithText("Ana nivel uno").assertIsDisplayed()
    }

    @Test
    fun teacherAssignsFromTheSelectedSubjectAndLevel() {
        val repo = MultiLevelTeachingRepository()
        compose.runOnUiThread { compose.activity.setContent { KMatTheme { TeacherWorkspaceScreen(repo, {}) } } }
        compose.onNodeWithText("Ver estudiantes").performScrollTo().performClick()
        compose.onAllNodesWithText("Progreso")[0].performScrollTo().performClick()
        compose.onNodeWithText("Asignar trabajo").performScrollTo().performClick()
        compose.onNodeWithText("Materia: Números y operaciones").assertIsDisplayed()
        compose.onNodeWithText("Inicial").performClick()
        compose.onNodeWithText("Sumas simples · 1 ejercicios").performClick()
        compose.onNodeWithText("Asignar").performClick()
        compose.waitUntil(5_000) { repo.assigned }
    }

    @Test
    fun suggestionIsGeneratedOnlyOnRequestAndCanBeUsedAlongsideAssignment() {
        val repo = MultiLevelTeachingRepository()
        compose.runOnUiThread { compose.activity.setContent { KMatTheme { TeacherWorkspaceScreen(repo, {}) } } }
        compose.onNodeWithText("Ver estudiantes").performScrollTo().performClick()
        compose.onAllNodesWithText("Progreso")[0].performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Generar sugerencia").fetchSemanticsNodes().isNotEmpty() }
        check(repo.generated == 0)
        compose.onNodeWithText("Generar sugerencia").performScrollTo().performClick()
        compose.waitUntil(5_000) { repo.generated == 1 }
        compose.onNodeWithText("Conteo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mantener la dificultad (Inicial). Asignar 2 ejercicios de «Conteo».").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Generar otra sugerencia").performScrollTo().performClick()
        compose.waitUntil(5_000) { repo.generated == 2 }
        compose.onNodeWithText("Asignar trabajo").performScrollTo().performClick()
        compose.onNodeWithText("Materia: Números y operaciones").assertIsDisplayed()
    }

    private class MultiLevelTeachingRepository : TeachingRepository {
        var assigned = false
        var generated = 0
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
        override suspend fun listReinforcementOptionsByLevel() = TeachingResult.Success(listOf(
            ReinforcementOption("content-1", "Sumas simples", 1, "NUMEROS_OPERACIONES", 0)
        ))
        override suspend fun assignReinforcementByLevel(studentId: String, option: ReinforcementOption, count: Int, note: String): TeachingResult<Int> {
            check(studentId == "student-1" && option.axis == "NUMEROS_OPERACIONES" && option.difficulty == 0 && count == 1)
            assigned = true
            return TeachingResult.Success(count)
        }
        override suspend fun getStudentReport(studentId: String) = TeachingResult.Success(
            StudentReport(studentId, "Estudiante", "BASICO_1", 0, 0, 0, emptyList(), emptyList())
        )
        override suspend fun getStudentProgress(studentId: String) = TeachingResult.Success(StudentProgressSnapshot(
            listOf(StudentSubjectProgress("Números y operaciones", 0, 0, 0, emptyList())), null
        ))
        override suspend fun getReinforcementSuggestions(studentId: String): SuggestionResult = error("No debe precargar sugerencias")
        private fun generatedResult(): SuggestionResult = SuggestionResult.Success(listOf(
            SubjectSuggestionState("GLOBAL", 5, 5, 0, generated == 0,
                if (generated == 0) null else ReinforcementSuggestion("Conteo", "2 correctas y 3 incorrectas.",
                    "Mantener la dificultad (Inicial). Asignar 2 ejercicios de «Conteo».", "2026-11-13T00:00:00Z", 5))
        ))
        override suspend fun generateReinforcementSuggestion(studentId: String): SuggestionResult {
            check(studentId == "student-1")
            generated++
            return generatedResult()
        }
        override suspend fun getGroupReport(groupId: String) = TeachingResult.Success(emptyList<GroupStudentReport>())
    }
}
