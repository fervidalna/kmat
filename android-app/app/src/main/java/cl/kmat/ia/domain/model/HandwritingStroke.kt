package cl.kmat.ia.domain.model

data class StrokePoint(val x: Float, val y: Float, val recordedAt: Long)

data class HandwritingStroke(val points: List<StrokePoint>)
