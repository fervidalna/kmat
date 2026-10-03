package cl.kmat.ia.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatSecondaryButton

@Composable
fun NavigationPlaceholderScreen(
    title: String,
    description: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    KMatBackground {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(28.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.9f))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = KMatInk, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, textAlign = TextAlign.Center)
            Text(description, color = KMatInk, fontSize = 18.sp, textAlign = TextAlign.Center)

            if (primaryLabel != null && onPrimary != null) {
                KMatPrimaryButton(primaryLabel, onPrimary, Modifier.fillMaxWidth())
            }
            if (secondaryLabel != null && onSecondary != null) {
                KMatSecondaryButton(secondaryLabel, onSecondary, Modifier.fillMaxWidth())
            }
            if (tertiaryLabel != null && onTertiary != null) {
                KMatSecondaryButton(tertiaryLabel, onTertiary, Modifier.fillMaxWidth())
            }
            if (onBack != null) {
                KMatSecondaryButton("Volver", onBack, Modifier.fillMaxWidth())
            }
        }
    }
}
