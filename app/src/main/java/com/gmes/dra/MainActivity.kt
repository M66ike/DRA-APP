package com.gmes.dra

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.Paint as APaint
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF182B49), secondary = Color(0xFF0099A8))) { App() } }
    }
}

@Composable private fun App() {
    val context = LocalContext.current
    val db = remember { DraDb(context) }
    var selected by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    if (selected == null) Home(db, refresh, { selected = it }, {
        val d = DraRecord(date = todayIso()); db.save(d); selected = d.id
    }) else Editor(db, selected!!, { refresh++; selected = null })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Home(db: DraDb, refresh: Int, open: (String)->Unit, create:()->Unit) {
    val rows = remember(refresh) { db.listAll() }
    Scaffold(topBar={ TopAppBar(title={Text("GMES Dynamic Risk Assessment")}) }, floatingActionButton={ ExtendedFloatingActionButton(onClick=create, text={Text("New DRA")}) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding=PaddingValues(12.dp,12.dp,12.dp,90.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Text("New DRAs are created for today. In-progress DRAs may be closed later; the actual close date/time is recorded.", style=MaterialTheme.typography.bodySmall) }
            items(rows.size) { i -> val d=rows[i]; Card(onClick={open(d.id)}, modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text(displayDate(d.date), fontWeight=FontWeight.Bold); Spacer(Modifier.width(10.dp)); AssistChip(onClick={}, label={Text(d.status)}) }
                Text(listOf(d.jobNumber,d.projectName).filter{it.isNotBlank()}.joinToString(" • ").ifBlank{"DRA not yet named"})
                if (d.status==STATUS_IN_PROGRESS && d.date!=todayIso()) Text("Close-out overdue", color=Color(0xFF9A5B00), style=MaterialTheme.typography.labelMedium)
            } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Editor(db:DraDb,id:String,back:()->Unit) {
    val context=LocalContext.current
    var d by remember(id){ mutableStateOf(db.get(id)?:DraRecord(id=id)) }
    var tab by remember{ mutableIntStateOf(0) }
    var msg by remember{ mutableStateOf("") }
    fun save(n:DraRecord){ d=n.copy(updatedAt=System.currentTimeMillis()); db.save(d); msg="" }
    BackHandler{back()}
    Scaffold(topBar={TopAppBar(title={Text("${d.jobNumber.ifBlank{"GMES DRA"}} • ${d.status}")}, navigationIcon={TextButton(onClick=back){Text("Back")}}, actions={TextButton(onClick={runCatching{
        val f=PdfExporter.create(context,d); val u=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",f); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share DRA"))
    }.onFailure{msg="PDF export failed: ${it.message}"}}){Text("PDF")}})}) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Details","Activities","Pre-start","Briefing","End shift").forEachIndexed{i,s->FilterChip(selected=tab==i,onClick={if(i!=4||d.status!=STATUS_NOT_STARTED)tab=i},label={Text(s)})} }
            if(msg.isNotBlank()) Text(msg, color=MaterialTheme.colorScheme.error, modifier=Modifier.padding(8.dp))
            when(tab){
                0->Details(d,d.status!=STATUS_CLOSED,::save)
                1->Activities(d,d.status!=STATUS_CLOSED,::save)
                2->Checks("Pre-start checks",d.preStartChecks,d.status==STATUS_NOT_STARTED){save(d.copy(preStartChecks=it))}
                3->Briefing(d,d.status==STATUS_NOT_STARTED,::save){ val e=startErrors(d); if(e.isNotEmpty())msg=e.joinToString("\n") else if(d.date!=todayIso())msg="A DRA can only be started on its work date." else {save(d.copy(status=STATUS_IN_PROGRESS,startedAt=System.currentTimeMillis()));tab=4} }
                4->EndShift(d,d.status==STATUS_IN_PROGRESS,::save){ val e=closeErrors(d); if(e.isNotEmpty())msg=e.joinToString("\n") else save(d.copy(status=STATUS_CLOSED,closedAt=System.currentTimeMillis())) }
            }
        }
    }
}

@Composable private fun Body(content:@Composable ColumnScope.()->Unit)=Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement=Arrangement.spacedBy(9.dp), content=content)
@Composable private fun Field(label:String,value:String,enabled:Boolean=true,lines:Int=1,change:(String)->Unit={})=OutlinedTextField(value=value,onValueChange=change,enabled=enabled,label={Text(label)},minLines=lines,modifier=Modifier.fillMaxWidth())

@Composable private fun Details(d:DraRecord,en:Boolean,save:(DraRecord)->Unit)=Body {
    Field("Form Reference",d.formReference,false); Field("Date",displayDate(d.date),false)
    Field("Job Number",d.jobNumber,en){save(d.copy(jobNumber=it))}; Field("Client",d.client,en){save(d.copy(client=it))}; Field("Project Name",d.projectName,en){save(d.copy(projectName=it))}; Field("Contracts Manager",d.contractsManager,en){save(d.copy(contractsManager=it))}; Field("Site Supervisor",d.siteSupervisor,en){save(d.copy(siteSupervisor=it))}; Field("Discussion points from previous work day events and today's activities",d.discussionPoints,en,3){save(d.copy(discussionPoints=it))}
}

@Composable private fun Activities(d:DraRecord,en:Boolean,save:(DraRecord)->Unit){ LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    itemsIndexed(d.activities,key={_,a->a.id}){i,a->Card{Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Activity ${i+1}",fontWeight=FontWeight.Bold);Field("Planned Activity",a.planned,en,2){v->save(d.copy(activities=d.activities.map{if(it.id==a.id)it.copy(planned=v)else it}))};Field("H&S and Quality Risks",a.risks,en,2){v->save(d.copy(activities=d.activities.map{if(it.id==a.id)it.copy(risks=v)else it}))};Field("Risk Control Measures",a.controls,en,2){v->save(d.copy(activities=d.activities.map{if(it.id==a.id)it.copy(controls=v)else it}))}}}}
    if(en&&d.activities.size<14)item{OutlinedButton(onClick={save(d.copy(activities=d.activities+ActivityItem()))},modifier=Modifier.fillMaxWidth()){Text("Add Activity")}}
} }

@Composable private fun Checks(title:String,items:List<CheckItem>,en:Boolean,save:(List<CheckItem>)->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)};itemsIndexed(items){i,c->Card{Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("${i+1}. ${c.question}");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(c.answer==true,{if(en)save(items.mapIndexed{x,o->if(x==i)o.copy(answer=true)else o})},{Text("Yes")});FilterChip(c.answer==false,{if(en)save(items.mapIndexed{x,o->if(x==i)o.copy(answer=false)else o})},{Text("No")})};Field("Comments${if(c.answer==false)" (required)" else ""}",c.comments,en,2){v->save(items.mapIndexed{x,o->if(x==i)o.copy(comments=v)else o})}}}}}}

@Composable private fun Briefing(d:DraRecord,en:Boolean,save:(DraRecord)->Unit,start:()->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("Supervisor sign-off and briefing",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)};item{Field("Supervisor Name",d.supervisorName,en){save(d.copy(supervisorName=it))}};item{Signature("Supervisor Signature",d.supervisorSignatureBase64,en){save(d.copy(supervisorSignatureBase64=it))}};itemsIndexed(d.attendees,key={_,a->a.id}){i,a->Card{Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Attendee ${i+1}",fontWeight=FontWeight.Bold);Field("Name",a.name,en){v->save(d.copy(attendees=d.attendees.map{if(it.id==a.id)it.copy(name=v)else it}))};Signature("Signature",a.signatureBase64,en){v->save(d.copy(attendees=d.attendees.map{if(it.id==a.id)it.copy(signatureBase64=v)else it}))}}}};if(en&&d.attendees.size<8)item{OutlinedButton(onClick={save(d.copy(attendees=d.attendees+Attendee()))},modifier=Modifier.fillMaxWidth()){Text("Add Attendee")}};item{if(d.status==STATUS_NOT_STARTED)Button(onClick=start,modifier=Modifier.fillMaxWidth()){Text("Complete Briefing & Start DRA")}else Text("Started ${displayDateTime(d.startedAt)}",fontWeight=FontWeight.Bold)}}}

@Composable private fun EndShift(d:DraRecord,en:Boolean,save:(DraRecord)->Unit,close:()->Unit){if(d.status==STATUS_NOT_STARTED){Body{Text("Complete the pre-start briefing first.")};return};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("End of shift checks",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)};itemsIndexed(d.endShiftChecks){i,c->Card{Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("${i+1}. ${c.question}");Row{FilterChip(c.answer==true,{if(en)save(d.copy(endShiftChecks=d.endShiftChecks.mapIndexed{x,o->if(x==i)o.copy(answer=true)else o}))},{Text("Yes")});Spacer(Modifier.width(8.dp));FilterChip(c.answer==false,{if(en)save(d.copy(endShiftChecks=d.endShiftChecks.mapIndexed{x,o->if(x==i)o.copy(answer=false)else o}))},{Text("No")})};Field("Comments${if(c.answer==false)" (required)" else ""}",c.comments,en,2){v->save(d.copy(endShiftChecks=d.endShiftChecks.mapIndexed{x,o->if(x==i)o.copy(comments=v)else o}))}}}};item{Field("Points for discussion / matters raised / improvements",d.improvements,en,3){save(d.copy(improvements=it))}};item{Field("Supervisor Name",d.closeSupervisorName,en){save(d.copy(closeSupervisorName=it))}};item{Signature("End of Activity Signature",d.closeSignatureBase64,en){save(d.copy(closeSignatureBase64=it))}};item{if(d.status==STATUS_IN_PROGRESS)Button(onClick=close,modifier=Modifier.fillMaxWidth()){Text("Close DRA")}else Text("Closed ${displayDateTime(d.closedAt)}",fontWeight=FontWeight.Bold)}}}

@Composable private fun Signature(title:String,base64:String,en:Boolean,save:(String)->Unit){var size by remember{mutableStateOf(IntSize.Zero)};val strokes=remember{mutableStateListOf<SnapshotStateList<Offset>>()};Column{Text(title,fontWeight=FontWeight.SemiBold);Box(Modifier.fillMaxWidth().height(130.dp).background(Color.White).onSizeChanged{size=it}.pointerInput(en){if(en)detectDragGestures(onDragStart={strokes.add(mutableStateListOf(it))},onDrag={ch,_->strokes.lastOrNull()?.add(ch.position)})}){Canvas(Modifier.fillMaxSize()){strokes.forEach{s->s.zipWithNext().forEach{(a,b)->drawLine(Color.Black,a,b,3f)}}};if(strokes.isEmpty())Text(if(base64.isBlank())"Sign here" else "Signature saved",Modifier.align(Alignment.Center))};if(en)Row{TextButton(onClick={strokes.clear();save("")}){Text("Clear")};Button(onClick={if(strokes.isNotEmpty())save(sig(strokes.map{it.toList()},size))},enabled=strokes.isNotEmpty()){Text("Save Signature")}}}}

private fun sig(strokes:List<List<Offset>>,s:IntSize):String{val b=Bitmap.createBitmap(1000,300,Bitmap.Config.ARGB_8888);val c=ACanvas(b);c.drawColor(AColor.WHITE);val p=APaint(APaint.ANTI_ALIAS_FLAG).apply{color=AColor.BLACK;strokeWidth=7f;strokeCap=APaint.Cap.ROUND};val sx=1000f/s.width.coerceAtLeast(1);val sy=300f/s.height.coerceAtLeast(1);strokes.forEach{it.zipWithNext().forEach{(a,z)->c.drawLine(a.x*sx,a.y*sy,z.x*sx,z.y*sy,p)}};val o=ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,o);return Base64.encodeToString(o.toByteArray(),Base64.NO_WRAP)}
private fun startErrors(d:DraRecord)=buildList{if(d.jobNumber.isBlank())add("Enter Job Number");if(d.client.isBlank())add("Enter Client");if(d.projectName.isBlank())add("Enter Project Name");if(d.siteSupervisor.isBlank())add("Enter Site Supervisor");if(d.activities.none{it.planned.isNotBlank()})add("Add a planned activity");if(d.preStartChecks.any{it.answer==null})add("Answer every pre-start check");if(d.preStartChecks.any{it.answer==false&&it.comments.isBlank()})add("Add comments for each No response");if(d.supervisorName.isBlank()||d.supervisorSignatureBase64.isBlank())add("Supervisor name and signature are required");val a=d.attendees.filter{it.name.isNotBlank()||it.signatureBase64.isNotBlank()};if(a.isEmpty()||a.any{it.name.isBlank()||it.signatureBase64.isBlank()})add("Each briefing attendee needs a name and signature")}
private fun closeErrors(d:DraRecord)=buildList{if(d.endShiftChecks.any{it.answer==null})add("Answer every end-of-shift check");if(d.endShiftChecks.any{it.answer==false&&it.comments.isBlank()})add("Add comments for each No response");if(d.closeSupervisorName.isBlank()||d.closeSignatureBase64.isBlank())add("Close-out supervisor name and signature are required")}
