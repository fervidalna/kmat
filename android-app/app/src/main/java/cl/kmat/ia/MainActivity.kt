package cl.kmat.ia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cl.kmat.ia.presentation.KMatApp
import cl.kmat.ia.presentation.design.KMatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KMatTheme {
                KMatApp()
            }
        }
    }
}
