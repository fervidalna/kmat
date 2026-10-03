package cl.kmat.ia.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton
import cl.kmat.ia.presentation.navigation.Route

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, onSignOut: () -> Unit) {
    BoxWithConstraints {
        val isTablet = maxWidth >= 840.dp && maxHeight >= 520.dp
        val screenWidth = maxWidth
        KMatBackground {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 54.dp else 22.dp, vertical = if (isTablet) 24.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeTopBar(
                    isTablet,
                    onSettings = { onNavigate(Route.ChildSettings) },
                    onSignOut = onSignOut
                )
                if (isTablet) {
                    TabletHomeContent(screenWidth, onNavigate)
                } else {
                    PhoneHomeContent(onNavigate)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(isTablet: Boolean, onSettings: () -> Unit, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KMatLogo(compact = !isTablet)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSignOut) {
                Text(
                    "Cerrar sesión",
                    color = KMatInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTablet) 17.sp else 14.sp
                )
            }
            KMatSecondaryButton("⚙", onSettings, Modifier.size(if (isTablet) 72.dp else 58.dp))
        }
    }
}

@Composable
private fun TabletHomeContent(maxWidth: androidx.compose.ui.unit.Dp, onNavigate: (String) -> Unit) {
    val mascotWidth = (maxWidth * 0.31f).coerceIn(220.dp, 360.dp)
    val contentWidth = (maxWidth * 0.44f).coerceIn(360.dp, 540.dp)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        KMatMascot(Modifier.width(mascotWidth).height(390.dp))
        Column(
            modifier = Modifier.width(contentWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WelcomeText(isTablet = true)
            KMatPrimaryButton("▶  Continuar aprendiendo", { onNavigate(Route.Practice) }, Modifier.fillMaxWidth())
            KMatSecondaryButton("⭐  Mi progreso", { onNavigate(Route.Progress) }, Modifier.fillMaxWidth(0.82f))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        KMatSecondaryButton("👥  Cambiar perfil", { onNavigate(Route.ProfileSelection) }, Modifier.width(270.dp))
        KMatSecondaryButton("⚙  Acceso adulto", { onNavigate(Route.AdultAuth) }, Modifier.width(270.dp))
    }
}

@Composable
private fun PhoneHomeContent(onNavigate: (String) -> Unit) {
    Spacer(Modifier.height(12.dp))
    KMatMascot(Modifier.size(145.dp))
    Spacer(Modifier.height(6.dp))
    WelcomeText(isTablet = false)
    Spacer(Modifier.height(16.dp))
    KMatPrimaryButton("▶  Continuar aprendiendo", { onNavigate(Route.Practice) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    KMatSecondaryButton("⭐  Mi progreso", { onNavigate(Route.Progress) }, Modifier.fillMaxWidth(0.9f))
    Spacer(Modifier.height(18.dp))
    KMatSecondaryButton("👥  Cambiar perfil", { onNavigate(Route.ProfileSelection) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    KMatSecondaryButton("⚙  Acceso adulto", { onNavigate(Route.AdultAuth) }, Modifier.fillMaxWidth())
}

@Composable
private fun WelcomeText(isTablet: Boolean) {
    Text(
        "¡Hola, Tomás!",
        color = KMatInk,
        fontWeight = FontWeight.ExtraBold,
        fontSize = if (isTablet) 48.sp else 32.sp,
        textAlign = TextAlign.Center
    )
    Text(
        "¡Listo para seguir aprendiendo!",
        color = KMatInk,
        fontWeight = FontWeight.ExtraBold,
        fontSize = if (isTablet) 27.sp else 19.sp,
        textAlign = TextAlign.Center
    )
}
