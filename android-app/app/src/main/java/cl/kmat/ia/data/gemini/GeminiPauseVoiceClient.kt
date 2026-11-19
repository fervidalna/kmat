package cl.kmat.ia.data.gemini

import android.content.Context
import cl.kmat.ia.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PauseVoiceAudio(val bytes: ByteArray)

class GeminiPauseVoiceClient(context: Context) {
    private val appContext = context.applicationContext

    suspend fun getPauseInstructionAudio(): PauseVoiceAudio = withContext(Dispatchers.IO) {
        val bytes = appContext.resources.openRawResource(R.raw.pausa_saltar).use { it.readBytes() }
        PauseVoiceAudio(bytes)
    }
}
