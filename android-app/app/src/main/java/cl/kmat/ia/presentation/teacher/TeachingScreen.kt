package cl.kmat.ia.presentation.teacher

import androidx.compose.runtime.Composable
import cl.kmat.ia.presentation.common.NavigationPlaceholderScreen

@Composable
fun TeachingScreen(onSignOut: () -> Unit) {
    NavigationPlaceholderScreen(
        title = "Docencia",
        description = "Sesión docente activa. Aquí se incorporarán las herramientas pedagógicas del MVP.",
        primaryLabel = "Cerrar sesión",
        onPrimary = onSignOut
    )
}
