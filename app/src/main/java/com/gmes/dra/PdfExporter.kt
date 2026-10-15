package com.gmes.dra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val M = 12f

    private val teal = Color.rgb(12, 110, 106)
    private val darkGrey = Color.rgb(91, 91, 92)
    private val coolGrey = Color.rgb(157, 157, 157)
    private val headerGrey = Color.rgb(210, 210, 210)
    private val border = Color.rgb(45, 45, 45)

    fun create(context: Context, dra: DraRecord): File {
        val logos = Logos(
            gmes = loadAssetBitmap(context, "gmes_logo.b64"),
            smart = loadAssetBitmap(context, "think_smart_logo.b64")
        )
        val document = PdfDocument()
        pageActivities(document, dra, logos)
        pagePreStart(document, dra, logos)
        pageBriefing(document, dra, logos)
        pageCloseOut(document, dra, logos)

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GMES DRA")
        dir.mkdirs()
        val file = File(dir, "${dra.exportFileBaseName()}.pdf")
        FileOutputStream(file).use(document::writeTo)
        document.close()
        return file
    }

    private data class Logos(val gmes: Bitmap?, val smart: Bitmap?)

    private fun newPage(doc: PdfDocument, number: Int, logos: Logos): Pair<PdfDocument.Page, Canvas> {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        drawHeader(c, logos)
        return page to c
    }

    private fun drawHeader(c: Canvas, logos: Logos) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal }
        c.drawRect(M, 11f, PAGE_W - M, 16f, p)
        logos.gmes?.let { drawBitmapFit(c, it, RectF(M, 22f, 185f, 88f)) }
        logos.smart?.let { drawBitmapFit(c, it, RectF(370f, 25f, PAGE_W - M, 86f)) }
    }

    private fun pageActivities(doc: PdfDocument, dra: DraRecord, logos: Logos) {
        val (page, c) = newPage(doc, 1, logos)
        var y = 96f
        val status = displayStatus(dra.status)
        y = tealBar(c, y, "GMES Dynamic Risk Assessment - $status", 14f, 30f)

        val labelW = 132f
        val totalW = PAGE_W - 2 * M
        val details = listOf(
            "Form Reference" to dra.formReferenceLabel(),
            "Status" to status,
            "Date" to displayDate(dra.date),
            "Job Number" to dra.jobNumber,
            "Client" to dra.client,
            "Project Name" to dra.projectName
        )
        details.forEach { (label, value) ->
            detailRow(c, y, labelW, totalW, label, value)
            y += 19f
        }
        c.drawRect(M, y, PAGE_W - M, y + 5f, Paint().apply { color = teal })
        y += 5f
        listOf(
            "Contracts Manager" to dra.contractsManager,
            "Site Supervisor" to dra.siteSupervisor
        ).forEach { (label, value) ->
            detailRow(c, y, labelW, totalW, label, value)
            y += 19f
        }

        y = tealBar(c, y, "Main discussion points from previous work day events and todays activities", 9.5f, 21f)
        if (dra.discussionPoints.isNotBlank()) {
            val h = 33f
            borderedCell(c, M, y, totalW, h, dra.discussionPoints, 8.5f)
            y += h
        }

        val numW = 16f
        val plannedW = 171f
        val riskW = 172f
        val controlW = totalW - numW - plannedW - riskW
        tableHeader(c, M, y, numW, 27f, "#", 8.5f)
        tableHeader(c, M + numW, y, plannedW, 27f, "Planned Activities", 8.5f)
        tableHeader(c, M + numW + plannedW, y, riskW, 27f, "H&S and Quality Risks", 8.5f)
        tableHeader(c, M + numW + plannedW + riskW, y, controlW, 27f, "Risk Control Measures", 8.5f)
        y += 27f

        val used = dra.activities.filter { it.planned.isNotBlank() || it.risks.isNotBlank() || it.controls.isNotBlank() }.take(14)
        val rows = if (used.isEmpty()) List(14) { ActivityItem() } else used
        val remaining = 830f - y
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.2f; typeface = Typeface.DEFAULT }
        val desired = rows.map { a ->
            max(
                33f,
                max(
                    requiredTextHeight(a.planned, plannedW - 6f, basePaint),
                    max(
                        requiredTextHeight(a.risks, riskW - 6f, basePaint),
                        requiredTextHeight(a.controls, controlW - 6f, basePaint)
                    )
                ) + 7f
            )
        }
        val desiredTotal = desired.sum()
        val heights = if (desiredTotal <= remaining) {
            val extra = (remaining - desiredTotal) / rows.size
            desired.map { it + extra }
        } else {
            val scale = remaining / desiredTotal
            desired.map { it * scale }
        }

        rows.forEachIndexed { i, a ->
            val h = heights[i]
            borderedCell(c, M, y, numW, h, "${i + 1}", 8f)
            borderedCell(c, M + numW, y, plannedW, h, a.planned, 8.2f)
            borderedCell(c, M + numW + plannedW, y, riskW, h, a.risks, 8.2f)
            borderedCell(c, M + numW + plannedW + riskW, y, controlW, h, a.controls, 8.2f)
            y += h
        }

        doc.finishPage(page)
    }

    private fun pagePreStart(doc: PdfDocument, dra: DraRecord, logos: Logos) {
        val (page, c) = newPage(doc, 2, logos)
        var y = 100f
        y = tealBar(c, y, "Pre-start Checks", 10f, 23f)

        val prompt = "Before starting work STOP, THINK and CHECK! What’s the worst that could happen specific to your tasks? What will stop you going home safe today?"
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        wrap(c, prompt, M + 3f, y + 7f, PAGE_W - 2 * M - 6f, 58f, pp, false)
        y += 64f

        val numW = 16f
        val ansW = 56f
        val mainW = PAGE_W - 2 * M - numW - ansW
        val available = 748f - y
        val rowH = available / 7f
        dra.preStartChecks.take(7).forEachIndexed { i, item ->
            drawCheckRow(c, y, numW, mainW, ansW, rowH, i + 1, item)
            y += rowH
        }

        y += 1f
        y = tealBar(c, y, "STOP if anything changes!", 9.5f, 21f)
        val note = "If you or your team change tasks during the day, consider whether the DRA needs reviewing, think about the items above and re-evaluate if needed."
        val np = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        wrap(c, note, M + 2f, y + 5f, PAGE_W - 2 * M - 4f, 55f, np, false)

        doc.finishPage(page)
    }

    private fun pageBriefing(doc: PdfDocument, dra: DraRecord, logos: Logos) {
        val (page, c) = newPage(doc, 3, logos)
        var y = 100f
        y = tealBar(c, y, "Supervisor's Signature", 10f, 22f)

        val totalW = PAGE_W - 2 * M
        val nameW = 242f
        val sigW = totalW - nameW
        tableHeader(c, M, y, nameW, 20f, "Name", 8.5f, false)
        tableHeader(c, M + nameW, y, sigW, 20f, "Signature", 8.5f, false)
        y += 20f
        val supervisor = dra.supervisorName.ifBlank { dra.siteSupervisor }
        borderedCell(c, M, y, nameW, 70f, supervisor, 9f, false, true)
        signatureCell(c, M + nameW, y, sigW, 70f, dra.supervisorSignatureBase64)
        y += 74f

        val statement = "I have been briefed and I understand my dynamic risk assessment, the key risks and required control measures specific to the tasks I am completing today"
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9.2f }
        wrap(c, statement, M + 2f, y, totalW - 4f, 34f, sp, false)
        y += 35f

        y = tealBar(c, y, "Breifing attendees", 9.5f, 21f)
        val numW = 16f
        val attendeeNameW = 226f
        val attendeeSigW = totalW - numW - attendeeNameW
        tableHeader(c, M, y, numW, 20f, "#", 8f)
        tableHeader(c, M + numW, y, attendeeNameW, 20f, "Name", 8.5f, false)
        tableHeader(c, M + numW + attendeeNameW, y, attendeeSigW, 20f, "Signature", 8.5f, false)
        y += 20f

        val rowH = (830f - y) / 8f
        val attendees = (dra.attendees + List(8) { Attendee() }).take(8)
        attendees.forEachIndexed { i, a ->
            borderedCell(c, M, y, numW, rowH, "${i + 1}", 8f)
            borderedCell(c, M + numW, y, attendeeNameW, rowH, a.name, 8.8f, false, true)
            signatureCell(c, M + numW + attendeeNameW, y, attendeeSigW, rowH, a.signatureBase64)
            y += rowH
        }

        doc.finishPage(page)
    }

    private fun pageCloseOut(doc: PdfDocument, dra: DraRecord, logos: Logos) {
        val (page, c) = newPage(doc, 4, logos)
        var y = 100f
        y = tealBar(c, y, "End of shift checks", 10f, 22f)

        val numW = 16f
        val ansW = 56f
        val mainW = PAGE_W - 2 * M - numW - ansW
        val rowH = 91f
        dra.endShiftChecks.take(4).forEachIndexed { i, item ->
            drawCheckRow(c, y, numW, mainW, ansW, rowH, i + 1, item)
            y += rowH
        }

        y = tealBar(c, y, "Points for discussion / matters raised i.e. What Improvements can be made?", 9.2f, 22f)
        borderedCell(c, M, y, PAGE_W - 2 * M, 22f, "Supervisor to record feedback and discuss with managers.", 8.7f, false, true)
        y += 22f

        val commentsH = 91f
        drawLabeledCommentBox(c, M, y, PAGE_W - 2 * M, commentsH, dra.improvements)
        y += commentsH

        y = tealBar(c, y, "Signed (at the end of activity)", 9.5f, 22f)
        val totalW = PAGE_W - 2 * M
        val leftW = totalW * 0.515f
        val rightW = totalW - leftW
        tableHeader(c, M, y, leftW, 22f, "Supervisor Name", 8.8f, false)
        tableHeader(c, M + leftW, y, rightW, 22f, "Time Closed", 8.8f, false)
        y += 22f
        borderedCell(c, M, y, leftW, 70f, dra.closeSupervisorName.ifBlank { dra.siteSupervisor }, 9f, false, true)
        borderedCell(c, M + leftW, y, rightW, 70f, displayDateTime(dra.closedAt), 9f, false, true)
        y += 70f
        tableHeader(c, M, y, totalW, 22f, "Signature", 8.8f, false)
        y += 22f
        signatureCell(c, M, y, totalW, 830f - y, dra.closeSignatureBase64)

        doc.finishPage(page)
    }

    private fun detailRow(c: Canvas, y: Float, labelW: Float, totalW: Float, label: String, value: String) {
        filledCell(c, M, y, labelW, 19f, headerGrey, label, 8.2f, false, true)
        borderedCell(c, M + labelW, y, totalW - labelW, 19f, value, 8.4f, false, true, true)
    }

    private fun drawCheckRow(
        c: Canvas,
        y: Float,
        numW: Float,
        mainW: Float,
        ansW: Float,
        h: Float,
        number: Int,
        item: CheckItem
    ) {
        borderedCell(c, M, y, numW, h, number.toString(), 8f, false, true)
        val x = M + numW
        val questionH = max(35f, h * 0.53f)
        borderedCell(c, x, y, mainW, questionH, item.question, 8.2f, false, false)
        drawLabeledCommentBox(c, x, y + questionH, mainW, h - questionH, item.comments)
        borderedCell(c, x + mainW, y, ansW, h, ans(item.answer), 8.8f, true, true, true)
    }

    private fun drawLabeledCommentBox(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String) {
        val labelW = 70f.coerceAtMost(w * 0.22f)
        borderedCell(c, x, y, labelW, h, "Comments:", 7.8f, false, false, true)
        borderedCell(c, x + labelW, y, w - labelW, h, text, 8f, false, false)
    }

    private fun tealBar(c: Canvas, y: Float, text: String, textSize: Float, h: Float): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal }
        c.drawRect(M, y, PAGE_W - M, y + h, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val baseline = y + (h - (t.descent() + t.ascent())) / 2f
        c.drawText(text, M + 4f, baseline, t)
        return y + h
    }

    private fun tableHeader(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, size: Float, center: Boolean = true) {
        filledCell(c, x, y, w, h, headerGrey, text, size, center, true)
    }

    private fun filledCell(c: Canvas, x: Float, y: Float, w: Float, h: Float, fill: Int, text: String, size: Float, center: Boolean, bold: Boolean) {
        val bg = Paint().apply { color = fill }
        c.drawRect(x, y, x + w, y + h, bg)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        c.drawRect(x, y, x + w, y + h, line)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        wrap(c, text, x + 3f, y + 2f, w - 6f, h - 4f, p, center)
    }

    private fun borderedCell(
        c: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        text: String,
        size: Float,
        center: Boolean = false,
        verticalCenter: Boolean = false,
        bold: Boolean = false
    ) {
        val bg = Paint().apply { color = Color.WHITE }
        c.drawRect(x, y, x + w, y + h, bg)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        c.drawRect(x, y, x + w, y + h, line)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        if (verticalCenter && text.isNotBlank()) {
            val lines = wrappedLines(text, w - 6f, p)
            val lh = p.textSize * 1.22f
            val textH = lines.size * lh
            val yy = y + max(2f, (h - textH) / 2f)
            drawLines(c, lines, x + 3f, yy, w - 6f, h - 4f, p, center)
        } else {
            wrap(c, text, x + 3f, y + 2f, w - 6f, h - 4f, p, center)
        }
    }

    private fun signatureCell(c: Canvas, x: Float, y: Float, w: Float, h: Float, b64: String) {
        borderedCell(c, x, y, w, h, "", 8f)
        val bytes = decodeSignature(b64) ?: return
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val scale = min((w - 12f) / bmp.width, (h - 10f) / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        c.drawBitmap(bmp, null, RectF(x + (w - dw) / 2f, y + (h - dh) / 2f, x + (w + dw) / 2f, y + (h + dh) / 2f), Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private fun drawBitmapFit(c: Canvas, bitmap: Bitmap, box: RectF) {
        val scale = min(box.width() / bitmap.width, box.height() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = box.left
        val top = box.top + (box.height() - h) / 2f
        c.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private fun loadAssetBitmap(context: Context, name: String): Bitmap? = runCatching {
        val raw = context.assets.open(name).bufferedReader().use { it.readText() }
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun displayStatus(status: String): String = when (status) {
        STATUS_IN_PROGRESS -> "Open"
        STATUS_CLOSED -> "Closed"
        else -> "Not Started"
    }

    private fun ans(a: Boolean?): String = when (a) {
        true -> "Yes"
        false -> "No"
        null -> ""
    }

    private fun requiredTextHeight(text: String, width: Float, p: Paint): Float {
        if (text.isBlank()) return p.textSize * 1.22f
        return wrappedLines(text, width, p).size * p.textSize * 1.22f
    }

    private fun wrap(c: Canvas, text: String, x: Float, y: Float, w: Float, h: Float, p: Paint, center: Boolean) {
        if (text.isBlank()) return
        val lines = wrappedLines(text, w, p)
        drawLines(c, lines, x, y, w, h, p, center)
    }

    private fun drawLines(c: Canvas, lines: List<String>, x: Float, y: Float, w: Float, h: Float, p: Paint, center: Boolean) {
        val lh = p.textSize * 1.22f
        val maxLines = (h / lh).toInt().coerceAtLeast(1)
        lines.take(maxLines).forEachIndexed { i, line ->
            val dx = if (center) x + (w - p.measureText(line)) / 2f else x
            c.drawText(line, dx, y + p.textSize + i * lh, p)
        }
    }

    private fun wrappedLines(text: String, width: Float, p: Paint): List<String> {
        val lines = mutableListOf<String>()
        text.split("\n").forEach { para ->
            if (para.isBlank()) {
                lines += ""
            } else {
                var line = ""
                para.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
                    val trial = if (line.isEmpty()) word else "$line $word"
                    if (p.measureText(trial) <= width || line.isEmpty()) {
                        line = trial
                    } else {
                        lines += line
                        line = word
                    }
                }
                if (line.isNotEmpty()) lines += line
            }
        }
        return lines
    }
}
