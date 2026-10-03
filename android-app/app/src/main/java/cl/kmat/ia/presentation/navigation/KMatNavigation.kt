package cl.kmat.ia.presentation.navigation

import androidx.compose.runtime.Composable
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
import cl.kmat.ia.presentation.auth.LoginScreen
import cl.kmat.ia.presentation.auth.RegistrationScreen
import cl.kmat.ia.KMatApplication
import cl.kmat.ia.presentation.admin.AdministrationScreen
import cl.kmat.ia.presentation.teacher.TeachingScreen
import cl.kmat.ia.domain.model.UserArea
import kotlinx.coroutines.launch

object Route {
    const val Login = "login"
    const val Register = "register"
    const val Administration = "administration"
    const val Teaching = "teaching"
    const val ProfileSelection = "profile-selection"
    const val Home = "home"
    const val Diagnostic = "diagnostic"
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
    val coroutineScope = rememberCoroutineScope()
    val signOut = {
        coroutineScope.launch { authRepository.signOut() }
        navController.goToLogin()
    }

    NavHost(navController = navController, startDestination = Route.Login, modifier = Modifier) {
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
            NavigationPlaceholderScreen(
                title = "¿Quién va a aprender?",
                description = "Selecciona el perfil de Tomás para continuar.",
                primaryLabel = "Usar perfil de Tomás",
                onPrimary = { navController.goTo(Route.Home) }
            )
        }
        composable(Route.Home) {
            HomeScreen(onNavigate = navController::goTo, onSignOut = signOut)
        }
        composable(Route.Administration) {
            AdministrationScreen(onSignOut = signOut)
        }
        composable(Route.Teaching) {
            TeachingScreen(onSignOut = signOut)
        }
        composable(Route.Diagnostic) {
            NavigationPlaceholderScreen(
                title = "Diagnóstico inicial",
                description = "Aquí se mostrará la sesión de diagnóstico.",
                primaryLabel = "Comenzar práctica",
                onPrimary = { navController.goTo(Route.Practice) },
                onBack = navController::navigateUp
            )
        }
        composable(Route.Practice) {
            PracticeScreen(
                onCorrect = { navController.goTo(Route.SessionSummary) },
                onNeedsReview = { navController.goTo(Route.PracticeFeedback) },
                onActiveBreak = { navController.goTo(Route.ActiveBreak) },
                onExit = { navController.goTo(Route.Home) }
            )
        }
        composable(Route.PracticeFeedback) {
            FeedbackScreen(
                onRetry = { navController.goTo(Route.Practice) },
                onExit = { navController.goTo(Route.Home) }
            )
        }
        composable(Route.ActiveBreak) {
            ActiveBreakScreen(onContinue = { navController.goTo(Route.Practice) })
        }
        composable(Route.SessionSummary) {
            NavigationPlaceholderScreen(
                title = "¡Muy bien, Tomás!",
                description = "La sesión terminó. Aquí verás un resumen de tu avance.",
                primaryLabel = "Volver al inicio",
                onPrimary = { navController.goTo(Route.Home) }
            )
        }
        composable(Route.Progress) {
            NavigationPlaceholderScreen(
                title = "Mi progreso",
                description = "Aquí se mostrarán los avances del estudiante.",
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
            NavigationPlaceholderScreen(
                title = "Área adulta",
                description = "Gestiona la información del estudiante.",
                primaryLabel = "Gestionar perfiles",
                onPrimary = { navController.goTo(Route.AdultProfiles) },
                secondaryLabel = "Ver progreso detallado",
                onSecondary = { navController.goTo(Route.AdultProgress) },
                tertiaryLabel = "Ajustes de aprendizaje",
                onTertiary = { navController.goTo(Route.AdultLearningSettings) },
                onBack = { navController.goTo(Route.Home) }
            )
        }
        composable(Route.AdultProfiles) {
            NavigationPlaceholderScreen(
                title = "Gestión de perfiles",
                description = "Aquí se podrán administrar los perfiles de estudiantes.",
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
    val route = when (area) {
        UserArea.ADMINISTRATION -> Route.Administration
        UserArea.TEACHING -> Route.Teaching
        UserArea.LEARNING -> Route.Home
    }
    navigate(route) {
        popUpTo(Route.Login) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.goToLogin() {
    navigate(Route.Login) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
