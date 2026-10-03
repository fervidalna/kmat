package cl.kmat.ia

import android.app.Application
import cl.kmat.ia.data.AppContainer

class KMatApplication : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
