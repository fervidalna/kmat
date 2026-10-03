package cl.kmat.ia.presentation.admin

import androidx.compose.runtime.Composable
import cl.kmat.ia.presentation.common.NavigationPlaceholderScreen

@Composable
fun AdministrationScreen(onSignOut: () -> Unit) {
    NavigationPlaceholderScreen(
        title = "Administración",
        description = "Sesión de administrador activa. Aquí se incorporarán las herramientas de administración del MVP.",
        primaryLabel = "Cerrar sesión",
        onPrimary = onSignOut
    )
}
