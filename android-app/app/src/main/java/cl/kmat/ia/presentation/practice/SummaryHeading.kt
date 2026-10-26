package cl.kmat.ia.presentation.practice

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatJumpingMascot

/** The companion has its own bounded space; its animation never moves the results. */
@Composable
internal fun SummaryHeading(title: String, compact: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = KMatInk,
            fontSize = if (compact) 20.sp else 27.sp,
            fontWeight = FontWeight.ExtraBold
        )
        SummaryCompanion(compact)
    }
}

@Composable
private fun SummaryCompanion(compact: Boolean) {
    val celebration = rememberInfiniteTransition(label = "summary-companion")
    val hop by celebration.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = keyframes {
            durationMillis = 2400
            0f at 0
            1f at 350
            0f at 700
            .65f at 1050
            0f at 1400
            0f at 2400
        }),
        label = "gentle-hops"
    )
    Box(
        Modifier.size(if (compact) 56.dp else 72.dp)
            .clipToBounds()
            .clearAndSetSemantics { }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        KMatJumpingMascot(Modifier.fillMaxSize().graphicsLayer {
            translationY = -7.dp.toPx() * hop
            rotationZ = -5f * hop
        })
    }
}
