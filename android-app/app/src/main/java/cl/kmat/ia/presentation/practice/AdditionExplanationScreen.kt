package cl.kmat.ia.presentation.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.kmat.ia.R
import cl.kmat.ia.presentation.design.KMatBackground
import cl.kmat.ia.presentation.design.KMatGreen
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
internal fun AdditionExplanationScreen(useAlternativeExample: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val voice = remember(context) { ExplanationVoice(context) }
    val firstCount = if (useAlternativeExample) 1 else 2
    val secondCount = if (useAlternativeExample) 2 else 3
    var stage by remember { mutableIntStateOf(0) }
    var activeStar by remember { mutableIntStateOf(-1) }
    var runNumber by remember { mutableIntStateOf(0) }
    var sequenceActive by remember { mutableStateOf(true) }
    var caption by remember { mutableStateOf("¡Escucha al perrito!") }
    BackHandler(onBack = onBack)

    LaunchedEffect(runNumber, sequenceActive) {
        if (!sequenceActive) return@LaunchedEffect
        stage = 0
        activeStar = -1
        caption = "Sumar es juntar cosas."
        voice.play(R.raw.explicacion_sumar_intro)
        stage = 1
        caption = if (useAlternativeExample) "Primero tenemos una estrella." else "Primero tenemos dos estrellas."
        voice.play(if (useAlternativeExample) R.raw.sumar_first_one else R.raw.sumar_first_two)
        delay(400)
        stage = 2
        caption = if (useAlternativeExample) "Agregamos dos estrellas más." else "Agregamos tres estrellas más."
        voice.play(if (useAlternativeExample) R.raw.sumar_add_two else R.raw.sumar_add_three)
        delay(350)
        stage = 3
        caption = "Juntemos las estrellas."
        delay(700)
        caption = "¡Contemos!"
        voice.play(R.raw.sumar_say_count)
        val numberAudio = listOf(R.raw.sumar_count_one, R.raw.sumar_count_two, R.raw.sumar_count_three, R.raw.sumar_count_four, R.raw.sumar_count_five)
        val numberText = listOf("Uno", "Dos", "Tres", "Cuatro", "Cinco")
        repeat(firstCount + secondCount) { index ->
            activeStar = index
            caption = numberText[index]
            voice.play(numberAudio[index])
            activeStar = -1
            delay(220)
        }
        stage = 4
        caption = if (useAlternativeExample) "¡Uno más dos es igual a tres!" else "¡Dos más tres es igual a cinco!"
        voice.play(if (useAlternativeExample) R.raw.sumar_result_three else R.raw.sumar_result_five)
    }
    DisposableEffect(voice, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                sequenceActive = false
                voice.release()
                activeStar = -1
                stage = 4
                caption = if (useAlternativeExample) "¡Uno más dos es igual a tres!" else "¡Dos más tres es igual a cinco!"
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); voice.release() }
    }

    KMatBackground {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag("addition-explanation")) {
            val landscape = maxWidth >= 480.dp && maxWidth > maxHeight
            val mascotSize = if (landscape) 360.dp else minOf(300.dp, maxHeight * 0.35f)
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    KMatLogo(compact = true)
                    OutlinedButton(onClick = onBack) { Text("Volver al ejercicio", color = KMatInk, fontWeight = FontWeight.Bold) }
                }
                Text("Aprendamos a sumar", modifier = Modifier.fillMaxWidth(), color = KMatInk,
                    fontSize = if (landscape) 22.sp else 25.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (landscape) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(0.35f), contentAlignment = Alignment.Center) {
                            TalkingPracticePuppy(voice.isPlaying, Modifier.size(mascotSize))
                        }
                        ExplanationPanel(stage, activeStar, firstCount, secondCount, caption, useAlternativeExample, Modifier.weight(0.65f))
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TalkingPracticePuppy(voice.isPlaying, Modifier.size(mascotSize))
                    }
                    ExplanationPanel(stage, activeStar, firstCount, secondCount, caption, useAlternativeExample, Modifier.fillMaxWidth().weight(1f))
                }
                if (stage >= 4) {
                    Button(onClick = { sequenceActive = true; runNumber++ }, modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(containerColor = KMatGreen, contentColor = Color.White)) {
                        Text("Ver explicación otra vez", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplanationPanel(stage: Int, activeStar: Int, firstCount: Int, secondCount: Int,
    caption: String, alternative: Boolean, modifier: Modifier) {
    val groupGap by animateDpAsState(if (stage >= 3) 8.dp else 36.dp, tween(600), label = "juntar-estrellas")
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.95f))
        .padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(caption, color = KMatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.testTag("explanation-caption"))
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().testTag("explanation-stars")) {
            if (stage >= 1) {
                repeat(firstCount) { index -> ExplanationStar(index == activeStar, index) }
            }
            if (stage >= 2) {
                Spacer(Modifier.width(groupGap))
                repeat(secondCount) { index -> ExplanationStar(firstCount + index == activeStar, firstCount + index) }
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedVisibility(stage >= 4) {
            Text(if (alternative) "1 + 2 = 3" else "2 + 3 = 5", color = KMatGreen,
                fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ExplanationStar(active: Boolean, index: Int) {
    val lift by animateDpAsState(if (active) (-16).dp else 0.dp, tween(180), label = "estrella-$index")
    Text("⭐", fontSize = 32.sp, modifier = Modifier.offset(y = lift).testTag("explanation-star-$index"))
}

internal class ExplanationVoice(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var onFinished: (() -> Unit)? = null
    var isPlaying by mutableStateOf(false)
        private set

    suspend fun play(resource: Int) = suspendCancellableCoroutine<Unit> { continuation ->
        release()
        onFinished = { if (continuation.isActive) continuation.resume(Unit) }
        continuation.invokeOnCancellation { release() }
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { if (it < 0) finish() }.build()
            focus = request
            if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                finish(); return@suspendCancellableCoroutine
            }
            val recording = MediaPlayer.create(appContext, resource, attributes, 0)
            if (recording == null) { finish(); return@suspendCancellableCoroutine }
            player = recording
            recording.setOnCompletionListener { finish() }
            recording.setOnErrorListener { _, _, _ -> finish(); true }
            recording.start()
            isPlaying = true
        } catch (_: Exception) { finish() }
    }

    private fun finish() {
        val callback = onFinished
        release()
        callback?.invoke()
    }

    fun release() {
        onFinished = null
        isPlaying = false
        player?.release()
        player = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }
}
