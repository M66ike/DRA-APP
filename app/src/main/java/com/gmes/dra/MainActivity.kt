package com.gmes.dra

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.Paint as APaint
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream

private val GMES_TEAL = Color(0xFF0C6E6A)
private val GMES_DARK_GREY = Color(0xFF5B5B5C)
private val GMES_COOL_GREY = Color(0xFF9D9D9D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = GMES_TEAL,
                    onPrimary = Color.White,
                    secondary = GMES_DARK_GREY,
                    tertiary = GMES_COOL_GREY
                )
            ) { App() }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val db = remember { DraDb(context) }
    val saved = remember { SavedDataStore(context).apply { seedFromRecords(db.listAll()) } }
    var selected by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showSavedData by remember { mutableStateOf(false) }

    fun createDra() {
        val supervisor = saved.last(SavedDataStore.KEY_SITE_SUPERVISOR)
        val d = DraRecord(
            date = todayIso(),
            jobNumber = saved.last(SavedDataStore.KEY_JOB_NUMBER),
            client = saved.last(SavedDataStore.KEY_CLIENT),
            projectName = saved.last(SavedDataStore.KEY_PROJECT_NAME),
            contractsManager = saved.last(SavedDataStore.KEY_CONTRACTS_MANAGER),
            siteSupervisor = supervisor,
            supervisorName = supervisor,
            closeSupervisorName = supervisor
        )
        db.save(d)
        selected = d.id
    }

    if (selected == null) {
        Home(
            db = db,
            refresh = refresh,
            open = { selected = it },
            create = ::createDra,
            openSavedData = { showSavedData = true }
        )
    } else {
        Editor(
            db = db,
            saved = saved,
            id = selected!!,
            back = { refresh++; selected = null },
            openSavedData = { showSavedData = true }
        )
    }

    if (showSavedData) {
        SavedDataDialog(saved) { showSavedData = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(
    db: DraDb,
    refresh: Int,
    open: (String) -> Unit,
    create: () -> Unit,
    openSavedData: () -> Unit
) {
    val rows = remember(refresh) { db.listAll() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GMES Dynamic Risk Assessment") },
                actions = { TextButton(onClick = openSavedData) { Text("Saved Data") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = create, text = { Text("New DRA") })
        }
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 90.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "New DRAs are created for today and pre-filled from your most recently used details. In-progress DRAs can be closed later and the actual close date/time is recorded.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(rows.size) { i ->
                val d = rows[i]
                Card(onClick = { open(d.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayDate(d.date), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            AssistChip(onClick = {}, label = { Text(d.status) })
                        }
                        Text(
                            listOf(d.jobNumber, d.projectName).filter { it.isNotBlank() }
                                .joinToString(" • ").ifBlank { "DRA not yet named" }
                        )
                        if (d.status == STATUS_IN_PROGRESS && d.date != todayIso()) {
                            Text(
                                "Close-out overdue",
                                color = Color(0xFF9A5B00),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(
    db: DraDb,
    saved: SavedDataStore,
    id: String,
    back: () -> Unit,
    openSavedData: () -> Unit
) {
    val context = LocalContext.current
    var d by remember(id) { mutableStateOf(db.get(id) ?: DraRecord(id = id)) }
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }

    fun save(n: DraRecord) {
        d = n.copy(updatedAt = System.currentTimeMillis())
        db.save(d)
        msg = ""
    }

    BackHandler { back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${d.projectName.ifBlank { d.jobNumber.ifBlank { "GMES DRA" } }} • ${d.status}") },
                navigationIcon = { TextButton(onClick = back) { Text("Back") } },
                actions = {
                    TextButton(onClick = openSavedData) { Text("Saved") }
                    TextButton(onClick = {
                        runCatching {
                            val f = PdfExporter.create(context, d)
                            val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, u)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "Share DRA"
                                )
                            )
                        }.onFailure { msg = "PDF export failed: ${it.message}" }
                    }) { Text("PDF") }
                }
            )
        }
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Details", "Activities", "Pre-start", "Briefing", "End shift").forEachIndexed { i, s ->
                    GmesFilterChip(
                        selected = tab == i,
                        onClick = { if (i != 4 || d.status != STATUS_NOT_STARTED) tab = i },
                        label = s
                    )
                }
            }
            if (msg.isNotBlank()) {
                Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            }
            when (tab) {
                0 -> Details(d, d.status != STATUS_CLOSED, saved, ::save)
                1 -> Activities(d, d.status != STATUS_CLOSED, saved, ::save)
                2 -> Checks("Pre-start checks", d.preStartChecks, d.status == STATUS_NOT_STARTED) {
                    save(d.copy(preStartChecks = it))
                }
                3 -> Briefing(d, d.status == STATUS_NOT_STARTED, ::save) {
                    val e = startErrors(d)
                    if (e.isNotEmpty()) {
                        msg = e.joinToString("\n")
                    } else if (d.date != todayIso()) {
                        msg = "A DRA can only be started on its work date."
                    } else {
                        val supervisor = d.supervisorName.ifBlank { d.siteSupervisor }
                        val started = d.copy(
                            supervisorName = supervisor,
                            status = STATUS_IN_PROGRESS,
                            startedAt = System.currentTimeMillis()
                        )
                        saved.rememberRecord(started)
                        save(started)
                        tab = 4
                    }
                }
                4 -> EndShift(d, d.status == STATUS_IN_PROGRESS, ::save) {
                    val e = closeErrors(d)
                    if (e.isNotEmpty()) {
                        msg = e.joinToString("\n")
                    } else {
                        val supervisor = d.closeSupervisorName.ifBlank { d.siteSupervisor }
                        val closed = d.copy(
                            closeSupervisorName = supervisor,
                            status = STATUS_CLOSED,
                            closedAt = System.currentTimeMillis()
                        )
                        saved.rememberRecord(closed)
                        save(closed)
                    }
                }
            }
        }
    }
}

@Composable
private fun Body(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content
    )

@Composable
private fun Field(
    label: String,
    value: String,
    enabled: Boolean = true,
    lines: Int = 1,
    change: (String) -> Unit = {}
) = OutlinedTextField(
    value = value,
    onValueChange = change,
    enabled = enabled,
    label = { Text(label) },
    minLines = lines,
    modifier = Modifier.fillMaxWidth()
)

@Composable
private fun SuggestField(
    label: String,
    value: String,
    enabled: Boolean,
    store: SavedDataStore,
    key: String,
    change: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    val all = store.values(key)
    val query = value.trim()
    val suggestions = if (query.isBlank()) {
        all.take(7)
    } else {
        val prefix = all.filter { !it.equals(query, true) && it.startsWith(query, true) }
        val contains = all.filter {
            !it.equals(query, true) && !it.startsWith(query, true) && it.contains(query, true)
        }
        (prefix + contains).distinct().take(7)
    }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                change(it)
                menuVisible = true
            },
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    menuVisible = true
                } else {
                    store.addValue(key, value)
                }
            }
        )
        DropdownMenu(
            expanded = enabled && focused && menuVisible && suggestions.isNotEmpty(),
            onDismissRequest = { menuVisible = false },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        change(suggestion)
                        store.addValue(key, suggestion)
                        menuVisible = false
                    }
                )
            }
        }
    }
}

@Composable
private fun Details(d: DraRecord, en: Boolean, store: SavedDataStore, save: (DraRecord) -> Unit) = Body {
    Field("Form Reference", d.formReferenceLabel(), false)
    Field("Date", displayDate(d.date), false)
    SuggestField("Job Number", d.jobNumber, en, store, SavedDataStore.KEY_JOB_NUMBER) {
        save(d.copy(jobNumber = it))
    }
    SuggestField("Client", d.client, en, store, SavedDataStore.KEY_CLIENT) {
        save(d.copy(client = it))
    }
    SuggestField("Project Name", d.projectName, en, store, SavedDataStore.KEY_PROJECT_NAME) {
        save(d.copy(projectName = it))
    }
    SuggestField("Contracts Manager", d.contractsManager, en, store, SavedDataStore.KEY_CONTRACTS_MANAGER) {
        save(d.copy(contractsManager = it))
    }
    SuggestField("Site Supervisor", d.siteSupervisor, en, store, SavedDataStore.KEY_SITE_SUPERVISOR) { value ->
        val previous = d.siteSupervisor
        save(
            d.copy(
                siteSupervisor = value,
                supervisorName = if (d.supervisorName.isBlank() || d.supervisorName == previous) value else d.supervisorName,
                closeSupervisorName = if (d.closeSupervisorName.isBlank() || d.closeSupervisorName == previous) value else d.closeSupervisorName
            )
        )
    }
    Field(
        "Discussion points from previous work day events and today's activities",
        d.discussionPoints,
        en,
        3
    ) { save(d.copy(discussionPoints = it)) }
}

@Composable
private fun Activities(d: DraRecord, en: Boolean, store: SavedDataStore, save: (DraRecord) -> Unit) {
    val context = LocalContext.current
    var pickingFor by remember { mutableStateOf<String?>(null) }
    var savedVersion by remember { mutableIntStateOf(0) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(d.activities, key = { _, a -> a.id }) { i, a ->
            Card {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activity ${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (en) {
                            TextButton(onClick = { pickingFor = a.id }) { Text("Saved") }
                            TextButton(onClick = {
                                store.saveActivity(a)
                                savedVersion++
                                Toast.makeText(context, "Activity saved", Toast.LENGTH_SHORT).show()
                            }) { Text("Save") }
                        }
                    }
                    Field("Planned Activity", a.planned, en, 2) { v ->
                        save(d.copy(activities = d.activities.map { if (it.id == a.id) it.copy(planned = v) else it }))
                    }
                    Field("H&S and Quality Risks", a.risks, en, 2) { v ->
                        save(d.copy(activities = d.activities.map { if (it.id == a.id) it.copy(risks = v) else it }))
                    }
                    Field("Risk Control Measures", a.controls, en, 2) { v ->
                        save(d.copy(activities = d.activities.map { if (it.id == a.id) it.copy(controls = v) else it }))
                    }
                }
            }
        }
        if (en && d.activities.size < 14) {
            item {
                OutlinedButton(
                    onClick = { save(d.copy(activities = d.activities + ActivityItem())) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Activity") }
            }
        }
    }

    pickingFor?.let { activityId ->
        SavedActivityPicker(
            store = store,
            version = savedVersion,
            onDismiss = { pickingFor = null },
            onSelect = { selected ->
                save(
                    d.copy(
                        activities = d.activities.map {
                            if (it.id == activityId) {
                                it.copy(planned = selected.planned, risks = selected.risks, controls = selected.controls)
                            } else it
                        }
                    )
                )
                pickingFor = null
            }
        )
    }
}

@Composable
private fun SavedActivityPicker(
    store: SavedDataStore,
    version: Int,
    onDismiss: () -> Unit,
    onSelect: (SavedActivity) -> Unit
) {
    val saved = remember(version) { store.savedActivities() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved Activities") },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                if (saved.isEmpty()) {
                    Text("No saved activities yet. Use Save on an activity after entering it.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(saved, key = { it.id }) { item ->
                            Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(item.planned.ifBlank { "Saved activity" }, fontWeight = FontWeight.Bold)
                                    if (item.risks.isNotBlank()) Text(item.risks, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                    if (item.controls.isNotBlank()) Text(item.controls, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun Checks(title: String, items: List<CheckItem>, en: Boolean, save: (List<CheckItem>) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        itemsIndexed(items) { i, c ->
            Card {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${i + 1}. ${c.question}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GmesFilterChip(c.answer == true, {
                            if (en) save(items.mapIndexed { x, o -> if (x == i) o.copy(answer = true) else o })
                        }, "Yes")
                        GmesFilterChip(c.answer == false, {
                            if (en) save(items.mapIndexed { x, o -> if (x == i) o.copy(answer = false) else o })
                        }, "No")
                    }
                    Field("Comments${if (c.answer == false) " (required)" else ""}", c.comments, en, 2) { v ->
                        save(items.mapIndexed { x, o -> if (x == i) o.copy(comments = v) else o })
                    }
                }
            }
        }
    }
}

@Composable
private fun Briefing(d: DraRecord, en: Boolean, save: (DraRecord) -> Unit, start: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Supervisor sign-off and briefing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Field("Supervisor Name", d.supervisorName.ifBlank { d.siteSupervisor }, en) {
                save(d.copy(supervisorName = it))
            }
        }
        item {
            Signature("Supervisor Signature", d.supervisorSignatureBase64, en) {
                save(d.copy(supervisorSignatureBase64 = it))
            }
        }
        itemsIndexed(d.attendees, key = { _, a -> a.id }) { i, a ->
            Card {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Attendee ${i + 1}", fontWeight = FontWeight.Bold)
                    Field("Name", a.name, en) { v ->
                        save(d.copy(attendees = d.attendees.map { if (it.id == a.id) it.copy(name = v) else it }))
                    }
                    Signature("Signature", a.signatureBase64, en) { v ->
                        save(d.copy(attendees = d.attendees.map { if (it.id == a.id) it.copy(signatureBase64 = v) else it }))
                    }
                }
            }
        }
        if (en && d.attendees.size < 8) {
            item {
                OutlinedButton(
                    onClick = { save(d.copy(attendees = d.attendees + Attendee())) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Attendee") }
            }
        }
        item {
            if (d.status == STATUS_NOT_STARTED) {
                Button(onClick = start, modifier = Modifier.fillMaxWidth()) { Text("Complete Briefing & Start DRA") }
            } else {
                Text("Started ${displayDateTime(d.startedAt)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EndShift(d: DraRecord, en: Boolean, save: (DraRecord) -> Unit, close: () -> Unit) {
    if (d.status == STATUS_NOT_STARTED) {
        Body { Text("Complete the pre-start briefing first.") }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("End of shift checks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        itemsIndexed(d.endShiftChecks) { i, c ->
            Card {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${i + 1}. ${c.question}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GmesFilterChip(c.answer == true, {
                            if (en) save(d.copy(endShiftChecks = d.endShiftChecks.mapIndexed { x, o -> if (x == i) o.copy(answer = true) else o }))
                        }, "Yes")
                        GmesFilterChip(c.answer == false, {
                            if (en) save(d.copy(endShiftChecks = d.endShiftChecks.mapIndexed { x, o -> if (x == i) o.copy(answer = false) else o }))
                        }, "No")
                    }
                    Field("Comments${if (c.answer == false) " (required)" else ""}", c.comments, en, 2) { v ->
                        save(d.copy(endShiftChecks = d.endShiftChecks.mapIndexed { x, o -> if (x == i) o.copy(comments = v) else o }))
                    }
                }
            }
        }
        item {
            Field("Points for discussion / matters raised / improvements", d.improvements, en, 3) {
                save(d.copy(improvements = it))
            }
        }
        item {
            Field("Supervisor Name", d.closeSupervisorName.ifBlank { d.siteSupervisor }, en) {
                save(d.copy(closeSupervisorName = it))
            }
        }
        item {
            Signature("End of Activity Signature", d.closeSignatureBase64, en) {
                save(d.copy(closeSignatureBase64 = it))
            }
        }
        item {
            if (d.status == STATUS_IN_PROGRESS) {
                Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Close DRA") }
            } else {
                Text("Closed ${displayDateTime(d.closedAt)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GmesFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = GMES_TEAL,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun Signature(title: String, base64: String, en: Boolean, save: (String) -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val strokes = remember { mutableStateListOf<SnapshotStateList<Offset>>() }
    val savedBitmap = remember(base64) {
        decodeSignature(base64)?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    }

    Column {
        Text(title, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.fillMaxWidth().height(130.dp).background(Color.White).onSizeChanged { size = it }
                .pointerInput(en, size) {
                    if (en) {
                        detectDragGestures(
                            onDragStart = { strokes.add(mutableStateListOf(it)) },
                            onDragEnd = {
                                if (strokes.isNotEmpty()) save(sig(strokes.map { it.toList() }, size))
                            },
                            onDrag = { ch, _ -> strokes.lastOrNull()?.add(ch.position) }
                        )
                    }
                }
        ) {
            if (strokes.isEmpty() && savedBitmap != null) {
                Image(
                    bitmap = savedBitmap.asImageBitmap(),
                    contentDescription = "Saved signature",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(5.dp)
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                strokes.forEach { s ->
                    s.zipWithNext().forEach { (a, b) -> drawLine(Color.Black, a, b, 3f) }
                }
            }
            if (strokes.isEmpty() && savedBitmap == null) {
                Text("Sign here", Modifier.align(Alignment.Center), color = Color.Gray)
            }
        }
        if (en) {
            TextButton(onClick = { strokes.clear(); save("") }) { Text("Clear Signature") }
        }
    }
}

private fun sig(strokes: List<List<Offset>>, s: IntSize): String {
    val b = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
    val c = ACanvas(b)
    c.drawColor(AColor.WHITE)
    val p = APaint(APaint.ANTI_ALIAS_FLAG).apply {
        color = AColor.BLACK
        strokeWidth = 7f
        strokeCap = APaint.Cap.ROUND
    }
    val sx = 1000f / s.width.coerceAtLeast(1)
    val sy = 300f / s.height.coerceAtLeast(1)
    strokes.forEach { it.zipWithNext().forEach { (a, z) -> c.drawLine(a.x * sx, a.y * sy, z.x * sx, z.y * sy, p) } }
    val o = ByteArrayOutputStream()
    b.compress(Bitmap.CompressFormat.PNG, 100, o)
    return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
}

@Composable
private fun SavedDataDialog(store: SavedDataStore, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    var editingActivity by remember { mutableStateOf<SavedActivity?>(null) }
    val dataVersion = version

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved DRA Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (editingActivity == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GmesFilterChip(tab == 0, { tab = 0 }, "Details")
                        GmesFilterChip(tab == 1, { tab = 1 }, "Activities")
                    }
                    Spacer(Modifier.height(8.dp))
                    if (tab == 0) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            SavedDataStore.DETAIL_CATEGORIES.forEach { (key, label) ->
                                item { Text(label, fontWeight = FontWeight.Bold, color = GMES_TEAL) }
                                val values = store.values(key)
                                if (values.isEmpty()) {
                                    item { Text("No saved entries", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                                } else {
                                    items(values, key = { "$key:$it:$dataVersion" }) { value ->
                                        SavedValueEditor(
                                            value = value,
                                            onSave = { updated -> store.replaceValue(key, value, updated); version++ },
                                            onDelete = { store.deleteValue(key, value); version++ }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val activities = remember(dataVersion) { store.savedActivities() }
                        if (activities.isEmpty()) {
                            Text("No saved activities yet.", modifier = Modifier.padding(top = 12.dp))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                items(activities, key = { it.id }) { activity ->
                                    Card {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(activity.planned.ifBlank { "Saved activity" }, fontWeight = FontWeight.Bold)
                                            if (activity.risks.isNotBlank()) Text(activity.risks, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                            if (activity.controls.isNotBlank()) Text(activity.controls, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                            Row {
                                                TextButton(onClick = { editingActivity = activity }) { Text("Edit") }
                                                TextButton(onClick = { store.deleteActivity(activity.id); version++ }) { Text("Delete") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val current = editingActivity!!
                    var planned by remember(current.id) { mutableStateOf(current.planned) }
                    var risks by remember(current.id) { mutableStateOf(current.risks) }
                    var controls by remember(current.id) { mutableStateOf(current.controls) }
                    TextButton(onClick = { editingActivity = null }) { Text("← Back to saved activities") }
                    Field("Planned Activity", planned, true, 2) { planned = it }
                    Spacer(Modifier.height(8.dp))
                    Field("H&S and Quality Risks", risks, true, 2) { risks = it }
                    Spacer(Modifier.height(8.dp))
                    Field("Risk Control Measures", controls, true, 3) { controls = it }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            store.updateActivity(current.copy(planned = planned, risks = risks, controls = controls))
                            version++
                            editingActivity = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Changes") }
                    TextButton(
                        onClick = {
                            store.deleteActivity(current.id)
                            version++
                            editingActivity = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete Saved Activity") }
                }
            }
        }
    }
}

@Composable
private fun SavedValueEditor(value: String, onSave: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Card {
        Column(Modifier.padding(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row {
                TextButton(onClick = { onSave(text) }) { Text("Save") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private fun startErrors(d: DraRecord) = buildList {
    if (d.jobNumber.isBlank()) add("Enter Job Number")
    if (d.client.isBlank()) add("Enter Client")
    if (d.projectName.isBlank()) add("Enter Project Name")
    if (d.siteSupervisor.isBlank()) add("Enter Site Supervisor")
    if (d.activities.none { it.planned.isNotBlank() }) add("Add a planned activity")
    if (d.preStartChecks.any { it.answer == null }) add("Answer every pre-start check")
    if (d.preStartChecks.any { it.answer == false && it.comments.isBlank() }) add("Add comments for each No response")
    if (d.supervisorName.ifBlank { d.siteSupervisor }.isBlank() || d.supervisorSignatureBase64.isBlank()) {
        add("Supervisor name and signature are required")
    }
    val a = d.attendees.filter { it.name.isNotBlank() || it.signatureBase64.isNotBlank() }
    if (a.isEmpty() || a.any { it.name.isBlank() || it.signatureBase64.isBlank() }) {
        add("Each briefing attendee needs a name and signature")
    }
}

private fun closeErrors(d: DraRecord) = buildList {
    if (d.endShiftChecks.any { it.answer == null }) add("Answer every end-of-shift check")
    if (d.endShiftChecks.any { it.answer == false && it.comments.isBlank() }) add("Add comments for each No response")
    if (d.closeSupervisorName.ifBlank { d.siteSupervisor }.isBlank() || d.closeSignatureBase64.isBlank()) {
        add("Close-out supervisor name and signature are required")
    }
}
