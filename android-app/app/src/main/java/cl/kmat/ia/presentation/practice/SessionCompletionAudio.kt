package cl.kmat.ia.presentation.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.kmat.ia.R

/** A bundled recording: no TTS service, download, or network call at playback time. */
@Composable
internal fun SessionCompletionAudio() {
    var hasPlayed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        var player: MediaPlayer? = null
        var focus: AudioFocusRequest? = null

        fun release() {
            player?.release()
            player = null
            focus?.let { audioManager.abandonAudioFocusRequest(it) }
            focus = null
        }

        fun playOnce() {
            if (hasPlayed) return
            // Save before playback so rotations and returning from the background do not repeat it.
            hasPlayed = true
            try {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { change ->
                        if (change < 0) release()
                    }
                    .build()
                focus = request
                if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    release()
                    return
                }
                player = MediaPlayer.create(context, R.raw.session_felicitaciones, attributes, 0)
                val recording = player
                if (recording == null) {
                    release()
                    return
                }
                recording.setOnCompletionListener { release() }
                recording.setOnErrorListener { _, _, _ -> release(); true }
                recording.start()
            } catch (error: Exception) {
                release()
                Log.w("KMatSummary", "No se pudo reproducir la felicitación", error)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> playOnce()
                Lifecycle.Event.ON_PAUSE -> release()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) playOnce()
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }
}
