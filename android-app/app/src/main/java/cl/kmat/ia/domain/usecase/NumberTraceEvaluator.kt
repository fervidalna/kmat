package cl.kmat.ia.domain.usecase

import cl.kmat.ia.domain.model.HandwritingStroke
import kotlin.math.hypot

data class NormalizedTracePoint(val x: Float, val y: Float)
data class NumberTraceResult(
    val coveredPoints: Int,
    val totalPoints: Int,
    val routeAdherence: Float,
    val coveredCheckpointIndexes: Set<Int> = emptySet(),
    val skippedCheckpointIndexes: Set<Int> = emptySet()
) {
    val coverage: Float get() = if (totalPoints == 0) 0f else coveredPoints.toFloat() / totalPoints
}

/** Centerline checkpoints for guided number writing. Coordinates use a 0..1 canvas. */
object NumberTraceEvaluator {
    private const val CHECKPOINT_STEP = 0.035f
    const val HIT_RADIUS = 0.022f
    private const val ADHERENCE_RADIUS = 0.04f
    private const val MAX_SKIPPED_CHECKPOINTS = 2
    private const val RESUME_STREAK = 3
    const val MIN_ROUTE_ADHERENCE = 0.65f

    fun routeSegments(number: Int): List<List<NormalizedTracePoint>> {
        require(number in 1..10)
        val routes = when (number) {
            1 -> listOf(
                listOf(p(.39f,.31f), p(.51f,.20f), p(.51f,.78f)),
                listOf(p(.36f,.79f), p(.66f,.79f))
            )
            2 -> listOf(listOf(p(.29f,.32f), p(.33f,.25f), p(.45f,.19f), p(.59f,.19f), p(.68f,.25f), p(.71f,.34f), p(.67f,.43f), p(.57f,.53f), p(.46f,.63f), p(.36f,.72f), p(.29f,.78f), p(.72f,.78f)))
            3 -> listOf(listOf(p(.38f,.25f), p(.46f,.20f), p(.56f,.20f), p(.64f,.25f), p(.67f,.33f), p(.63f,.41f), p(.54f,.47f), p(.62f,.50f), p(.68f,.57f), p(.69f,.65f), p(.64f,.74f), p(.55f,.79f), p(.45f,.79f), p(.36f,.74f)))
            4 -> listOf(
                listOf(p(.29f,.57f), p(.62f,.20f), p(.62f,.57f), p(.72f,.57f))
            )
            5 -> listOf(listOf(p(.68f,.21f), p(.35f,.21f), p(.34f,.42f), p(.48f,.42f), p(.61f,.46f), p(.69f,.55f), p(.69f,.65f), p(.63f,.73f), p(.51f,.79f), p(.40f,.79f), p(.29f,.74f)))
            6 -> listOf(listOf(p(.64f,.22f), p(.51f,.24f), p(.40f,.32f), p(.33f,.43f), p(.30f,.56f), p(.32f,.67f), p(.40f,.76f), p(.52f,.80f), p(.63f,.76f), p(.69f,.67f), p(.69f,.57f), p(.63f,.50f), p(.52f,.48f), p(.41f,.51f), p(.33f,.59f)))
            7 -> listOf(listOf(p(.29f,.22f), p(.72f,.22f), p(.55f,.46f), p(.40f,.80f)))
            8 -> listOf(
                ellipse(.50f,.34f,.16f,.15f, clockwise = true),
                ellipse(.50f,.65f,.19f,.16f, clockwise = false)
            )
            9 -> listOf(listOf(p(.64f,.51f), p(.51f,.57f), p(.38f,.53f), p(.31f,.43f), p(.32f,.32f), p(.40f,.23f), p(.53f,.20f), p(.65f,.25f), p(.70f,.36f), p(.67f,.49f), p(.63f,.65f), p(.53f,.76f), p(.40f,.80f), p(.31f,.77f)))
            10 -> listOf(
                listOf(p(.22f,.31f), p(.34f,.20f), p(.34f,.77f)),
                listOf(p(.22f,.78f), p(.45f,.78f)),
                ellipse(.66f,.50f,.15f,.29f, clockwise = true)
            )
            else -> emptyList()
        }
        return routes.map(::sampleRoute)
    }

    fun checkpoints(number: Int): List<NormalizedTracePoint> = routeSegments(number).flatten()

    fun canvasPoint(point: NormalizedTracePoint, width: Float, height: Float): NormalizedTracePoint {
        val scale = minOf(width, height)
        return p((width - scale) / 2f + point.x * scale, (height - scale) / 2f + point.y * scale)
    }

    fun evaluate(number: Int, strokes: List<HandwritingStroke>, width: Float, height: Float): NumberTraceResult {
        val target = checkpoints(number)
        if (target.isEmpty() || width <= 0f || height <= 0f) return NumberTraceResult(0, target.size, 0f)
        val scale = minOf(width, height)
        val offsetX = (width - scale) / 2f
        val offsetY = (height - scale) / 2f
        val studentStrokes = strokes.map { stroke ->
            stroke.points.map { p((it.x - offsetX) / scale, (it.y - offsetY) / scale) }
        }.filter { it.isNotEmpty() }
        val studentPath = studentStrokes.flatten()
        if (studentPath.isEmpty()) return NumberTraceResult(0, target.size, 0f)

        // Pointer events are sparse on some devices. Sample the path between events so a
        // quick, straight finger movement still passes through the guide checkpoints.
        val sampledStudentPath = buildList {
            studentStrokes.forEach { stroke ->
                stroke.forEachIndexed { index, point ->
                    add(point)
                    val nextPoint = stroke.getOrNull(index + 1) ?: return@forEachIndexed
                    val segmentLength = distance(point, nextPoint)
                    val steps = (segmentLength / 0.025f).toInt()
                    for (step in 1..steps) {
                        val fraction = step.toFloat() / (steps + 1)
                        add(p(point.x + (nextPoint.x - point.x) * fraction, point.y + (nextPoint.y - point.y) * fraction))
                    }
                }
            }
        }

        // Keep checkpoints in order. If up to two are missed, wait for three following
        // checkpoints in order before resuming; the skipped checkpoints remain incorrect.
        val covered = mutableSetOf<Int>()
        val skipped = mutableSetOf<Int>()
        var nextCheckpoint = 0
        var pendingStart = -1
        var pendingNext = -1
        var pendingCount = 0
        sampledStudentPath.forEach { point ->
            val expectedHit = nextCheckpoint < target.size && distance(point, target[nextCheckpoint]) <= HIT_RADIUS
            when {
                expectedHit -> {
                    covered += nextCheckpoint
                    nextCheckpoint++
                    pendingStart = -1
                    pendingNext = -1
                    pendingCount = 0
                }
                pendingStart >= 0 && pendingNext < target.size && distance(point, target[pendingNext]) <= HIT_RADIUS -> {
                    pendingCount++
                    pendingNext++
                    if (pendingCount >= RESUME_STREAK) {
                        skipped += nextCheckpoint until pendingStart
                        covered += pendingStart until pendingNext
                        nextCheckpoint = pendingNext
                        pendingStart = -1
                        pendingNext = -1
                        pendingCount = 0
                    }
                }
                else -> {
                    val candidate = (nextCheckpoint + 1..minOf(target.lastIndex, nextCheckpoint + MAX_SKIPPED_CHECKPOINTS))
                        .firstOrNull { distance(point, target[it]) <= HIT_RADIUS }
                    if (candidate != null) {
                        pendingStart = candidate
                        pendingNext = candidate + 1
                        pendingCount = 1
                    }
                }
            }
        }
        val onGuideCount = sampledStudentPath.count { point ->
            target.any { checkpoint -> distance(point, checkpoint) <= ADHERENCE_RADIUS }
        }
        val adherence = onGuideCount.toFloat() / sampledStudentPath.size
        return NumberTraceResult(covered.size, target.size, adherence, covered, skipped)
    }

    fun passThreshold(difficulty: Int): Float = when (difficulty.coerceIn(0, 2)) {
        0 -> .55f
        1 -> .70f
        else -> .82f
    }

    fun passes(number: Int, strokes: List<HandwritingStroke>, width: Float, height: Float, difficulty: Int): Boolean =
        passes(evaluate(number, strokes, width, height), difficulty)

    fun passes(result: NumberTraceResult, difficulty: Int): Boolean =
        result.coverage >= passThreshold(difficulty) && result.routeAdherence >= MIN_ROUTE_ADHERENCE

    private fun sampleRoute(points: List<NormalizedTracePoint>): List<NormalizedTracePoint> {
        if (points.size < 2) return points
        val softened = softenCorners(points)
        val result = mutableListOf(softened.first())
        var nextDistance = CHECKPOINT_STEP
        var traversedDistance = 0f
        softened.zipWithNext().forEach { (start, end) ->
            val segmentDistance = distance(start, end)
            if (segmentDistance <= 0f) return@forEach
            while (nextDistance <= traversedDistance + segmentDistance) {
                val distanceOnSegment = nextDistance - traversedDistance
                result += interpolate(start, end, (distanceOnSegment / segmentDistance).coerceIn(0f, 1f))
                nextDistance += CHECKPOINT_STEP
            }
            traversedDistance += segmentDistance
        }
        if (distance(result.last(), softened.last()) > 0.001f) result += softened.last()
        return result
    }

    private fun softenCorners(points: List<NormalizedTracePoint>): List<NormalizedTracePoint> {
        if (points.size < 3) return points
        return buildList {
            add(points.first())
            for (index in 1 until points.lastIndex) {
                val previous = points[index - 1]
                val corner = points[index]
                val next = points[index + 1]
                val before = interpolate(corner, previous, 0.28f)
                val after = interpolate(corner, next, 0.28f)
                add(before)
                for (step in 1..8) {
                    val t = step / 8f
                    val inverse = 1f - t
                    add(p(
                        inverse * inverse * before.x + 2f * inverse * t * corner.x + t * t * after.x,
                        inverse * inverse * before.y + 2f * inverse * t * corner.y + t * t * after.y
                    ))
                }
            }
            add(points.last())
        }
    }

    private fun interpolate(from: NormalizedTracePoint, to: NormalizedTracePoint, fraction: Float) =
        p(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction)

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, clockwise: Boolean): List<NormalizedTracePoint> {
        val steps = 48
        return (0..steps).map { index ->
            val sign = if (clockwise) 1f else -1f
            val angle = sign * (2.0 * Math.PI * index / steps) - Math.PI / 2.0
            p(cx + rx * kotlin.math.cos(angle).toFloat(), cy + ry * kotlin.math.sin(angle).toFloat())
        }
    }

    private fun p(x: Float, y: Float) = NormalizedTracePoint(x, y)
    private fun distance(a: NormalizedTracePoint, b: NormalizedTracePoint) = hypot(a.x - b.x, a.y - b.y)
}
