package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import cl.kmat.ia.domain.model.DiagnosticReport
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton

@Composable
fun DiagnosticSummaryScreen(report: DiagnosticReport, onHome: () -> Unit) {
    val levelLabel = when (report.overallLevel) {
        3 -> "Avanzado"
        2 -> "En desarrollo"
        else -> "Nivel inicial"
    }
    KMatBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val phoneLandscape = maxWidth < 840.dp && maxWidth > maxHeight
            if (phoneLandscape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        Modifier.weight(0.9f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("¡Diagnóstico terminado!", color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .94f)).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            KMatMascot(Modifier.fillMaxWidth(.28f))
                            Text("Tu nivel de partida", color = KMatInk, fontSize = 14.sp)
                            Text(levelLabel, color = KMatInk, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Respondidos ${report.totalQuestions} ejercicios. Este resultado ayuda a elegir por dónde comenzar.", color = KMatInk.copy(alpha = .76f), textAlign = TextAlign.Center, fontSize = 12.sp)
                        }
                        KMatPrimaryButton("Volver al inicio", onHome, Modifier.fillMaxWidth())
                    }
                    Column(
                        Modifier.weight(1.1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("Resultado por eje", color = KMatInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        report.axes.forEach { axis ->
                            val background = when (axis.level) {
                                3 -> Color(0xFFE5F6E9)
                                2 -> Color(0xFFFFF3D9)
                                else -> Color(0xFFFFE9E6)
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(background).padding(9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(axis.label, color = KMatInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(axis.status, color = KMatInk.copy(alpha = .8f), fontSize = 11.sp)
                                    Text("Dentro del tiempo: ${axis.withinExpectedTime}/${axis.total}", color = KMatInk.copy(alpha = .75f), fontSize = 10.sp)
                                }
                                Text("${axis.correct}/${axis.total}", color = KMatInk, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text("Las próximas sesiones priorizarán los ejes que necesiten más refuerzo. El tiempo es una referencia y no determina por sí solo el resultado.", color = KMatInk, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            } else Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("¡Diagnóstico terminado!", color = KMatInk, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = .94f)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KMatMascot(Modifier.padding(4.dp).fillMaxWidth(.32f).padding(vertical = 4.dp))
                Text("Tu nivel de partida", color = KMatInk, fontSize = 17.sp)
                Text(levelLabel, color = KMatInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Respondidos ${report.totalQuestions} ejercicios. Este resultado ayuda a elegir por dónde comenzar.", color = KMatInk.copy(alpha = .76f), textAlign = TextAlign.Center)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .92f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Resultado por eje", color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                report.axes.forEach { axis ->
                    val background = when (axis.level) {
                        3 -> Color(0xFFE5F6E9)
                        2 -> Color(0xFFFFF3D9)
                        else -> Color(0xFFFFE9E6)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(axis.label, color = KMatInk, fontWeight = FontWeight.Bold)
                            Text(axis.status, color = KMatInk.copy(alpha = .8f), fontSize = 13.sp)
                            Text("Dentro del tiempo de referencia: ${axis.withinExpectedTime}/${axis.total}", color = KMatInk.copy(alpha = .75f), fontSize = 12.sp)
                        }
                        Text("${axis.correct}/${axis.total}", color = KMatInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Text("Las próximas sesiones priorizarán los ejes que necesiten más refuerzo y ajustarán la dificultad según tus respuestas. El tiempo es una referencia y no determina por sí solo el resultado.", color = KMatInk, fontSize = 14.sp, textAlign = TextAlign.Center)
            KMatPrimaryButton("Volver al inicio", onHome, Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        }
    }
}
