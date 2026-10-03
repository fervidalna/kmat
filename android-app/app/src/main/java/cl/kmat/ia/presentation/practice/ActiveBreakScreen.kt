package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import cl.kmat.ia.presentation.design.KMatJumpingMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton

@Composable
fun ActiveBreakScreen(onContinue: () -> Unit) {
    BoxWithConstraints {
        val isTablet = maxWidth >= 840.dp && maxHeight >= 520.dp
        KMatBackground {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 64.dp else 24.dp, vertical = if (isTablet) 34.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "¡Hagamos una pequeña pausa!",
                    color = KMatInk,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isTablet) 43.sp else 29.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Levántate y salta 3 veces",
                    color = KMatInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTablet) 28.sp else 21.sp,
                    textAlign = TextAlign.Center
                )
                KMatJumpingMascot(Modifier.size(if (isTablet) 250.dp else 170.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(if (isTablet) 28.dp else 16.dp)) {
                    repeat(3) { index -> BreakCounter(index + 1) }
                }
                Spacer(Modifier.size(if (isTablet) 8.dp else 2.dp))
                Text("¡Muy bien! Ahora continuemos.", color = KMatInk, fontSize = if (isTablet) 28.sp else 21.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                KMatPrimaryButton(
                    "Continuar  ›",
                    onContinue,
                    Modifier.widthIn(max = 440.dp).fillMaxWidth(if (isTablet) 0.54f else 1f)
                )
            }
        }
    }
}

@Composable
private fun BreakCounter(number: Int) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(Color(0xFFEAF9FF))
            .border(2.dp, Color(0xFFB6E4FC), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(number.toString(), color = KMatInk, fontSize = 45.sp, fontWeight = FontWeight.ExtraBold)
    }
}
