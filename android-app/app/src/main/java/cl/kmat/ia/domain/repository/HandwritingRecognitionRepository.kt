package cl.kmat.ia.domain.repository

import cl.kmat.ia.domain.model.HandwritingStroke

interface HandwritingRecognitionRepository {
    suspend fun prepare()
    suspend fun recognize(strokes: List<HandwritingStroke>, width: Float, height: Float): String?
    fun close()
}
