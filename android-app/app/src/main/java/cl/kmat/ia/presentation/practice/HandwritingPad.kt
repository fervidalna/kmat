package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.usecase.NumberTraceEvaluator
import cl.kmat.ia.presentation.design.KMatInk
import cl.kmat.ia.presentation.design.KMatOutline

@Composable
fun HandwritingPad(
    strokes: List<HandwritingStroke>,
    onStrokeStart: (Float, Float) -> Unit,
    onStrokePoint: (Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    onWritingAreaChanged: (Float, Float) -> Unit,
    traceNumber: Int? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(3.dp, KMatOutline, shape)
            .onSizeChanged { onWritingAreaChanged(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onStrokeStart(down.position.x, down.position.y)
                    var lastPosition = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.position != lastPosition) {
                            onStrokePoint(change.position.x, change.position.y)
                            lastPosition = change.position
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                    onStrokeEnd()
                }
            }
    ) {
        val penWidth = if (traceNumber != null) 10.dp.toPx() else 8f
        traceNumber?.let { number ->
            val traceResult = NumberTraceEvaluator.evaluate(number, strokes, size.width, size.height)
            val coveredIndexes = traceResult.coveredCheckpointIndexes
            val skippedIndexes = traceResult.skippedCheckpointIndexes
            NumberTraceEvaluator.checkpoints(number).forEachIndexed { index, checkpoint ->
                val position = NumberTraceEvaluator.canvasPoint(checkpoint, size.width, size.height)
                val center = androidx.compose.ui.geometry.Offset(position.x, position.y)
                val touchedInOrder = index in coveredIndexes
                drawCircle(
                    color = when {
                        touchedInOrder -> Color(0xFF12A85A)
                        index in skippedIndexes -> Color(0xFFE89537)
                        index == 0 -> Color(0xFFFFB52E)
                        else -> Color(0xFF70BDEB)
                    },
                    radius = if (touchedInOrder || index == 0) 7.dp.toPx() else 5.dp.toPx(),
                    center = center
                )
            }
        }
        strokes.forEach { stroke ->
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                drawCircle(
                    KMatInk,
                    radius = if (traceNumber != null) penWidth / 2f else 5f,
                    center = androidx.compose.ui.geometry.Offset(point.x, point.y)
                )
            } else if (stroke.points.size > 1) {
                val path = Path().apply {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path, color = KMatInk,
                    style = Stroke(width = penWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
