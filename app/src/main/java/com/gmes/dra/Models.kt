package com.gmes.dra

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ActivityItem(
    val id: String = UUID.randomUUID().toString(),
    val planned: String = "",
    val risks: String = "",
    val controls: String = ""
)

data class CheckItem(
    val question: String,
    val answer: Boolean? = null,
    val comments: String = ""
)

data class Attendee(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val signatureBase64: String = ""
)

data class DraRecord(
    val id: String = UUID.randomUUID().toString(),
    val formReference: String = "FM_047_8",
    val date: String = todayIso(),
    val status: String = STATUS_NOT_STARTED,
    val jobNumber: String = "",
    val client: String = "",
    val projectName: String = "",
    val contractsManager: String = "",
    val siteSupervisor: String = "",
    val discussionPoints: String = "",
    val activities: List<ActivityItem> = listOf(ActivityItem()),
    val preStartChecks: List<CheckItem> = defaultPreStartChecks(),
    val supervisorName: String = "",
    val supervisorSignatureBase64: String = "",
    val attendees: List<Attendee> = listOf(Attendee()),
    val startedAt: Long? = null,
    val endShiftChecks: List<CheckItem> = defaultEndShiftChecks(),
    val improvements: String = "",
    val closeSupervisorName: String = "",
    val closeSignatureBase64: String = "",
    val closedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun referenceLabel(): String = formReferenceLabel()

    fun formReferenceLabel(): String {
        val name = projectName.trim().ifBlank { jobNumber.trim().ifBlank { "Job Name" } }
        val code = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(date)
            SimpleDateFormat("yyMMdd", Locale.UK).format(parsed ?: Date())
        } catch (_: Exception) {
            date.replace("-", "").takeLast(6)
        }
        return "$code - $name"
    }

    fun exportFileBaseName(): String {
        val name = projectName.trim().ifBlank { jobNumber.trim().ifBlank { "DRA" } }
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
        val code = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(date)
            SimpleDateFormat("yyMMdd", Locale.UK).format(parsed ?: Date())
        } catch (_: Exception) {
            date.replace("-", "").takeLast(6)
        }
        return "${code}_${name.ifBlank { "DRA" }}"
    }

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("formReference", formReference)
        put("date", date)
        put("status", status)
        put("jobNumber", jobNumber)
        put("client", client)
        put("projectName", projectName)
        put("contractsManager", contractsManager)
        put("siteSupervisor", siteSupervisor)
        put("discussionPoints", discussionPoints)
        put("activities", JSONArray().apply {
            activities.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("planned", item.planned)
                    put("risks", item.risks)
                    put("controls", item.controls)
                })
            }
        })
        put("preStartChecks", checksToJson(preStartChecks))
        put("supervisorName", supervisorName)
        put("supervisorSignatureBase64", supervisorSignatureBase64)
        put("attendees", JSONArray().apply {
            attendees.forEach { person ->
                put(JSONObject().apply {
                    put("id", person.id)
                    put("name", person.name)
                    put("signatureBase64", person.signatureBase64)
                })
            }
        })
        put("startedAt", startedAt ?: JSONObject.NULL)
        put("endShiftChecks", checksToJson(endShiftChecks))
        put("improvements", improvements)
        put("closeSupervisorName", closeSupervisorName)
        put("closeSignatureBase64", closeSignatureBase64)
        put("closedAt", closedAt ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }.toString()

    companion object {
        fun fromJson(raw: String): DraRecord {
            val o = JSONObject(raw)
            val activities = o.optJSONArray("activities")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val a = arr.getJSONObject(i)
                    ActivityItem(
                        id = a.optString("id", UUID.randomUUID().toString()),
                        planned = a.optString("planned"),
                        risks = a.optString("risks"),
                        controls = a.optString("controls")
                    )
                }
            }.orEmpty().ifEmpty { listOf(ActivityItem()) }

            val attendees = o.optJSONArray("attendees")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val a = arr.getJSONObject(i)
                    Attendee(
                        id = a.optString("id", UUID.randomUUID().toString()),
                        name = a.optString("name"),
                        signatureBase64 = a.optString("signatureBase64")
                    )
                }
            }.orEmpty().ifEmpty { listOf(Attendee()) }

            return DraRecord(
                id = o.optString("id", UUID.randomUUID().toString()),
                formReference = o.optString("formReference", "FM_047_8"),
                date = o.optString("date", todayIso()),
                status = o.optString("status", STATUS_NOT_STARTED),
                jobNumber = o.optString("jobNumber"),
                client = o.optString("client"),
                projectName = o.optString("projectName"),
                contractsManager = o.optString("contractsManager"),
                siteSupervisor = o.optString("siteSupervisor"),
                discussionPoints = o.optString("discussionPoints"),
                activities = activities,
                preStartChecks = jsonToChecks(o.optJSONArray("preStartChecks"), defaultPreStartChecks()),
                supervisorName = o.optString("supervisorName"),
                supervisorSignatureBase64 = o.optString("supervisorSignatureBase64"),
                attendees = attendees,
                startedAt = o.optLongOrNull("startedAt"),
                endShiftChecks = jsonToChecks(o.optJSONArray("endShiftChecks"), defaultEndShiftChecks()),
                improvements = o.optString("improvements"),
                closeSupervisorName = o.optString("closeSupervisorName"),
                closeSignatureBase64 = o.optString("closeSignatureBase64"),
                closedAt = o.optLongOrNull("closedAt"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

private fun checksToJson(items: List<CheckItem>) = JSONArray().apply {
    items.forEach { item ->
        put(JSONObject().apply {
            put("question", item.question)
            when (item.answer) {
                true -> put("answer", true)
                false -> put("answer", false)
                null -> put("answer", JSONObject.NULL)
            }
            put("comments", item.comments)
        })
    }
}

private fun jsonToChecks(arr: JSONArray?, defaults: List<CheckItem>): List<CheckItem> {
    if (arr == null || arr.length() == 0) return defaults
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        CheckItem(
            question = o.optString("question", defaults.getOrNull(i)?.question.orEmpty()),
            answer = if (o.isNull("answer")) null else o.optBoolean("answer"),
            comments = o.optString("comments")
        )
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
fun displayDate(iso: String): String = try {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(iso)
    SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(input ?: Date())
} catch (_: Exception) { iso }
fun displayDateTime(epoch: Long?): String = if (epoch == null) "" else
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.UK).format(Date(epoch))
fun displayTime(epoch: Long?): String = if (epoch == null) "" else
    SimpleDateFormat("HH:mm", Locale.UK).format(Date(epoch))

fun decodeSignature(base64: String): ByteArray? = try {
    if (base64.isBlank()) null else Base64.decode(base64, Base64.DEFAULT)
} catch (_: Exception) { null }

const val STATUS_NOT_STARTED = "Not Started"
const val STATUS_IN_PROGRESS = "In Progress"
const val STATUS_CLOSED = "Closed"

fun defaultPreStartChecks() = listOf(
    CheckItem("Have all operatives clocked in via uAttend and any other site specific signing in process?"),
    CheckItem("Have all operatives read, understood and signed the latest health and safety documentation?"),
    CheckItem("Are all tools and equipment being used in good condition and, if applicable, within calibration date with certificates available?"),
    CheckItem("Has all access equipment been confirmed as suitable for the task, with all required pre-use inspections completed?"),
    CheckItem("Do the work areas have safe access and adequate lighting, with barriers available if needed to cordon off the area from other trades?"),
    CheckItem("Is all required PPE available, being used and in good condition as per site requirements? (This includes GMES 10 CAL uniform.)"),
    CheckItem("Are all circuits safely and securely isolated and proven dead before working on, with access to voltage indicators/proving units and LOTO kits for all engineers?")
)

fun defaultEndShiftChecks() = listOf(
    CheckItem("Are all work areas cleared of hazards, debris or materials, with barriers removed or secured if no longer needed?"),
    CheckItem("Are all tools and equipment in good condition, with any damaged or faulty items reported to the manager or supervisor?"),
    CheckItem("Has all access equipment been safely stored and inspected for damage?"),
    CheckItem("Have all operatives clocked out via uAttend and any other site specific signing out process?")
)
