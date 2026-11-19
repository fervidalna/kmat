package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import cl.kmat.ia.presentation.design.KMatInk

@Composable
internal fun AutoFitQuestionText(
    text: String,
    modifier: Modifier = Modifier,
    maximumFontSize: TextUnit,
    minimumFontSize: TextUnit,
    onLayout: (fontSize: TextUnit, fits: Boolean) -> Unit = { _, _ -> }
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        var fontSize by remember(text, maxWidth, maxHeight, maximumFontSize, minimumFontSize) {
            mutableStateOf(maximumFontSize)
        }
        var needsScroll by remember(text, maxWidth, maxHeight, maximumFontSize, minimumFontSize) { mutableStateOf(false) }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().then(if (needsScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            color = KMatInk,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = fontSize * 1.15f,
            softWrap = true,
            onTextLayout = { layout ->
                val fits = !layout.didOverflowHeight && !layout.didOverflowWidth
                onLayout(fontSize, fits)
                if (!fits && fontSize > minimumFontSize) {
                    fontSize = (fontSize.value - 2f).coerceAtLeast(minimumFontSize.value).sp
                } else if (!fits) {
                    needsScroll = true
                }
            }
        )
    }
}
