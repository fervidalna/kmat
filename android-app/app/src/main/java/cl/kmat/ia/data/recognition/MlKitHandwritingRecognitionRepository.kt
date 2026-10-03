package cl.kmat.ia.data.recognition

import cl.kmat.ia.domain.model.HandwritingStroke
import cl.kmat.ia.domain.repository.HandwritingRecognitionRepository
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class MlKitHandwritingRecognitionRepository : HandwritingRecognitionRepository {
    private val model = DigitalInkRecognitionModel.builder(DigitalInkRecognitionModelIdentifier.ES).build()
    private val modelManager = RemoteModelManager.getInstance()
    private val recognizer = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build()
    )

    override suspend fun prepare() {
        if (!modelManager.isModelDownloaded(model).awaitResult()) {
            modelManager.download(model, DownloadConditions.Builder().build()).awaitResult()
        }
    }

    override suspend fun recognize(
        strokes: List<HandwritingStroke>,
        width: Float,
        height: Float
    ): String? {
        val ink = Ink.builder().apply {
            strokes.filter { it.points.isNotEmpty() }.forEach { stroke ->
                addStroke(Ink.Stroke.builder().apply {
                    stroke.points.forEach { point ->
                        addPoint(Ink.Point.create(point.x, point.y, point.recordedAt))
                    }
                }.build())
            }
        }.build()
        if (ink.strokes.isEmpty()) return null

        val context = RecognitionContext.builder()
            // ML Kit 19 requires this field even for a standalone answer.
            .setPreContext("")
            .setWritingArea(WritingArea(width, height))
            .build()
        val candidates = recognizer.recognize(ink, context).awaitResult().candidates
        return candidates.firstOrNull { it.text.trim().matches(Regex("[0-9]+")) }?.text?.trim()
    }

    override fun close() = recognizer.close()
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
