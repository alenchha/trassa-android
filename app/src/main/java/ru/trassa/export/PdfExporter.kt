package ru.trassa.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import ru.trassa.R
import ru.trassa.data.ProjectEntity
import ru.trassa.data.RoomDetails
import ru.trassa.geometry.DrillCheck
import ru.trassa.geometry.RouteType
import ru.trassa.geometry.Vec3
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.printColor
import ru.trassa.theme.title
import ru.trassa.util.Formatters
import java.io.File
import kotlin.math.min

/** Плоская схема трасс («вид на стену спереди») для PDF и превью */
object Schematic {
    data class Line(val type: RouteType, val points: List<Pair<Float, Float>>)
    data class Layout(val lines: List<Line>, val pointsPerMeter: Float)

    /**
     * Проецирует 3D-точки на плоскость стены и вписывает в прямоугольник.
     * «Вверх» - ось Y (ARCore направляет её против гравитации), нормаль - средняя
     * нормаль точек без вертикальной составляющей, «вправо» = up × normal.
     * Для нескольких стен получается развёртка на одну плоскость - для MVP достаточно.
     */
    fun layout(room: RoomDetails, left: Float, top: Float, width: Float, height: Float): Layout {
        val routes = room.routes
        var n = routes.flatMap { r -> r.points.map { it.normal } }.fold(Vec3.ZERO) { a, b -> a + b }
        n = Vec3(n.x, 0f, n.z)
        n = if (n.length < 1e-3f) Vec3(0f, 0f, 1f) else n.normalized()
        val right = (Vec3.UP cross n).normalized()

        val projected = routes.map { r -> r.route.type to r.points.map { (it.position dot right) to (it.position dot Vec3.UP) } }
        val all = projected.flatMap { it.second }
        if (all.isEmpty()) return Layout(emptyList(), 1f)
        val margin = 0.25f // зона ±15 см не должна обрезаться краем
        val minX = all.minOf { it.first } - margin
        val maxX = all.maxOf { it.first } + margin
        val minY = all.minOf { it.second } - margin
        val maxY = all.maxOf { it.second } + margin
        val scale = min(width / (maxX - minX), height / (maxY - minY))
        val ox = left + (width - (maxX - minX) * scale) / 2
        val oy = top + (height - (maxY - minY) * scale) / 2
        // Ось Y экрана направлена вниз - переворачиваем
        return Layout(projected.map { (type, pts) -> Line(type, pts.map { (x, y) -> ox + (x - minX) * scale to oy + (maxY - y) * scale }) }, scale)
    }

    fun draw(canvas: Canvas, room: RoomDetails, rect: RectF) {
        canvas.drawRect(rect, Paint().apply { color = TrassaColors.PaperWall.toArgb() })
        val l = layout(room, rect.left + 12, rect.top + 12, rect.width() - 24, rect.height() - 24)
        val zone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.argb(72, 0xD9, 0x57, 0x57)
            strokeWidth = maxOf(DrillCheck.DANGER_RADIUS * 2 * l.pointsPerMeter, 8f)
        }
        l.lines.forEach { canvas.drawPath(path(it.points), zone) }
        l.lines.forEach { line ->
            canvas.drawPath(path(line.points), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                color = line.type.printColor.toArgb()
            })
        }
    }

    private fun path(pts: List<Pair<Float, Float>>) = Path().apply {
        pts.firstOrNull()?.let { moveTo(it.first, it.second) }
        pts.drop(1).forEach { lineTo(it.first, it.second) }
    }
}

/**
 * Экспорт схемы комнаты в PDF для мебельщиков:
 * стр. 1 - схема с зонами ±15 см, сводка, превью; стр. 2+ - таблица трасс, фото штроб, заметки.
 */
class PdfExporter(private val context: Context) {
    private val pageW = 595 // A4 в пунктах
    private val pageH = 842
    private val margin = 40f
    private val dark = TrassaColors.Background.toArgb()
    private val gray = TrassaColors.TextMuted.toArgb()

    private fun font(weight: Int): Typeface? = ResourcesCompat.getFont(context, weight)

    data class Result(val file: File, val pages: Int)

    fun fileName(room: RoomDetails) = "${room.room.name.replace('/', '-').replace(' ', '_')}_схема.pdf"

    fun export(room: RoomDetails, project: ProjectEntity?): Result {
        val doc = PdfDocument()
        var pageNo = 0
        fun newPage(): PdfDocument.Page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())

        // ── Страница 1 ──
        var page = newPage()
        var c = page.canvas
        c.drawColor(TrassaColors.Paper.toArgb())
        val width = pageW - margin * 2
        var y = margin
        y += text(c, "ТРАССА / СХЕМА КОМНАТЫ", 10f, R.font.inter_bold, gray, y, width) + 10
        y += text(c, room.room.name, 28f, R.font.inter_bold, dark, y, width) + 4
        y += text(c, listOfNotNull(project?.name, project?.address?.takeIf { it.isNotBlank() }).joinToString(" · "), 12f, R.font.inter_regular, gray, y, width) + 18
        val scheme = RectF(margin, y, margin + width, y + 260)
        Schematic.draw(c, room, scheme)
        y = scheme.bottom + 14
        y += text(c, "±15 см от трассы - не сверлить", 12f, R.font.inter_semibold, TrassaColors.PaperDanger.toArgb(), y, width) + 8
        y += text(c, "${Formatters.routes(room.routes.size)} · ${Formatters.meters(room.totalLength)} · ${Formatters.numeric(room.room.updatedAt)}",
            12f, R.font.inter_regular, gray, y, width) + 18
        room.room.previewPath?.let { BitmapFactory.decodeFile(it) }?.let { bmp ->
            val maxH = pageH - margin - 40 - y
            val s = min(width / bmp.width, maxH / bmp.height)
            val w = bmp.width * s
            val h = bmp.height * s
            c.drawBitmap(bmp, null, RectF(margin + (width - w) / 2, y, margin + (width + w) / 2, y + h), null)
        }
        text(c, "Оценка по сохранённой AR-схеме. Перед сверлением проверьте детектором.", 10f, R.font.inter_regular, gray, pageH - margin - 12, width)
        doc.finishPage(page)

        // ── Страница 2+: таблица ──
        page = newPage(); c = page.canvas; c.drawColor(TrassaColors.Paper.toArgb()); y = margin
        fun ensure(h: Float) {
            if (y + h > pageH - margin) {
                doc.finishPage(page); page = newPage(); c = page.canvas; c.drawColor(TrassaColors.Paper.toArgb()); y = margin
            }
        }
        y += text(c, "Трассы · ${room.room.name}", 20f, R.font.inter_bold, dark, y, width) + 16
        val cols = floatArrayOf(24f, 100f, 235f, 55f, 45f, 56f)
        fun row(values: List<String>, bold: Boolean, color: Int?) {
            val h = maxOf(24f, measure(values[2], 10f, cols[2] - 6) + 12)
            ensure(h)
            var x = margin
            values.forEachIndexed { i, v ->
                val shift = if (i == 1 && color != null) 12f else 0f
                text(c, v, 10f, if (bold) R.font.inter_bold else R.font.inter_regular, if (i == 1 && color != null) color else dark, y + 6, cols[i] - 6 - shift, x + shift)
                x += cols[i]
            }
            if (color != null) c.drawRect(margin + cols[0], y + 8, margin + cols[0] + 8, y + 16, Paint().apply { this.color = color })
            y += h
            c.drawRect(margin, y, margin + width, y + 0.5f, Paint().apply { this.color = android.graphics.Color.rgb(0xD5, 0xD6, 0xD1) })
        }
        row(listOf("№", "Тип", "Назначение и заметка", "Длина", "Точек", "Дата"), true, null)
        room.routes.forEachIndexed { i, r ->
            val desc = listOf(r.route.title, r.route.note).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "-" }
            row(listOf("${i + 1}", r.route.type.title, desc, Formatters.meters(r.length), "${r.points.size}", Formatters.numeric(r.route.createdAt)),
                false, r.route.type.printColor.toArgb())
        }

        // Фото штроб
        val photos = room.routes.mapNotNull { r -> r.route.photoPath?.let { BitmapFactory.decodeFile(it) }?.let { r to it } }
        if (photos.isNotEmpty()) {
            y += 24; ensure(40f)
            y += text(c, "Фото штроб", 14f, R.font.inter_bold, dark, y, width) + 10
            val cell = (width - 12) / 2
            photos.chunked(2).forEach { pair ->
                ensure(cell * 0.75f + 24)
                pair.forEachIndexed { j, (r, bmp) ->
                    val x = margin + j * (cell + 12)
                    val s = min(cell / bmp.width, cell * 0.75f / bmp.height)
                    c.drawBitmap(bmp, null, RectF(x, y, x + bmp.width * s, y + bmp.height * s), null)
                    text(c, "${r.route.type.title} · ${r.displayTitle}", 9f, R.font.inter_regular, gray, y + cell * 0.75f + 4, cell, x)
                }
                y += cell * 0.75f + 24
            }
        }
        // Заметки
        if (room.notes.isNotEmpty()) {
            y += 16; ensure(40f)
            y += text(c, "Заметки", 14f, R.font.inter_bold, dark, y, width) + 8
            room.notes.forEach { n ->
                val line = "• ${n.text} (${Formatters.numeric(n.createdAt)})"
                ensure(measure(line, 11f, width) + 6)
                y += text(c, line, 11f, R.font.inter_regular, dark, y, width) + 6
            }
        }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName(room))
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return Result(file, pageNo)
    }

    private fun paint(size: Float, fontRes: Int, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = font(fontRes)
    }

    private fun layout(s: String, size: Float, fontRes: Int, color: Int, width: Float) =
        StaticLayout.Builder.obtain(s, 0, s.length, paint(size, fontRes, color), width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    private fun measure(s: String, size: Float, width: Float) = layout(s, size, R.font.inter_regular, 0, width).height.toFloat()

    /** Рисует текст с переносами и возвращает его высоту */
    private fun text(c: Canvas, s: String, size: Float, fontRes: Int, color: Int, y: Float, width: Float, x: Float = margin): Float {
        val l = layout(s, size, fontRes, color, width)
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }
}
