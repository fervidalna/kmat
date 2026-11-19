package cl.kmat.ia.presentation.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.kmat.ia.R
import kotlin.random.Random

internal fun nextEncouragementIndex(previous: Int, random: Random = Random.Default): Int {
    if (previous !in 0..2) return random.nextInt(3)
    val choice = random.nextInt(2)
    return if (choice >= previous) choice + 1 else choice
}

/** Plays the packaged encouragement; does not need network access. */
internal class PracticeEncouragementAudio(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("practice_encouragement", Context.MODE_PRIVATE)
    private val recordings = intArrayOf(
        R.raw.practica_animo_gemini,
        R.raw.practica_animo_sigue,
        R.raw.practica_animo_excelente,
    )
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    var isPlaying by mutableStateOf(false)
        private set

    fun play() {
        release()
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change -> if (change < 0) release() }
                .build()
            focus = request
            if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                release()
                return
            }
            val index = nextEncouragementIndex(preferences.getInt("last_played", -1))
            val recording = MediaPlayer.create(appContext, recordings[index], attributes, 0)
            if (recording == null) {
                release()
                return
            }
            player = recording
            recording.setOnCompletionListener { release() }
            recording.setOnErrorListener { _, _, _ -> release(); true }
            recording.start()
            isPlaying = true
            preferences.edit().putInt("last_played", index).apply()
        } catch (error: Exception) {
            release()
            Log.w("KMatPracticeAudio", "No se pudo reproducir el mensaje de ánimo", error)
        }
    }

    fun release() {
        isPlaying = false
        player?.release()
        player = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }
}
