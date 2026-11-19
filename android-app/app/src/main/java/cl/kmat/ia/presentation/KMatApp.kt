package cl.kmat.ia.presentation

import androidx.compose.runtime.Composable
import cl.kmat.ia.presentation.navigation.KMatNavigation

@Composable
fun KMatApp(recoveryLink: String? = null, onRecoveryLinkHandled: () -> Unit = {}) {
    KMatNavigation(recoveryLink, onRecoveryLinkHandled)
}
