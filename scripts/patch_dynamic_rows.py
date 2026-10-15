from pathlib import Path

path = Path("app/src/main/java/com/gmes/dra/MainActivity.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        'if (a.isEmpty() || a.any { it.name.isBlank() || it.signatureBase64.isBlank() }) {\n        add("Each briefing attendee needs a name and signature")\n    }',
        'if (a.any { it.name.isBlank() || it.signatureBase64.isBlank() }) {\n        add("Each added briefing attendee needs a name and signature")\n    }'
    ),
    (
        'if (en) {\n                            TextButton(onClick = { pickingFor = a.id }) { Text("Saved") }',
        'if (en) {\n                            TextButton(onClick = { save(d.copy(activities = d.activities.filterNot { it.id == a.id })) }) { Text("Remove") }\n                            TextButton(onClick = { pickingFor = a.id }) { Text("Saved") }'
    ),
    (
        'Text("Attendee ${i + 1}", fontWeight = FontWeight.Bold)\n                    Field("Name", a.name, en) { v ->',
        'Row(verticalAlignment = Alignment.CenterVertically) {\n                        Text("Attendee ${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))\n                        if (en) {\n                            TextButton(onClick = { save(d.copy(attendees = d.attendees.filterNot { it.id == a.id })) }) { Text("Remove") }\n                        }\n                    }\n                    Field("Name", a.name, en) { v ->'
    ),
]

for old, new in replacements:
    if old not in text:
        raise SystemExit(f"Expected source block not found:\n{old}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Applied dynamic activity/attendee row patch")
