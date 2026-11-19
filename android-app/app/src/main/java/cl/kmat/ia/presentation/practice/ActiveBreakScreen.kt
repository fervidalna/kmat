package cl.kmat.ia.presentation.practice

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import cl.kmat.ia.data.gemini.GeminiPauseVoiceClient
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatJumpingMascot
import cl.kmat.ia.presentation.design.KMatPrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class BreakPhase { COUNTDOWN, JUMPING, COMPLETE }

@Composable
fun ActiveBreakScreen(onContinue: () -> Unit, voiceClient: GeminiPauseVoiceClient? = null) {
    var phase by remember { mutableStateOf(BreakPhase.COUNTDOWN) }
    var countdown by remember { mutableIntStateOf(5) }
    var round by remember { mutableIntStateOf(1) }
    var jumpsInRound by remember { mutableIntStateOf(0) }
    var isChangingRound by remember { mutableStateOf(false) }
    var voiceLoading by remember { mutableStateOf(false) }
    var voiceReady by remember { mutableStateOf(false) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    var voicePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            voicePlayer?.release()
            voiceFile?.delete()
        }
    }

    fun playVoiceInstruction() {
        val client = voiceClient ?: return
        if (voiceLoading) return
        scope.launch {
            voiceLoading = true
            voiceMessage = null
            try {
                val audio = client.getPauseInstructionAudio()
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "gemini-pausa-${System.currentTimeMillis()}.wav")
                        .apply { writeBytes(audio.bytes) }
                }
                voicePlayer?.release()
                voiceFile?.delete()
                val player = withContext(Dispatchers.IO) {
                    MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                    }
                }
                voiceFile = file
                player.setOnCompletionListener { completed ->
                    completed.release()
                    if (voicePlayer === completed) voicePlayer = null
                    file.delete()
                    voiceFile = null
                }
                player.setOnErrorListener { failed, _, _ ->
                    failed.release()
                    if (voicePlayer === failed) voicePlayer = null
                    file.delete()
                    voiceFile = null
                    voiceReady = false
                    voiceMessage = "No se pudo reproducir el audio. Inténtalo otra vez."
                    true
                }
                voicePlayer = player
                player.start()
                voiceReady = true
            } catch (error: Exception) {
                voiceMessage = error.message ?: "No se pudo reproducir la voz."
            } finally {
                voiceLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        for (number in 1..5) {
            countdown = number
            delay(1_000)
        }
        phase = BreakPhase.JUMPING
        playVoiceInstruction()
    }

    BoxWithConstraints {
        val isTablet = maxWidth >= 840.dp && maxHeight >= 520.dp
        val compact = maxHeight < 520.dp
        KMatBackground {
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    horizontal = if (isTablet) 64.dp else 24.dp,
                    vertical = if (compact) 10.dp else if (isTablet) 30.dp else 20.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
            ) {
                Text(
                    when (phase) {
                        BreakPhase.COUNTDOWN -> "¡Preparemos el cuerpo!"
                        BreakPhase.JUMPING -> "¡Vamos a saltar!"
                        BreakPhase.COMPLETE -> "¡Lo hiciste genial!"
                    },
                    color = KMatInk, fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isTablet) 40.sp else 28.sp, textAlign = TextAlign.Center
                )

                if (voiceClient != null && voiceReady) {
                    OutlinedButton(onClick = ::playVoiceInstruction) {
                        Text("Volver a escuchar")
                    }
                } else if (voiceLoading) {
                    Text("Preparando la voz…", color = KMatInk, fontSize = 13.sp, textAlign = TextAlign.Center)
                } else if (voiceMessage != null) {
                    Text(voiceMessage.orEmpty(), color = KMatInk, fontSize = 13.sp, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = ::playVoiceInstruction) {
                        Text("Reintentar voz")
                    }
                }

                if (phase == BreakPhase.COUNTDOWN) {
                    Text("Cuenta conmigo…", color = KMatInk, fontSize = 19.sp, textAlign = TextAlign.Center)
                    Box(
                        Modifier.size(if (compact) 112.dp else if (isTablet) 190.dp else 150.dp)
                            .clip(CircleShape).background(Color.White.copy(alpha = .92f))
                            .border(5.dp, Color(0xFF00A951), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(countdown.toString(), color = KMatInk, fontSize = if (isTablet) 88.sp else 68.sp,
                            fontWeight = FontWeight.ExtraBold)
                    }
                    Text("¡Ahora empieza a saltar!", color = KMatInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                } else {
                    Text(
                        when (phase) {
                            BreakPhase.JUMPING -> "RONDA $round DE 3"
                            BreakPhase.COMPLETE -> "Terminamos la pausa. ¡Volvamos a aprender!"
                            else -> ""
                        },
                        color = KMatInk, fontWeight = FontWeight.Bold,
                        fontSize = if (isTablet) 23.sp else 18.sp, textAlign = TextAlign.Center
                    )
                    if (phase == BreakPhase.JUMPING) {
                        Text(
                            "Ahora haremos $round ${if (round == 1) "salto" else "saltos"}",
                            color = KMatInk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isTablet) 26.sp else 21.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Spacer(Modifier.size(if (compact) 112.dp else if (isTablet) 220.dp else 160.dp))
                        KMatJumpingMascot(
                            Modifier.size(if (compact) 104.dp else if (isTablet) 205.dp else 150.dp),
                            isJumping = true,
                            useJumpSequence = true
                        )
                    }
                    if (phase == BreakPhase.JUMPING) {
                        Text(
                            if (jumpsInRound == 0) "Prepárate…" else "¡Salto $jumpsInRound!",
                            color = Color(0xFF00A951),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (isTablet) 42.sp else 34.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Salto ${minOf(jumpsInRound + 1, round)} de $round",
                            color = KMatInk,
                            fontSize = if (isTablet) 19.sp else 16.sp,
                            textAlign = TextAlign.Center
                        )
                        KMatPrimaryButton(
                            "Hice el salto ${minOf(jumpsInRound + 1, round)}",
                            onClick = {
                                if (phase == BreakPhase.JUMPING && !isChangingRound) {
                                    jumpsInRound++
                                    if (jumpsInRound >= round) {
                                        if (round == 3) phase = BreakPhase.COMPLETE
                                        else {
                                            isChangingRound = true
                                            scope.launch {
                                                delay(900)
                                                round++
                                                jumpsInRound = 0
                                                isChangingRound = false
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                            enabled = !isChangingRound
                        )
                    } else {
                        KMatPrimaryButton("Continuar  ›", onContinue, Modifier.widthIn(max = 420.dp).fillMaxWidth())
                    }
                }
            }
        }
    }
}
