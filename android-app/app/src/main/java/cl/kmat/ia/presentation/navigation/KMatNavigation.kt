package cl.kmat.ia.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.kmat.ia.presentation.practice.ActiveBreakScreen
import cl.kmat.ia.presentation.practice.FeedbackScreen
import cl.kmat.ia.presentation.home.HomeScreen
import cl.kmat.ia.presentation.common.NavigationPlaceholderScreen
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import cl.kmat.ia.presentation.practice.PracticeScreen
import cl.kmat.ia.presentation.practice.SessionSummaryScreen
import cl.kmat.ia.presentation.practice.DiagnosticSummaryScreen
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.domain.model.StudentProfile
import cl.kmat.ia.domain.model.StudentProgressSnapshot
import cl.kmat.ia.domain.model.StudentProfilesResult
import cl.kmat.ia.presentation.practice.ExerciseOutcome
import cl.kmat.ia.presentation.auth.LoginScreen
import cl.kmat.ia.presentation.auth.RegistrationScreen
import cl.kmat.ia.presentation.auth.PasswordRecoveryScreen
import cl.kmat.ia.KMatApplication
import cl.kmat.ia.data.recognition.MlKitHandwritingRecognitionRepository
import cl.kmat.ia.presentation.admin.AdministrationScreen
import cl.kmat.ia.presentation.teacher.TeacherWorkspaceScreen
import cl.kmat.ia.presentation.progress.StudentProgressScreen
import cl.kmat.ia.domain.model.UserArea
import cl.kmat.ia.presentation.student.StudentProfilesScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
    const val AdultHome = "adult-home"
    const val AdultProfiles = "adult-profiles"
    const val AdultProgress = "adult-progress"
    const val AdultLearningSettings = "adult-learning-settings"
}

@Composable
fun KMatNavigation(recoveryLink: String? = null, onRecoveryLinkHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as KMatApplication
    val authRepository = application.container.authRepository
    val recoveryCallback = remember(recoveryLink) { passwordRecoveryCallback(recoveryLink) }
    if (recoveryCallback != null) {
        PasswordRecoveryScreen(
            authCode = recoveryCallback.getQueryParameter("code")
                ?: android.net.Uri.parse("https://callback.invalid/?${recoveryCallback.fragment.orEmpty()}").getQueryParameter("code"),
            callbackError = recoveryCallback.getQueryParameter("error_description")
                ?: android.net.Uri.parse("https://callback.invalid/?${recoveryCallback.fragment.orEmpty()}").getQueryParameter("error_description"),
            onUpdatePassword = authRepository::updatePassword,
            onBackToLogin = onRecoveryLinkHandled
        )
        return
    }
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
    val practicePreparation = remember { mutableStateMapOf<String, Pair<Boolean, String>>() }
    val progressSnapshots = remember { mutableStateMapOf<String, StudentProgressSnapshot>() }
    val preparingStudentIds = remember { mutableSetOf<String>() }
    val recognizerPreparationStarted = rememberSaveable { mutableStateOf(false) }
    val isSigningOut = remember { mutableStateOf(false) }
    val signOut = {
        if (!isSigningOut.value) {
            isSigningOut.value = true
            coroutineScope.launch {
                runCatching { authRepository.signOut() }
                navController.goToLogin()
            }
        }
    }
    val prepareProfiles: (List<StudentProfile>) -> Unit = remember(application) {
        { profiles ->
            profiles.forEach { profile ->
                if (practicePreparation[profile.id] == null && preparingStudentIds.add(profile.id)) {
                    coroutineScope.launch {
                        try {
                            val hasDiagnostic = application.container.learningSessionRepository
                                .hasCompletedDiagnostic(profile.id)
                            practicePreparation[profile.id] = hasDiagnostic to profile.educationLevel
                            try {
                                val subjects = application.container.getStudentProgress(profile.id)
                                progressSnapshots[profile.id] = StudentProgressSnapshot(subjects, null)
                                val diagnosticLevel = if (hasDiagnostic) {
                                    application.container.getStudentProgress.getDiagnosticReport(profile.id)?.overallLevel
                                } else null
                                progressSnapshots[profile.id] = StudentProgressSnapshot(subjects, diagnosticLevel)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                android.util.Log.i("KMatProgress", "El progreso se cargará desde el dispositivo al abrirlo", error)
                            }
                        } catch (error: CancellationException) {
                            preparingStudentIds.remove(profile.id)
                            throw error
                        } catch (error: Exception) {
                            android.util.Log.w("KMatPractice", "No se pudo adelantar la preparación del perfil", error)
                        } finally {
                            preparingStudentIds.remove(profile.id)
                        }
                    }
                }
            }
        }
    }
    val prepareRecognizer: () -> Unit = remember(application) {
        {
            if (!recognizerPreparationStarted.value) {
                recognizerPreparationStarted.value = true
                coroutineScope.launch {
                    val recognizer = MlKitHandwritingRecognitionRepository()
                    try {
                        recognizer.prepare()
                    } catch (error: CancellationException) {
                        recognizerPreparationStarted.value = false
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.i("KMatPractice", "El reconocimiento se preparará al iniciar una práctica", error)
                        recognizerPreparationStarted.value = false
                    } finally {
                        recognizer.close()
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
        composable(Route.Login) {
            if (isSigningOut.value) SideEffect { isSigningOut.value = false }
            LoginScreen(
                onAuthenticated = navController::goToAuthenticatedDestination,
                onRegister = { navController.goTo(Route.Register) }
            )
        }
        composable(Route.Register) {
            RegistrationScreen(
                onAuthenticated = { signOut() },
                onBack = navController::navigateUp
            )
        }
        composable(Route.ProfileSelection) {
            StudentProfilesScreen(
                repository = application.container.studentProfilesRepository,
                onSelect = { profile ->
                    authRepository.saveActiveStudentId(profile.id)
                    authRepository.saveDisplayName(profile.name)
                    cl.kmat.ia.data.sync.LearningSyncScheduler.enqueueWhenConnected(application)
                    navController.goTo(Route.Home)
                },
                onBack = navController::navigateUp,
                onAuthenticationRequired = signOut,
                onProfilesLoaded = prepareProfiles,
                onPreparePractice = prepareRecognizer
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
            val hasAuthenticatedSession = authRepository.hasStoredSession()
            val studentId = authRepository.activeStudentIdOrNull()
            val diagnosticMode = rememberSaveable(studentId) { mutableStateOf<Boolean?>(null) }
            val schoolLevel = rememberSaveable(studentId) { mutableStateOf("BASICO_1") }
            val preparationError = rememberSaveable(studentId) { mutableStateOf(false) }
            val preparationAttempt = rememberSaveable(studentId) { mutableStateOf(0) }
            LaunchedEffect(studentId, hasAuthenticatedSession, preparationAttempt.value) {
                if (!hasAuthenticatedSession) {
                    navController.goToLogin()
                    return@LaunchedEffect
                }
                if (studentId != null && (diagnosticMode.value == null || preparationError.value)) {
                    preparationError.value = false
                    try {
                        val prepared = practicePreparation[studentId]
                        val alreadyDiagnosed = prepared?.first
                            ?: application.container.learningSessionRepository.hasCompletedDiagnostic(studentId)
                        if (!alreadyDiagnosed) {
                            val cachedProfiles = application.container.studentProfilesRepository.cachedMine()
                            schoolLevel.value = prepared?.second
                                ?: cachedProfiles?.firstOrNull { it.id == studentId }?.educationLevel
                                ?: when (val result = application.container.studentProfilesRepository.listMine()) {
                                    is StudentProfilesResult.Success -> result.profiles.firstOrNull { it.id == studentId }?.educationLevel ?: "BASICO_1"
                                    else -> "BASICO_1"
                                }
                        }
                        diagnosticMode.value = !alreadyDiagnosed
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.e("KMatPractice", "No se pudo consultar el diagnóstico guardado", error)
                        preparationError.value = true
                    }
                }
            }
            if (!hasAuthenticatedSession) {
                NavigationPlaceholderScreen(
                    title = "Volviendo al inicio de sesión",
                    description = "Para continuar, inicia sesión nuevamente."
                )
            } else if (studentId == null) {
                NavigationPlaceholderScreen(
                    title = "Selecciona un perfil",
                    description = "El progreso se guarda por estudiante. Entra al acceso adulto y elige un perfil antes de comenzar.",
                    primaryLabel = "Elegir perfil",
                    onPrimary = { navController.goTo(Route.AdultHome) },
                    onBack = navController::navigateUp
                )
            } else if (preparationError.value) {
                NavigationPlaceholderScreen(
                    title = "No pudimos consultar tu progreso",
                    description = "Reintenta para continuar con tus ejercicios.",
                    primaryLabel = "Reintentar",
                    onPrimary = { preparationAttempt.value++ },
                    onBack = navController::navigateUp
                )
            } else if (diagnosticMode.value == null) {
                NavigationPlaceholderScreen(title = "Preparando tu sesión", description = "Estamos preparando tus ejercicios…")
            } else PracticeScreen(
                onComplete = { outcomes ->
                    completedSessionResults.value = outcomes
                    navController.goToSessionResult(Route.SessionSummary)
                },
                onActiveBreak = { navController.goTo(Route.ActiveBreak) },
                onExit = { navController.goTo(Route.Home) },
                diagnosticMode = diagnosticMode.value == true,
                schoolLevel = schoolLevel.value,
                onDiagnosticComplete = { _, report ->
                    studentId?.let { practicePreparation[it] = true to schoolLevel.value }
                    completedDiagnosticReport.value = report
                    navController.goToSessionResult(Route.DiagnosticSummary)
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
            ActiveBreakScreen(
                onContinue = { navController.popBackStack() },
                voiceClient = application.container.geminiPauseVoiceClient
            )
        }
        composable(Route.SessionSummary) {
            SessionSummaryScreen(
                outcomes = completedSessionResults.value,
                onHome = { navController.goTo(Route.Home) },
                onContinue = { navController.goTo(Route.Practice) }
            )
        }
        composable(Route.Progress) {
            val studentId = authRepository.activeStudentIdOrNull()
            val progressProfile = remember(studentId) { mutableStateOf<StudentProfile?>(null) }
            LaunchedEffect(studentId) {
                if (studentId != null) {
                    val cachedProfiles = application.container.studentProfilesRepository.cachedMine()
                    progressProfile.value = cachedProfiles?.firstOrNull { it.id == studentId }
                    when (val result = application.container.studentProfilesRepository.listMine()) {
                        is StudentProfilesResult.Success -> {
                            result.profiles.firstOrNull { it.id == studentId }?.let { progressProfile.value = it }
                        }
                        else -> Unit
                    }
                }
            }
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
                studentName = progressProfile.value?.name ?: "Estudiante",
                selectedGrade = progressProfile.value?.educationLevel ?: "No disponible",
                onBack = navController::navigateUp,
                syncProgress = { id -> application.container.syncCoordinator.syncPending(id) == cl.kmat.ia.data.sync.RemoteSyncResult.Success },
                initialSnapshot = progressSnapshots[studentId],
                onSnapshotUpdated = { snapshot -> progressSnapshots[studentId] = snapshot }
            )
        }
        composable(Route.ChildSettings) {
            NavigationPlaceholderScreen(
                title = "Configuración",
                description = "Aquí estarán las preferencias de sonido y apoyo visual.",
                onBack = navController::navigateUp
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
                onAuthenticationRequired = signOut,
                backLabel = "Cerrar cuenta",
                onProfilesLoaded = prepareProfiles,
                onPreparePractice = prepareRecognizer
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
                onBack = navController::navigateUp,
                onAuthenticationRequired = signOut,
                onProfilesLoaded = prepareProfiles,
                onPreparePractice = prepareRecognizer
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
        if (isSigningOut.value) SignOutLoadingScreen()
    }
}

@Composable
private fun SignOutLoadingScreen() {
    KMatBackground {
        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(28.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = .96f)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KMatLogo()
            CircularProgressIndicator(color = KMatGreen)
            Text("Cerrando sesión", color = KMatInk, fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun passwordRecoveryCallback(link: String?): android.net.Uri? {
    val callback = link?.let { android.net.Uri.parse(it) } ?: return null
    if (callback.scheme != "kmat" || callback.host != "auth" || callback.path != "/reset-password") return null
    return callback
}

private fun NavHostController.goTo(route: String) {
    navigate(route) {
        if (route == Route.Home) popUpTo(Route.Home) { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavHostController.goToSessionResult(route: String) {
    navigate(route) {
        popUpTo(Route.Practice) { inclusive = true }
        launchSingleTop = true
    }
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
