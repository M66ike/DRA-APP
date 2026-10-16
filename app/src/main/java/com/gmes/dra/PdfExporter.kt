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
    private val lightGrey = Color.rgb(211, 211, 211)
    private val border = Color.rgb(55, 55, 55)
    private val darkGrey = Color.rgb(91, 91, 92)

    private data class Logos(val gmes: Bitmap?, val smart: Bitmap?)

    fun create(context: Context, dra: DraRecord): File {
        val logos = Logos(
            loadAsset(context, "gmes_logo.b64"),
            loadAsset(context, "think_smart_logo.b64")
        )
        val pdf = PdfDocument()
        pageOne(pdf, dra, logos)
        pageTwo(pdf, dra, logos)
        pageThree(pdf, dra, logos)
        pageFour(pdf, dra, logos)
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GMES DRA").apply { mkdirs() }
        val file = File(dir, "${dra.exportFileBaseName()}.pdf")
        FileOutputStream(file).use(pdf::writeTo)
        pdf.close()
        return file
    }

    private fun pageOne(pdf: PdfDocument, d: DraRecord, logos: Logos) {
        val (page, c) = page(pdf, 1, logos)
        var y = 96f
        y = bar(c, y, "GMES Dynamic Risk Assessment - ${status(d.status)}", 30f, 14f)
        val total = PAGE_W - M * 2
        val label = 132f
        listOf(
            "Form Reference" to d.formReferenceLabel(),
            "Status" to status(d.status),
            "Date" to displayDate(d.date),
            "Job Number" to d.jobNumber,
            "Client" to d.client,
            "Project Name" to d.projectName
        ).forEach { (a, b) -> detail(c, y, label, total, a, b); y += 19f }
        c.drawRect(M, y, PAGE_W - M, y + 5f, Paint().apply { color = teal }); y += 5f
        detail(c, y, label, total, "Contracts Manager", d.contractsManager); y += 19f
        detail(c, y, label, total, "Site Supervisor", d.siteSupervisor); y += 19f

        y = bar(c, y, "Main discussion points from previous work day events and todays activities", 21f, 9.5f)
        val discussionH = if (d.discussionPoints.isBlank()) 28f else 42f
        cell(c, M, y, total, discussionH, d.discussionPoints, 8.4f); y += discussionH

        val n = 16f
        val p = 171f
        val r = 172f
        val ctrl = total - n - p - r
        head(c, M, y, n, 27f, "#")
        head(c, M + n, y, p, 27f, "Planned Activities")
        head(c, M + n + p, y, r, 27f, "H&S and Quality Risks")
        head(c, M + n + p + r, y, ctrl, 27f, "Risk Control Measures")
        y += 27f

        val used = d.activities
            .filter { it.planned.isNotBlank() || it.risks.isNotBlank() || it.controls.isNotBlank() }
            .take(14)

        used.forEachIndexed { i, a ->
            val rowH = activityHeight(a, p, r, ctrl).coerceAtMost(max(42f, 830f - y))
            cell(c, M, y, n, rowH, "${i + 1}", 8f, true, true)
            cell(c, M + n, y, p, rowH, a.planned, 8f)
            cell(c, M + n + p, y, r, rowH, a.risks, 8f)
            cell(c, M + n + p + r, y, ctrl, rowH, a.controls, 8f)
            y += rowH
        }
        pdf.finishPage(page)
    }

    private fun pageTwo(pdf: PdfDocument, d: DraRecord, logos: Logos) {
        val (page, c) = page(pdf, 2, logos)
        var y = 100f
        y = bar(c, y, "Pre-start Checks", 23f, 10f)
        val intro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        wrap(
            c,
            "Before starting work STOP, THINK and CHECK! What’s the worst that could happen specific to your tasks? What will stop you going home safe today?",
            M + 3,
            y + 5,
            PAGE_W - M * 2 - 6,
            58f,
            intro
        )
        y += 64f
        val n = 16f
        val answer = 56f
        val body = PAGE_W - M * 2 - n - answer
        val rowH = (748f - y) / 7f
        d.preStartChecks.take(7).forEachIndexed { i, item ->
            checkRow(c, y, n, body, answer, rowH, i + 1, item)
            y += rowH
        }
        y = bar(c, y + 1, "STOP if anything changes!", 21f, 9.5f)
        val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        wrap(
            c,
            "If you or your team change tasks during the day, consider whether the DRA needs reviewing, think about the items above and re-evaluate if needed.",
            M + 3,
            y + 4,
            PAGE_W - M * 2 - 6,
            55f,
            note
        )
        pdf.finishPage(page)
    }

    private fun pageThree(pdf: PdfDocument, d: DraRecord, logos: Logos) {
        val (page, c) = page(pdf, 3, logos)
        var y = 100f
        y = bar(c, y, "Supervisor's Signature", 22f, 10f)
        val total = PAGE_W - M * 2
        val nameW = 242f
        val sigW = total - nameW
        head(c, M, y, nameW, 20f, "Name", false)
        head(c, M + nameW, y, sigW, 20f, "Signature", false)
        y += 20f
        cell(c, M, y, nameW, 70f, d.supervisorName.ifBlank { d.siteSupervisor }, 9f, false, true)
        signature(c, M + nameW, y, sigW, 70f, d.supervisorSignatureBase64)
        y += 76f

        val people = d.attendees
            .filter { it.name.isNotBlank() || it.signatureBase64.isNotBlank() }
            .take(8)

        if (people.isNotEmpty()) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9.2f }
            wrap(
                c,
                "I have been briefed and I understand my dynamic risk assessment, the key risks and required control measures specific to the tasks I am completing today",
                M + 2,
                y,
                total - 4,
                34f,
                p
            )
            y += 35f
            y = bar(c, y, "Briefing attendees", 21f, 9.5f)

            val n = 16f
            val attendee = 226f
            val attendeeSig = total - n - attendee
            head(c, M, y, n, 20f, "#")
            head(c, M + n, y, attendee, 20f, "Name", false)
            head(c, M + n + attendee, y, attendeeSig, 20f, "Signature", false)
            y += 20f
            val rowH = min(82f, max(62f, (825f - y) / people.size.coerceAtLeast(1)))
            people.forEachIndexed { i, a ->
                cell(c, M, y, n, rowH, "${i + 1}", 8f, true, true)
                cell(c, M + n, y, attendee, rowH, a.name, 8.8f, false, true)
                signature(c, M + n + attendee, y, attendeeSig, rowH, a.signatureBase64)
                y += rowH
            }
        }
        pdf.finishPage(page)
    }

    private fun pageFour(pdf: PdfDocument, d: DraRecord, logos: Logos) {
        val (page, c) = page(pdf, 4, logos)
        var y = 100f
        y = bar(c, y, "End of shift checks", 22f, 10f)
        val n = 16f
        val answer = 56f
        val body = PAGE_W - M * 2 - n - answer
        val rowH = 91f
        d.endShiftChecks.take(4).forEachIndexed { i, item ->
            checkRow(c, y, n, body, answer, rowH, i + 1, item)
            y += rowH
        }
        y = bar(c, y, "Points for discussion / matters raised i.e. What Improvements can be made?", 22f, 9.2f)
        cell(c, M, y, PAGE_W - M * 2, 22f, "Supervisor to record feedback and discuss with managers.", 8.7f, false, true, true); y += 22f
        commentBox(c, M, y, PAGE_W - M * 2, 91f, d.improvements); y += 91f
        y = bar(c, y, "Signed (at the end of activity)", 22f, 9.5f)
        val total = PAGE_W - M * 2
        val left = total * .515f
        val right = total - left
        head(c, M, y, left, 22f, "Supervisor Name", false)
        head(c, M + left, y, right, 22f, "Time Closed", false); y += 22f
        cell(c, M, y, left, 70f, d.closeSupervisorName.ifBlank { d.siteSupervisor }, 9f, false, true)
        cell(c, M + left, y, right, 70f, displayDateTime(d.closedAt), 9f, false, true); y += 70f
        head(c, M, y, total, 22f, "Signature", false); y += 22f
        signature(c, M, y, total, max(32f, 830f - y), d.closeSignatureBase64)
        pdf.finishPage(page)
    }

    private fun page(pdf: PdfDocument, number: Int, logos: Logos): Pair<PdfDocument.Page, Canvas> {
        val p = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
        val c = p.canvas
        c.drawColor(Color.WHITE)
        c.drawRect(M, 11f, PAGE_W - M, 16f, Paint().apply { color = teal })

        if (logos.gmes != null) {
            bitmap(c, logos.gmes, RectF(M, 20f, 205f, 88f))
        } else {
            fallbackGmes(c)
        }

        if (logos.smart != null) {
            bitmapRight(c, logos.smart, RectF(345f, 22f, PAGE_W - M, 88f))
        } else {
            fallbackSmart(c)
        }
        return p to c
    }

    private fun fallbackGmes(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            textSize = 31f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("GMES", M + 8, 56f, p)
        p.color = darkGrey
        p.textSize = 6.3f
        c.drawText("GROUP MANAGEMENT ELECTRICAL SURVEYS", M + 8, 69f, p)
    }

    private fun fallbackSmart(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = teal
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("THINK SMART", 372f, 58f, p)
    }

    private fun activityHeight(a: ActivityItem, plannedW: Float, riskW: Float, controlW: Float): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f }
        val planned = lineCount(a.planned, plannedW - 6f, p)
        val risks = lineCount(a.risks, riskW - 6f, p)
        val controls = lineCount(a.controls, controlW - 6f, p)
        val count = max(1, max(planned, max(risks, controls)))
        return max(42f, min(104f, 10f + count * 10.3f))
    }

    private fun lineCount(value: String, width: Float, paint: Paint): Int =
        if (value.isBlank()) 1 else lines(value, width, paint).size.coerceAtLeast(1)

    private fun detail(c: Canvas, y: Float, labelW: Float, totalW: Float, label: String, value: String) {
        fill(c, M, y, labelW, 19f, lightGrey, label, 8.2f, false, true)
        cell(c, M + labelW, y, totalW - labelW, 19f, value, 8.4f, false, true, true)
    }

    private fun checkRow(c: Canvas, y: Float, n: Float, body: Float, answer: Float, h: Float, number: Int, item: CheckItem) {
        cell(c, M, y, n, h, number.toString(), 8f, true, true)
        val x = M + n
        val qh = max(35f, h * .53f)
        cell(c, x, y, body, qh, item.question, 8.2f)
        commentBox(c, x, y + qh, body, h - qh, item.comments)
        cell(c, x + body, y, answer, h, ans(item.answer), 8.8f, true, true, true)
    }

    private fun commentBox(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String) {
        val lw = min(70f, w * .22f)
        cell(c, x, y, lw, h, "Comments:", 7.8f, false, false, true)
        cell(c, x + lw, y, w - lw, h, text, 8f)
    }

    private fun bar(c: Canvas, y: Float, text: String, h: Float, size: Float): Float {
        c.drawRect(M, y, PAGE_W - M, y + h, Paint().apply { color = teal })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText(text, M + 4, y + (h - p.ascent() - p.descent()) / 2, p)
        return y + h
    }

    private fun head(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, center: Boolean = true) =
        fill(c, x, y, w, h, lightGrey, text, 8.5f, center, true)

    private fun fill(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, text: String, size: Float, center: Boolean, bold: Boolean) {
        c.drawRect(x, y, x + w, y + h, Paint().apply { this.color = color })
        outline(c, x, y, w, h)
        text(c, x, y, w, h, text, size, center, false, bold)
    }

    private fun cell(
        c: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        text: String,
        size: Float,
        center: Boolean = false,
        vertical: Boolean = false,
        bold: Boolean = false
    ) {
        c.drawRect(x, y, x + w, y + h, Paint().apply { color = Color.WHITE })
        outline(c, x, y, w, h)
        text(c, x, y, w, h, text, size, center, vertical, bold)
    }

    private fun outline(c: Canvas, x: Float, y: Float, w: Float, h: Float) =
        c.drawRect(
            x,
            y,
            x + w,
            y + h,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = border
                style = Paint.Style.STROKE
                strokeWidth = .8f
            }
        )

    private fun text(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, size: Float, center: Boolean, vertical: Boolean, bold: Boolean) {
        if (text.isBlank()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        val lines = lines(text, w - 6, p)
        val lh = p.textSize * 1.22f
        val maxLines = max(1, (h / lh).toInt())
        val shown = lines.take(maxLines)
        val startY = if (vertical) y + max(2f, (h - shown.size * lh) / 2f) else y + 2f
        shown.forEachIndexed { i, line ->
            val dx = if (center) x + (w - p.measureText(line)) / 2 else x + 3
            c.drawText(line, dx, startY + p.textSize + i * lh, p)
        }
    }

    private fun wrap(c: Canvas, text: String, x: Float, y: Float, w: Float, h: Float, p: Paint) {
        val lh = p.textSize * 1.22f
        lines(text, w, p).take(max(1, (h / lh).toInt())).forEachIndexed { i, line ->
            c.drawText(line, x, y + p.textSize + i * lh, p)
        }
    }

    private fun lines(text: String, width: Float, p: Paint): List<String> {
        val result = mutableListOf<String>()
        text.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                result += ""
            } else {
                var line = ""
                paragraph.split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
                    val trial = if (line.isBlank()) word else "$line $word"
                    if (p.measureText(trial) <= width || line.isBlank()) {
                        line = trial
                    } else {
                        result += line
                        line = word
                    }
                }
                if (line.isNotBlank()) result += line
            }
        }
        return result
    }

    private fun signature(c: Canvas, x: Float, y: Float, w: Float, h: Float, b64: String) {
        cell(c, x, y, w, h, "", 8f)
        val bytes = decodeSignature(b64) ?: return
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val s = min((w - 12) / bmp.width, (h - 10) / bmp.height)
        val dw = bmp.width * s
        val dh = bmp.height * s
        c.drawBitmap(
            bmp,
            null,
            RectF(x + (w - dw) / 2, y + (h - dh) / 2, x + (w + dw) / 2, y + (h + dh) / 2),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
    }

    private fun bitmap(c: Canvas, bmp: Bitmap, box: RectF) {
        val s = min(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        c.drawBitmap(
            bmp,
            null,
            RectF(box.left, box.top + (box.height() - h) / 2, box.left + w, box.top + (box.height() - h) / 2 + h),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
    }

    private fun bitmapRight(c: Canvas, bmp: Bitmap, box: RectF) {
        val s = min(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        c.drawBitmap(
            bmp,
            null,
            RectF(box.right - w, box.top + (box.height() - h) / 2, box.right, box.top + (box.height() - h) / 2 + h),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
    }

    private fun loadAsset(context: Context, name: String): Bitmap? = runCatching {
        val raw = context.assets.open(name).bufferedReader().use { it.readText() }
        val bytes = Base64.decode(raw.trim(), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun status(s: String) = when (s) {
        STATUS_IN_PROGRESS -> "Open"
        STATUS_CLOSED -> "Closed"
        else -> "Not Started"
    }

    private fun ans(v: Boolean?) = when (v) {
        true -> "Yes"
        false -> "No"
        null -> ""
    }
}
