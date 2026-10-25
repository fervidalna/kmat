package cl.kmat.ia.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.kmat.ia.presentation.practice.ActiveBreakScreen
import cl.kmat.ia.presentation.practice.FeedbackScreen
import cl.kmat.ia.presentation.home.HomeScreen
import cl.kmat.ia.presentation.common.NavigationPlaceholderScreen
import cl.kmat.ia.presentation.practice.PracticeScreen
import cl.kmat.ia.presentation.practice.SessionSummaryScreen
import cl.kmat.ia.presentation.practice.DiagnosticSummaryScreen
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.model.StudentProfilesResult
import cl.kmat.ia.presentation.practice.ExerciseOutcome
import cl.kmat.ia.presentation.auth.LoginScreen
import cl.kmat.ia.presentation.auth.RegistrationScreen
import cl.kmat.ia.KMatApplication
import cl.kmat.ia.presentation.admin.AdministrationScreen
import cl.kmat.ia.presentation.teacher.TeacherWorkspaceScreen
import cl.kmat.ia.presentation.progress.StudentProgressScreen
import cl.kmat.ia.domain.model.UserArea
import cl.kmat.ia.presentation.student.StudentProfilesScreen
import kotlinx.coroutines.launch

object Route {
    const val Login = "login"
    const val Register = "register"
    const val Administration = "administration"
    const val Teaching = "teaching"
    const val ProfileSelection = "profile-selection"
    const val Home = "home"
    const val DiagnosticSummary = "diagnostic-summary"
    const val Practice = "practice"
    const val PracticeFeedback = "practice-feedback"
    const val ActiveBreak = "active-break"
    const val SessionSummary = "session-summary"
    const val Progress = "progress"
    const val ChildSettings = "child-settings"
    const val AdultAuth = "adult-auth"
    const val AdultHome = "adult-home"
    const val AdultProfiles = "adult-profiles"
    const val AdultProgress = "adult-progress"
    const val AdultLearningSettings = "adult-learning-settings"
}

@Composable
fun KMatNavigation() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as KMatApplication
    val authRepository = application.container.authRepository
    val startDestination = remember {
        if (authRepository.hasStoredSession()) {
            authRepository.storedUserAreaOrNull()?.authenticatedRoute() ?: Route.Login
        } else {
            Route.Login
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val completedSessionResults = remember { mutableStateOf(emptyList<ExerciseOutcome>()) }
    val completedDiagnosticReport = remember { mutableStateOf<DiagnosticReport?>(null) }
    val signOut = {
        coroutineScope.launch { authRepository.signOut() }
        navController.goToLogin()
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = Modifier) {
        composable(Route.Login) {
            LoginScreen(
                onAuthenticated = navController::goToAuthenticatedDestination,
                onRegister = { navController.goTo(Route.Register) }
            )
        }
        composable(Route.Register) {
            RegistrationScreen(
                onAuthenticated = navController::goToAuthenticatedDestination,
                onBack = navController::navigateUp
            )
        }
        composable(Route.ProfileSelection) {
            StudentProfilesScreen(
                repository = application.container.studentProfilesRepository,
                onSelect = { profile ->
                    authRepository.saveActiveStudentId(profile.id)
                    authRepository.saveDisplayName(profile.name)
                    navController.goTo(Route.Home)
                },
                onBack = navController::navigateUp
            )
        }
        composable(Route.Home) {
            HomeScreen(
                displayName = authRepository.storedDisplayNameOrNull() ?: "Usuario",
                studentId = authRepository.activeStudentIdOrNull(),
                studentProfilesRepository = application.container.studentProfilesRepository,
                onNavigate = navController::goTo,
                onSignOut = signOut
            )
        }
        composable(Route.Administration) {
            val isAdministrator = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (authRepository.validateStoredUserArea() == UserArea.ADMINISTRATION) {
                    isAdministrator.value = true
                } else {
                    signOut()
                }
            }
            if (isAdministrator.value) {
                AdministrationScreen(
                    repository = application.container.managedAccountsRepository,
                    onSignOut = signOut
                )
            }
        }
        composable(Route.Teaching) {
            TeacherWorkspaceScreen(
                repository = application.container.teachingRepository,
                onSignOut = signOut
            )
        }
        composable(Route.DiagnosticSummary) {
            val report = completedDiagnosticReport.value
            if (report == null) {
                NavigationPlaceholderScreen(
                    title = "Diagnóstico guardado",
                    description = "El resultado quedó guardado en este dispositivo.",
                    primaryLabel = "Volver al inicio",
                    onPrimary = { navController.goTo(Route.Home) }
                )
            } else DiagnosticSummaryScreen(report) { navController.goTo(Route.Home) }
        }
        composable(Route.Practice) {
            val studentId = authRepository.activeStudentIdOrNull()
            val diagnosticMode = remember(studentId) { mutableStateOf<Boolean?>(null) }
            val schoolLevel = remember(studentId) { mutableStateOf("BASICO_1") }
            LaunchedEffect(studentId) {
                if (studentId != null) {
                    val alreadyDiagnosed = runCatching {
                        application.container.learningSessionRepository.hasCompletedDiagnostic(studentId)
                    }.getOrDefault(false)
                    diagnosticMode.value = !alreadyDiagnosed
                    if (!alreadyDiagnosed) {
                        schoolLevel.value = when (val result = application.container.studentProfilesRepository.listMine()) {
                            is StudentProfilesResult.Success -> result.profiles.firstOrNull { it.id == studentId }?.educationLevel ?: "BASICO_1"
                            else -> "BASICO_1"
                        }
                    }
                }
            }
            if (studentId == null) {
                NavigationPlaceholderScreen(
                    title = "Selecciona un perfil",
                    description = "El progreso se guarda por estudiante. Entra al acceso adulto y elige un perfil antes de comenzar.",
                    primaryLabel = "Elegir perfil",
                    onPrimary = { navController.goTo(Route.AdultHome) },
                    onBack = navController::navigateUp
                )
            } else if (diagnosticMode.value == null) {
                NavigationPlaceholderScreen(title = "Preparando tu sesión", description = "Estamos preparando tus ejercicios…")
            } else PracticeScreen(
                onComplete = { outcomes ->
                    completedSessionResults.value = outcomes
                    navController.goTo(Route.SessionSummary)
                },
                onActiveBreak = { navController.goTo(Route.ActiveBreak) },
                onExit = { navController.goTo(Route.Home) },
                diagnosticMode = diagnosticMode.value == true,
                schoolLevel = schoolLevel.value,
                onDiagnosticComplete = { _, report ->
                    completedDiagnosticReport.value = report
                    navController.goTo(Route.DiagnosticSummary)
                }
            )
        }
        composable(Route.PracticeFeedback) {
            FeedbackScreen(
                onRetry = { navController.popBackStack() },
                onExit = { navController.goTo(Route.Home) }
            )
        }
        composable(Route.ActiveBreak) {
            ActiveBreakScreen(onContinue = { navController.popBackStack() })
        }
        composable(Route.SessionSummary) {
            SessionSummaryScreen(completedSessionResults.value) { navController.goTo(Route.Home) }
        }
        composable(Route.Progress) {
            val studentId = authRepository.activeStudentIdOrNull()
            if (studentId == null) {
                NavigationPlaceholderScreen(
                    title = "Selecciona un perfil",
                    description = "Primero elige el perfil del estudiante para consultar su avance.",
                    primaryLabel = "Elegir perfil",
                    onPrimary = { navController.goTo(Route.AdultHome) },
                    onBack = navController::navigateUp
                )
            } else StudentProgressScreen(
                getStudentProgress = application.container.getStudentProgress,
                studentId = studentId,
                onBack = navController::navigateUp
            )
        }
        composable(Route.ChildSettings) {
            NavigationPlaceholderScreen(
                title = "Configuración",
                description = "Aquí estarán las preferencias de sonido y apoyo visual.",
                onBack = navController::navigateUp
            )
        }
        composable(Route.AdultAuth) {
            LoginScreen(
                onAuthenticated = { area ->
                    when (area) {
                        UserArea.ADMINISTRATION -> navController.goTo(Route.Administration)
                        UserArea.TEACHING -> navController.goTo(Route.Teaching)
                        UserArea.LEARNING -> navController.goTo(Route.AdultHome)
                    }
                },
                onRegister = { navController.goTo(Route.Register) }
            )
        }
        composable(Route.AdultHome) {
            StudentProfilesScreen(
                repository = application.container.studentProfilesRepository,
                onSelect = { profile ->
                    authRepository.saveActiveStudentId(profile.id)
                    authRepository.saveDisplayName(profile.name)
                    navController.goTo(Route.Home)
                },
                onBack = signOut,
                backLabel = "Cerrar cuenta"
            )
        }
        composable(Route.AdultProfiles) {
            StudentProfilesScreen(
                repository = application.container.studentProfilesRepository,
                onSelect = { profile ->
                    authRepository.saveActiveStudentId(profile.id)
                    authRepository.saveDisplayName(profile.name)
                    navController.goTo(Route.Home)
                },
                onBack = navController::navigateUp
            )
        }
        composable(Route.AdultProgress) {
            NavigationPlaceholderScreen(
                title = "Progreso detallado",
                description = "Aquí se mostrará el avance detallado para el adulto.",
                onBack = navController::navigateUp
            )
        }
        composable(Route.AdultLearningSettings) {
            NavigationPlaceholderScreen(
                title = "Ajustes de aprendizaje",
                description = "Aquí estarán los ajustes pedagógicos del perfil.",
                onBack = navController::navigateUp
            )
        }
    }
}

private fun NavHostController.goTo(route: String) {
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.goToAuthenticatedDestination(area: UserArea) {
    val route = area.authenticatedRoute()
    navigate(route) {
        popUpTo(Route.Login) { inclusive = true }
        launchSingleTop = true
    }
}

private fun UserArea.authenticatedRoute(): String = when (this) {
    UserArea.ADMINISTRATION -> Route.Administration
    UserArea.TEACHING -> Route.Teaching
    UserArea.LEARNING -> Route.AdultHome
}

private fun NavHostController.goToLogin() {
    navigate(Route.Login) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
