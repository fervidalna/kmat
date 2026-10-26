package cl.kmat.ia.practice

import android.content.Context
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.kmat.ia.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionCompletionAudioTest {
    @Test
    fun bundledGreetingDecodesAndPlaysToCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val finished = CountDownLatch(1)
        val failed = AtomicBoolean(false)
        var player: MediaPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = MediaPlayer.create(context, R.raw.session_felicitaciones)
                assertNotNull("The packaged WAV must be playable on Android", player)
                val recording = requireNotNull(player)
                assertTrue("Greeting must remain brief", recording.duration in 500..5000)
                recording.setOnErrorListener { _, _, _ ->
                    failed.set(true)
                    finished.countDown()
                    true
                }
                recording.setOnCompletionListener { finished.countDown() }
                recording.start()
            }
            assertTrue("Playback must finish", finished.await(10, TimeUnit.SECONDS))
            assertFalse("Audio decoder must not fail", failed.get())
        } finally {
            instrumentation.runOnMainSync { player?.release() }
        }
    }
}
