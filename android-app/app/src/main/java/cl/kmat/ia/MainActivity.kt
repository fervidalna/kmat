package cl.kmat.ia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import cl.kmat.ia.presentation.KMatApp
import cl.kmat.ia.presentation.design.KMatTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val recoveryLink = mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        cl.kmat.ia.data.sync.LearningSyncScheduler.enqueueWhenConnected(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recoveryLink.value = intent?.dataString
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    (application as KMatApplication).container.syncCoordinator.syncPending()
                    delay(30_000)
                }
            }
        }
        setContent {
            KMatTheme {
                KMatApp(
                    recoveryLink = recoveryLink.value,
                    onRecoveryLinkHandled = {
                        recoveryLink.value = null
                        intent?.data = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recoveryLink.value = intent.dataString
    }
}
