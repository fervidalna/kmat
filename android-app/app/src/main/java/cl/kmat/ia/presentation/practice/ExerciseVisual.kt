package cl.kmat.ia.presentation.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kmat.ia.presentation.design.KMatInk
import org.json.JSONObject

private data class VisualGroup(val label: String, val symbol: String, val count: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseVisual(spec: String, modifier: Modifier = Modifier) {
    val visual = remember(spec) { runCatching { JSONObject(spec) }.getOrNull() } ?: return
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFFF1F8FF)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (visual.optString("type")) {
            "OBJECTS" -> {
                val groups = visual.optJSONArray("groups") ?: return@Column
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(groups.length()) { index ->
                        val group = groups.getJSONObject(index)
                        ObjectGroup(
                            symbol = group.optString("symbol", "●"),
                            label = group.optString("label"),
                            count = group.optInt("count").coerceIn(1, 20)
                        )
                    }
                }
            }
            "EQUATION_OBJECTS" -> {
                val symbol = visual.optString("symbol", "🍎")
                val left = visual.optInt("left").coerceIn(1, 12)
                val right = visual.optInt("right").coerceIn(1, 12)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    ObjectGroup(symbol, "${visual.optString("label")} · $left", left, Modifier.weight(1f))
                    Text(visual.optString("operation", "+"), Modifier.padding(horizontal = 6.dp), color = KMatInk, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    ObjectGroup(symbol, "${visual.optString("label")} · $right", right, Modifier.weight(1f))
                }
            }
            "SHAPES" -> {
                val groups = visual.optJSONArray("groups") ?: return@Column
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(groups.length()) { index ->
                        val group = groups.getJSONObject(index)
                        val shape = group.optString("shape", "triangle")
                        repeat(group.optInt("count").coerceIn(1, 4)) {
                            ShapeTile(shape)
                        }
                    }
                }
            }
            "MEASURE" -> {
                val groups = visual.optJSONArray("groups") ?: return@Column
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(groups.length()) { index ->
                        val group = groups.getJSONObject(index)
                        MeasureBar(group.optString("label"), group.optInt("count").coerceIn(1, 15))
                    }
                }
            }
            "SEQUENCE" -> {
                val items = visual.optJSONArray("items") ?: return@Column
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(items.length()) { index ->
                        val item = items.optString(index)
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (item == "?") Color(0xFFFFE3A7) else Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item, color = KMatInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObjectGroup(symbol: String, label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .9f)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            repeat(count.coerceAtMost(12)) { Text(symbol, fontSize = 22.sp) }
            if (count > 12) Text("× $count", color = KMatInk, fontWeight = FontWeight.Bold)
        }
        if (label.isNotBlank()) Text(label, color = KMatInk, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShapeTile(shape: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.size(62.dp)) {
            val sides = when (shape) { "triangle" -> 3; "square", "rectangle" -> 4; "pentagon" -> 5; else -> 6 }
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radius = size.minDimension * .39f
            val path = Path()
        if (shape == "rectangle") {
            path.moveTo(centerX - radius, centerY - radius * .62f)
            path.lineTo(centerX + radius, centerY - radius * .62f)
            path.lineTo(centerX + radius, centerY + radius * .62f)
            path.lineTo(centerX - radius, centerY + radius * .62f)
            path.close()
            drawPath(path, Color(0xFFFFD56A))
            drawPath(path, KMatInk, style = Stroke(width = 3.dp.toPx()))
            return@Canvas
        }
            repeat(sides) { index ->
                val angle = Math.PI * 2.0 * index / sides - Math.PI / 2.0
                val point = Offset(
                    centerX + (radius * kotlin.math.cos(angle)).toFloat(),
                    centerY + (radius * kotlin.math.sin(angle)).toFloat()
                )
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()
            drawPath(path, Color(0xFFFFD56A))
            drawPath(path, KMatInk, style = Stroke(width = 3.dp.toPx()))
        }
        Text(shapeLabel(shape), color = KMatInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasureBar(label: String, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = KMatInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(count) {
                Box(Modifier.size(width = 18.dp, height = 16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF76C6EE)))
            }
        }
    }
}

private fun shapeLabel(shape: String) = when (shape) {
    "triangle" -> "Triángulo"
    "square" -> "Cuadrado"
    "rectangle" -> "Rectángulo"
    "pentagon" -> "Pentágono"
    else -> "Figura"
}
