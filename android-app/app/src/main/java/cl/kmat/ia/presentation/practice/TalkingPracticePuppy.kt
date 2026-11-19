package cl.kmat.ia.presentation.practice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import cl.kmat.ia.R
import kotlin.math.roundToInt

/** Only the mouth changes; the full mascot remains a single stable image. */
@Composable
internal fun TalkingPracticePuppy(speaking: Boolean, modifier: Modifier = Modifier) {
    val closed = ImageBitmap.imageResource(R.drawable.kmat_puppy_speaking_base)
    val open = ImageBitmap.imageResource(R.drawable.kmat_mascot_blue_puppy)
    val mouthOpening = if (speaking) {
        val transition = rememberInfiniteTransition(label = "puppy-talking")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(180), RepeatMode.Reverse),
            label = "puppy-mouth"
        )
        value
    } else 0f

    Canvas(modifier.testTag("practice-puppy")) {
        // Both images have the same full-body pose and share the mouth coordinates.
        val imageWidth = closed.width
        val imageHeight = closed.height
        val scale = minOf(size.width / imageWidth, size.height / imageHeight)
        val width = (imageWidth * scale).roundToInt()
        val height = (imageHeight * scale).roundToInt()
        val left = ((size.width - width) / 2f).roundToInt()
        val top = ((size.height - height) / 2f).roundToInt()
        drawImage(closed, srcOffset = IntOffset.Zero, srcSize = IntSize(imageWidth, imageHeight),
            dstOffset = IntOffset(left, top), dstSize = IntSize(width, height))
        if (mouthOpening > 0f) {
            val mouthX = 525
            val mouthY = 550
            val mouthWidth = 235
            val mouthHeight = 125
            drawImage(open, srcOffset = IntOffset(mouthX, mouthY), srcSize = IntSize(mouthWidth, mouthHeight),
                dstOffset = IntOffset(left + (mouthX * scale).roundToInt(), top + (mouthY * scale).roundToInt()),
                dstSize = IntSize((mouthWidth * scale).roundToInt().coerceAtLeast(1), (mouthHeight * scale).roundToInt().coerceAtLeast(1)),
                alpha = mouthOpening)
        }
    }
}
