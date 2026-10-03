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
import androidx.compose.foundation.layout.width
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
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import cl.kmat.ia.presentation.design.KMatRed
import cl.kmat.ia.presentation.design.KMatSecondaryButton

@Composable
fun FeedbackScreen(onRetry: () -> Unit, onExit: () -> Unit) {
    BoxWithConstraints {
        val isTablet = maxWidth >= 840.dp && maxHeight >= 520.dp
        val padding = if (isTablet) 52.dp else 20.dp
        val cardWidth = ((maxWidth - padding * 2 - 16.dp) / 2).coerceIn(140.dp, 360.dp)
        val singleCardWidth = (maxWidth - padding * 2).coerceAtMost(440.dp)

        KMatBackground {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = if (isTablet) 44.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    FeedbackPill()
                    KMatSecondaryButton("×  Salir", onExit)
                }
                Text(
                    "Revisa tu trabajo e inténtalo nuevamente.",
                    color = KMatInk,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isTablet) 28.sp else 21.sp,
                    textAlign = TextAlign.Center
                )
                if (isTablet) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AnswerCard("Mi trabajo", "1 + 1 = 1", cardWidth)
                        AnswerCard("Mi respuesta", "1", cardWidth)
                    }
                } else {
                    AnswerCard("Mi trabajo", "1 + 1 = 1", singleCardWidth)
                    AnswerCard("Mi respuesta", "1", singleCardWidth)
                }
                Text(
                    "Piensa despacio, puedes revisar tu estrategia.",
                    color = KMatInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTablet) 21.sp else 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFFDDEC))
                        .padding(18.dp)
                )
                KMatPrimaryButton(
                    "↻  Intentar nuevamente",
                    onRetry,
                    Modifier.widthIn(max = 440.dp).fillMaxWidth(if (isTablet) 0.6f else 1f)
                )
                if (isTablet) KMatMascot(Modifier.size(110.dp))
            }
        }
    }
}

@Composable
private fun FeedbackPill() {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xFFFFD7DE)).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(KMatRed),
            contentAlignment = Alignment.Center
        ) { Text("!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp) }
        Text("Casi", color = KMatRed, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
    }
}

@Composable
private fun AnswerCard(title: String, answer: String, width: androidx.compose.ui.unit.Dp) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .border(2.dp, Color(0xFFBCE7FA), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, color = KMatInk, fontWeight = FontWeight.Bold, fontSize = 21.sp)
        Text(answer, color = KMatInk, fontSize = 36.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
