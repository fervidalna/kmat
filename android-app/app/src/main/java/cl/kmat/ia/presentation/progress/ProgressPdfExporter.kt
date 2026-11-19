package cl.kmat.ia.presentation.progress

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProgressPdfArea(val name: String, val correct: Int, val total: Int, val currentLevel: String? = null)
data class ProgressPdfTrendPoint(val label: String, val correct: Int, val total: Int)
data class ProgressPdfHistoryRow(
    val date: String,
    val area: String,
    val activity: String,
    val result: String,
    val answer: String = "",
    val isCorrect: Boolean? = null,
    val durationMillis: Long? = null
)
data class ProgressPdfReport(
    val studentName: String,
    val selectedGrade: String,
    val performanceLevel: String,
    val areas: List<ProgressPdfArea>,
    val trend: List<ProgressPdfTrendPoint>,
    val history: List<ProgressPdfHistoryRow>
)

object ProgressPdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f
    private const val NAVY = 0xFF073B86.toInt()
    private const val GREEN = 0xFF00A951.toInt()
    private const val PALE = 0xFFEAF4FC.toInt()
    private const val GRID = 0xFFDCE5EF.toInt()
    private const val INK = 0xFF17365D.toInt()
    private const val RED = 0xFFE85D4A.toInt()

    fun write(output: OutputStream, report: ProgressPdfReport) {
        val document = PdfDocument()
        try {
            drawSummaryPage(document, report)
            if (report.history.isNotEmpty()) drawHistoryPages(document, report)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun drawSummaryPage(document: PdfDocument, report: ProgressPdfReport) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        canvas.drawColor(0xFFFFFFFF.toInt())
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 124f, fill(NAVY))
        canvas.drawText("REPORTE DE PROGRESO", MARGIN, 48f, text(0xFFFFFFFF.toInt(), 21f, true))
        canvas.drawText("Matemática · resumen de avance e historial", MARGIN, 76f, text(0xFFDCEBFF.toInt(), 11f))
        canvas.drawText("Generado: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date()),
            MARGIN, 102f, text(0xFFDCEBFF.toInt(), 9f))

        canvas.drawText(report.studentName.ifBlank { "Estudiante" }, MARGIN, 156f, text(NAVY, 19f, true))
        canvas.drawText("Curso seleccionado: " + report.selectedGrade, MARGIN, 180f, text(INK, 11f, true))
        canvas.drawText("Nivel de desempeño (diagnóstico): " + report.performanceLevel, MARGIN, 199f, text(INK, 10f))

        val correct = report.areas.sumOf { it.correct }
        val total = report.areas.sumOf { it.total }
        val percentage = if (total == 0) null else correct * 100 / total
        val summary = RectF(MARGIN, 218f, PAGE_WIDTH - MARGIN, 296f)
        canvas.drawRoundRect(summary, 15f, 15f, fill(PALE))
        canvas.drawText(percentage?.let { "$it%" } ?: "—", MARGIN + 16f, 268f, text(NAVY, 32f, true))
        canvas.drawText(if (total == 0) "Sin ejercicios respondidos" else "$correct de $total ejercicios correctos",
            MARGIN + 112f, 254f, text(INK, 13f, true))
        canvas.drawText(report.areas.count { it.total > 0 }.toString() + " de " + report.areas.size + " apartados con actividad",
            MARGIN + 112f, 275f, text(INK, 10f))

        canvas.drawText("PORCENTAJE POR MATERIA", MARGIN, 329f, text(NAVY, 12f, true))
        var y = 354f
        report.areas.forEach { area ->
            val value = if (area.total == 0) null else area.correct * 100 / area.total
            canvas.drawText(area.name + (area.currentLevel?.let { " · $it" } ?: ""), MARGIN, y, text(INK, 10f, true))
            canvas.drawText(
                value?.let { "${it}% · ${area.correct}/${area.total}" } ?: "Sin ejercicios",
                PAGE_WIDTH - MARGIN, y, text(INK, 9f, true, Paint.Align.RIGHT)
            )
            val bar = RectF(MARGIN, y + 8f, PAGE_WIDTH - MARGIN, y + 17f)
            canvas.drawRoundRect(bar, 5f, 5f, fill(GRID))
            if (value != null && value > 0) {
                val end = bar.left + bar.width() * (value / 100f)
                canvas.drawRoundRect(RectF(bar.left, bar.top, end, bar.bottom), 5f, 5f, fill(GREEN))
            }
            y += 43f
        }

        canvas.drawText("EVOLUCIÓN DE ACIERTOS", MARGIN, 589f, text(NAVY, 12f, true))
        drawTrend(canvas, report.trend, RectF(MARGIN, 605f, PAGE_WIDTH - MARGIN, 770f))
        canvas.drawText("El porcentaje se calcula como ejercicios correctos sobre ejercicios respondidos.",
            MARGIN, 804f, text(0xFF5C6D80.toInt(), 8f))
        document.finishPage(page)
    }

    private fun drawTrend(canvas: Canvas, points: List<ProgressPdfTrendPoint>, bounds: RectF) {
        if (points.isEmpty()) {
            canvas.drawText("Aún no hay sesiones con resultados para graficar.", bounds.left, bounds.centerY(), text(INK, 10f))
            return
        }
        val left = bounds.left + 35f
        val right = bounds.right - 8f
        val top = bounds.top + 8f
        val bottom = bounds.bottom - 28f
        val height = bottom - top
        val paint = text(0xFF68788A.toInt(), 8f, align = Paint.Align.RIGHT)
        (0..4).forEach { step ->
            val fraction = step / 4f
            val yy = bottom - height * fraction
            canvas.drawLine(left, yy, right, yy, stroke(GRID, 0.7f))
            canvas.drawText("${step * 25}%", left - 6f, yy + 3f, paint)
        }
        fun xAt(index: Int) = if (points.size == 1) (left + right) / 2f else left + (right - left) * index / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, point ->
            val rate = if (point.total == 0) 0f else (point.correct.toFloat() / point.total).coerceIn(0f, 1f)
            val yy = bottom - height * rate
            if (index == 0) path.moveTo(xAt(index), yy) else path.lineTo(xAt(index), yy)
        }
        if (points.size > 1) canvas.drawPath(path, stroke(GREEN, 2.5f))
        points.forEachIndexed { index, point ->
            val rate = if (point.total == 0) 0f else (point.correct.toFloat() / point.total).coerceIn(0f, 1f)
            val xx = xAt(index)
            val yy = bottom - height * rate
            canvas.drawCircle(xx, yy, 3.5f, fill(GREEN))
            canvas.drawText("${point.correct}/${point.total}", xx, yy - 7f, text(INK, 7f, true, Paint.Align.CENTER))
        }
        val labelPaint = text(0xFF68788A.toInt(), 7.5f, align = Paint.Align.CENTER)
        listOf(0, points.lastIndex).distinct().forEach { index ->
            canvas.drawText(fit(points[index].label, 70f, labelPaint), xAt(index), bounds.bottom - 7f, labelPaint)
        }
    }

    private fun drawHistoryPages(document: PdfDocument, report: ProgressPdfReport) {
        var pageNumber = 2
        var page = newHistoryPage(document, pageNumber, report)
        var canvas = page.canvas
        var y = 105f
        report.history.forEach { row ->
            if (y + 54f > PAGE_HEIGHT - 48f) {
                document.finishPage(page)
                pageNumber += 1
                page = newHistoryPage(document, pageNumber, report)
                canvas = page.canvas
                y = 105f
            }
            val resultColor = when (row.isCorrect) {
                true -> GREEN
                false -> RED
                null -> INK
            }
            canvas.drawCircle(MARGIN + 5f, y - 4f, 4f, fill(resultColor))
            canvas.drawText(row.date + " · " + row.area, MARGIN + 17f, y, text(NAVY, 9f, true))
            canvas.drawText(fit(row.result, 135f, text(INK, 8.5f)), PAGE_WIDTH - MARGIN, y,
                text(resultColor, 8.5f, true, Paint.Align.RIGHT))
            canvas.drawText(fit(row.activity, PAGE_WIDTH - 2 * MARGIN - 20f, text(INK, 9f, true)),
                MARGIN + 17f, y + 15f, text(INK, 9f, true))
            if (row.answer.isNotBlank()) {
                canvas.drawText("Resp.: " + fit(row.answer, PAGE_WIDTH - 2 * MARGIN - 105f, text(0xFF5C6D80.toInt(), 8f)),
                    MARGIN + 17f, y + 29f, text(0xFF5C6D80.toInt(), 8f))
            }
            row.durationMillis?.takeIf { it >= 0 }?.let { duration ->
                canvas.drawText("Tiempo: ${formatDuration(duration)}", PAGE_WIDTH - MARGIN, y + 29f,
                    text(0xFF5C6D80.toInt(), 8f, align = Paint.Align.RIGHT))
            }
            canvas.drawLine(MARGIN, y + 39f, PAGE_WIDTH - MARGIN, y + 39f, stroke(GRID, 0.6f))
            y += 49f
        }
        document.finishPage(page)
    }

    private fun formatDuration(durationMillis: Long): String {
        val seconds = (durationMillis / 1000.0).toInt()
        return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
    }

    private fun newHistoryPage(document: PdfDocument, pageNumber: Int, report: ProgressPdfReport): PdfDocument.Page {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        val canvas = page.canvas
        canvas.drawColor(0xFFFFFFFF.toInt())
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 77f, fill(NAVY))
        canvas.drawText("HISTORIAL DE EJERCICIOS", MARGIN, 34f, text(0xFFFFFFFF.toInt(), 16f, true))
        canvas.drawText(report.studentName + " · " + report.selectedGrade, MARGIN, 57f, text(0xFFDCEBFF.toInt(), 9f))
        canvas.drawText("Página " + pageNumber, PAGE_WIDTH - MARGIN, 57f, text(0xFFDCEBFF.toInt(), 8f, align = Paint.Align.RIGHT))
        return page
    }

    private fun fit(value: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(value) <= maxWidth) return value
        var shortened = value
        while (shortened.isNotEmpty() && paint.measureText(shortened + "…") > maxWidth) {
            shortened = shortened.dropLast(1)
        }
        return shortened + "…"
    }

    private fun text(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = width
        style = Paint.Style.STROKE
    }
}
