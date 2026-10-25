package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.background
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatJumpingMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton

@Composable
fun SessionSummaryScreen(outcomes: List<ExerciseOutcome>, onHome: () -> Unit) {
    val correct = outcomes.count { it.isCorrect }
    val total = outcomes.size
    val percent = if (total == 0) 0 else correct * 100 / total
    val message = when {
        percent == 100 -> "¡Lo hiciste increíble!"
        percent >= 70 -> "¡Vas muy bien!"
        else -> "¡Buen esfuerzo! Sigue practicando"
    }
    val celebration = rememberInfiniteTransition(label = "mascot-celebration")
    val jump by celebration.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                0f at 0
                0f at 250
                1f at 500
                1f at 900
                0f at 1150
                0f at 1500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "jump"
    )
    KMatBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("¡Sesión completada! 🎉", color = KMatInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = .94f)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    if (jump > 0.5f) {
                        KMatJumpingMascot(
                            Modifier.size(112.dp).graphicsLayer {
                                translationY = -18.dp.toPx() * ((jump - 0.5f) * 2f)
                                rotationZ = -4f * ((jump - 0.5f) * 2f)
                            }
                        )
                    } else {
                        KMatMascot(
                            Modifier.size(112.dp).graphicsLayer {
                                translationY = -8.dp.toPx() * (jump * 2f)
                            }.scale(1f + 0.04f * jump)
                        )
                    }
                }
                Text(message, color = KMatInk, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Completaste $total ${if (total == 1) "ejercicio" else "ejercicios"}", color = KMatInk.copy(alpha = .76f), fontSize = 17.sp)
                Box(Modifier.size(112.dp).clip(CircleShape).background(KMatGreen), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$percent%", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("correctas", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryStat("✓", "$correct", "Correctas", Color(0xFFE4F6E9), Modifier.weight(1f))
                    SummaryStat("★", "${(total - correct).coerceAtLeast(0)}", "Por practicar", Color(0xFFFFF2D8), Modifier.weight(1f))
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .9f)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Así te fue", color = KMatInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                outcomes.forEachIndexed { index, outcome ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (outcome.isCorrect) Color(0xFFEAF7ED) else Color(0xFFFFF4E5)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (outcome.isCorrect) "⭐" else "💪", fontSize = 22.sp)
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${outcome.exerciseName}", color = KMatInk, fontWeight = FontWeight.Bold)
                            Text("Tu respuesta: ${outcome.answer} · ${if (outcome.isCorrect) "¡Correcta!" else "Respuesta: ${outcome.expectedAnswer}"}", color = KMatInk.copy(alpha = .78f), fontSize = 13.sp)
                        }
                    }
                }
            }
            KMatPrimaryButton("Volver al inicio", onHome, Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun SummaryStat(icon: String, value: String, label: String, background: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(background).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("$icon  $value", color = KMatInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = KMatInk, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
