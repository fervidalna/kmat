package cl.kmat.ia.presentation.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
private val KMatColors = lightColorScheme(
    primary = KMatGreen,
    secondary = KMatBlue,
    tertiary = KMatYellow,
    background = KMatCream
)

@Composable
fun KMatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KMatColors, content = content)
}
