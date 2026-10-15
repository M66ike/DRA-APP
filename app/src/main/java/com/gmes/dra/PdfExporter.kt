package com.gmes.dra

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val M = 28f
    private val navy = Color.rgb(24, 43, 73)
    private val grey = Color.rgb(225, 228, 232)

    fun create(context: Context, dra: DraRecord): File {
        val document = PdfDocument()
        pageActivities(document, dra)
        pagePreStart(document, dra)
        pageBriefing(document, dra)
        pageCloseOut(document, dra)
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GMES DRA")
        dir.mkdirs()
        val safeJob = dra.jobNumber.ifBlank { "NoJob" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "DRA_${dra.date.replace("-", "")}_${safeJob}_${dra.status.replace(" ", "_")}.pdf")
        FileOutputStream(file).use(document::writeTo)
        document.close()
        return file
    }

    private fun startPage(doc: PdfDocument, number: Int, dra: DraRecord, title: String): Pair<PdfDocument.Page, Canvas> {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
        val c = page.canvas
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = navy; c.drawRect(0f, 0f, PAGE_W.toFloat(), 62f, p)
        p.color = Color.WHITE; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.textSize = 18f
        c.drawText("GMES Dynamic Risk Assessment", M, 28f, p)
        p.textSize = 10f; c.drawText(title, M, 48f, p)
        c.drawText("${dra.formReference}  •  ${displayDate(dra.date)}  •  ${dra.status}", 340f, 48f, p)
        return page to c
    }

    private fun pageActivities(doc: PdfDocument, dra: DraRecord) {
        val (page, c) = startPage(doc, 1, dra, "Daily planning and task risk review")
        var y = 78f
        val info = listOf("Form Reference" to dra.formReference,"Date" to displayDate(dra.date),"Job Number" to dra.jobNumber,"Client" to dra.client,"Project Name" to dra.projectName,"Contracts Manager" to dra.contractsManager,"Site Supervisor" to dra.siteSupervisor,"Status" to dra.status)
        info.chunked(2).forEach { row -> row.forEachIndexed { i,p -> cell(c,M+i*(PAGE_W-2*M)/2,y,(PAGE_W-2*M)/2,31f,"${p.first}\n${p.second}",8.5f) }; y+=31f }
        y += 8f; y = section(c,y,"Main discussion points from previous work day events and today's activities"); cell(c,M,y,PAGE_W-2*M,56f,dra.discussionPoints,9f); y+=66f
        y = section(c,y,"Planned Activities / H&S and Quality Risks / Risk Control Measures")
        val first=24f; val col=(PAGE_W-2*M-first)/3f
        header(c,M,y,first,26f,"#"); header(c,M+first,y,col,26f,"Planned Activities"); header(c,M+first+col,y,col,26f,"H&S and Quality Risks"); header(c,M+first+2*col,y,col,26f,"Risk Control Measures"); y+=26f
        dra.activities.filter{it.planned.isNotBlank()||it.risks.isNotBlank()||it.controls.isNotBlank()}.take(14).forEachIndexed { i,a ->
            val h=39f; cell(c,M,y,first,h,"${i+1}",8f); cell(c,M+first,y,col,h,a.planned,8f); cell(c,M+first+col,y,col,h,a.risks,8f); cell(c,M+first+2*col,y,col,h,a.controls,8f); y+=h
        }
        footer(c,1); doc.finishPage(page)
    }

    private fun pagePreStart(doc: PdfDocument, dra: DraRecord) {
        val (page,c)=startPage(doc,2,dra,"Pre-start checks and supervisor sign-off")
        var y=78f; y=section(c,y,"Pre-start Checks"); cell(c,M,y,PAGE_W-2*M,38f,"Before starting work STOP, THINK and CHECK! What's the worst that could happen specific to your tasks? What will stop you going home safe today?",8.5f); y+=43f
        dra.preStartChecks.forEachIndexed { i,x -> val h=53f; cell(c,M,y,24f,h,"${i+1}",8f); cell(c,M+24f,y,355f,h,x.question,8f); cell(c,M+379f,y,55f,h,ans(x.answer),8f,true); cell(c,M+434f,y,PAGE_W-2*M-434f,h,x.comments,7.5f); y+=h }
        y+=6f; y=section(c,y,"Supervisor's Signature"); cell(c,M,y,180f,60f,"Name\n${dra.supervisorName.ifBlank{dra.siteSupervisor}}",8.5f); sigCell(c,M+180f,y,PAGE_W-2*M-180f,60f,dra.supervisorSignatureBase64)
        footer(c,2); doc.finishPage(page)
    }

    private fun pageBriefing(doc: PdfDocument, dra: DraRecord) {
        val (page,c)=startPage(doc,3,dra,"Team briefing acknowledgements")
        var y=78f; y=section(c,y,"Briefing attendees"); cell(c,M,y,PAGE_W-2*M,42f,"I have been briefed and I understand my dynamic risk assessment, the key risks and required control measures specific to the tasks I am completing today.",8.5f); y+=50f
        header(c,M,y,24f,24f,"#"); header(c,M+24f,y,190f,24f,"Name"); header(c,M+214f,y,PAGE_W-2*M-214f,24f,"Signature"); y+=24f
        dra.attendees.take(8).forEachIndexed { i,a -> val h=70f; cell(c,M,y,24f,h,"${i+1}",8f); cell(c,M+24f,y,190f,h,a.name,8.5f); sigCell(c,M+214f,y,PAGE_W-2*M-214f,h,a.signatureBase64); y+=h }
        footer(c,3); doc.finishPage(page)
    }

    private fun pageCloseOut(doc: PdfDocument, dra: DraRecord) {
        val (page,c)=startPage(doc,4,dra,"End of shift checks and close-out")
        var y=78f; y=section(c,y,"STOP if anything changes!"); cell(c,M,y,PAGE_W-2*M,42f,"If you or your team change tasks during the day, consider whether the DRA needs reviewing. Think about the items above and re-evaluate if needed.",8.5f); y+=52f
        y=section(c,y,"End of shift checks")
        dra.endShiftChecks.forEachIndexed { i,x -> val h=68f; cell(c,M,y,24f,h,"${i+1}",8f); cell(c,M+24f,y,355f,h,x.question,8f); cell(c,M+379f,y,55f,h,ans(x.answer),8f,true); cell(c,M+434f,y,PAGE_W-2*M-434f,h,x.comments,7.5f); y+=h }
        y+=10f; y=section(c,y,"Points for discussion / matters raised / improvements"); cell(c,M,y,PAGE_W-2*M,92f,dra.improvements,8.5f); y+=102f
        y=section(c,y,"Signed at the end of activity"); cell(c,M,y,200f,50f,"Supervisor Name\n${dra.closeSupervisorName}",8.5f); cell(c,M+200f,y,100f,50f,"Time Closed\n${displayDateTime(dra.closedAt)}",8.5f); sigCell(c,M+300f,y,PAGE_W-2*M-300f,50f,dra.closeSignatureBase64)
        footer(c,4); doc.finishPage(page)
    }

    private fun section(c:Canvas,y:Float,text:String):Float { val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=navy}; c.drawRect(M,y,PAGE_W-M,y+24f,p); p.color=Color.WHITE;p.textSize=10f;p.typeface=Typeface.DEFAULT_BOLD;c.drawText(text,M+7f,y+16f,p);return y+24f }
    private fun header(c:Canvas,x:Float,y:Float,w:Float,h:Float,text:String){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=grey};c.drawRect(x,y,x+w,y+h,p);p.color=Color.DKGRAY;p.style=Paint.Style.STROKE;c.drawRect(x,y,x+w,y+h,p);p.style=Paint.Style.FILL;p.color=Color.BLACK;p.textSize=8f;p.typeface=Typeface.DEFAULT_BOLD;wrap(c,text,x+4,y+4,w-8,h-8,p,true)}
    private fun cell(c:Canvas,x:Float,y:Float,w:Float,h:Float,text:String,size:Float,center:Boolean=false){val b=Paint().apply{color=Color.WHITE};c.drawRect(x,y,x+w,y+h,b);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.DKGRAY;style=Paint.Style.STROKE;strokeWidth=.6f};c.drawRect(x,y,x+w,y+h,p);p.style=Paint.Style.FILL;p.color=Color.BLACK;p.textSize=size;wrap(c,text,x+4,y+4,w-8,h-8,p,center)}
    private fun sigCell(c:Canvas,x:Float,y:Float,w:Float,h:Float,b64:String){cell(c,x,y,w,h,if(b64.isBlank())"Signature" else "",8f);val bytes=decodeSignature(b64)?:return;val bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return;val s=min((w-10)/bmp.width,(h-10)/bmp.height);val dw=bmp.width*s;val dh=bmp.height*s;c.drawBitmap(bmp,null,RectF(x+(w-dw)/2,y+(h-dh)/2,x+(w+dw)/2,y+(h+dh)/2),Paint(Paint.ANTI_ALIAS_FLAG))}
    private fun wrap(c:Canvas,text:String,x:Float,y:Float,w:Float,h:Float,p:Paint,center:Boolean){if(text.isBlank())return;val lh=p.textSize*1.22f;val max=(h/lh).toInt().coerceAtLeast(1);val lines=mutableListOf<String>();text.split("\n").forEach{para->var line="";para.split(Regex("\\s+")).filter{it.isNotEmpty()}.forEach{word->val t=if(line.isEmpty())word else "$line $word";if(p.measureText(t)<=w||line.isEmpty())line=t else{lines+=line;line=word}};if(line.isNotEmpty())lines+=line};lines.take(max).forEachIndexed{i,l->val dx=if(center)x+(w-p.measureText(l))/2 else x;c.drawText(l,dx,y+p.textSize+i*lh,p)}}
    private fun ans(a:Boolean?)=when(a){true->"YES";false->"NO";null->"—"}
    private fun footer(c:Canvas,n:Int){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.GRAY;textSize=7.5f};c.drawText("GMES • Dynamic Risk Assessment • FM_047_8",M,PAGE_H-18f,p);c.drawText("Page $n of 4",PAGE_W-75f,PAGE_H-18f,p)}
}
