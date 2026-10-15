package com.gmes.dra

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedActivity(
    val id: String = UUID.randomUUID().toString(),
    val planned: String = "",
    val risks: String = "",
    val controls: String = ""
)

class SavedDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("gmes_dra_saved_data", Context.MODE_PRIVATE)

    fun seedFromRecords(records: List<DraRecord>) {
        if (prefs.getBoolean("seeded_from_existing_dras_v11", false)) return
        records.sortedBy { it.updatedAt }.forEach(::rememberRecord)
        prefs.edit().putBoolean("seeded_from_existing_dras_v11", true).apply()
    }

    fun rememberRecord(record: DraRecord) {
        addValue(KEY_JOB_NUMBER, record.jobNumber)
        addValue(KEY_CLIENT, record.client)
        addValue(KEY_PROJECT_NAME, record.projectName)
        addValue(KEY_CONTRACTS_MANAGER, record.contractsManager)
        addValue(KEY_SITE_SUPERVISOR, record.siteSupervisor)
    }

    fun values(key: String): List<String> = readStringArray(key)

    fun last(key: String): String = values(key).firstOrNull().orEmpty()

    fun addValue(key: String, raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        val items = values(key).filterNot { it.equals(value, ignoreCase = true) }.toMutableList()
        items.add(0, value)
        writeStringArray(key, items.take(60))
    }

    fun replaceValue(key: String, oldValue: String, newValue: String) {
        val newText = newValue.trim()
        if (newText.isBlank()) {
            deleteValue(key, oldValue)
            return
        }
        val items = values(key).map { if (it == oldValue) newText else it }
            .distinctBy { it.lowercase() }
        writeStringArray(key, items)
    }

    fun deleteValue(key: String, value: String) {
        writeStringArray(key, values(key).filterNot { it == value })
    }

    fun savedActivities(): List<SavedActivity> {
        val raw = prefs.getString(KEY_SAVED_ACTIVITIES, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedActivity(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    planned = o.optString("planned"),
                    risks = o.optString("risks"),
                    controls = o.optString("controls")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveActivity(item: ActivityItem) {
        val planned = item.planned.trim()
        val risks = item.risks.trim()
        val controls = item.controls.trim()
        if (planned.isBlank() && risks.isBlank() && controls.isBlank()) return
        val existing = savedActivities().filterNot {
            it.planned.equals(planned, true) && it.risks.equals(risks, true) && it.controls.equals(controls, true)
        }.toMutableList()
        existing.add(0, SavedActivity(planned = planned, risks = risks, controls = controls))
        writeActivities(existing.take(60))
    }

    fun updateActivity(updated: SavedActivity) {
        writeActivities(savedActivities().map { if (it.id == updated.id) updated else it })
    }

    fun deleteActivity(id: String) {
        writeActivities(savedActivities().filterNot { it.id == id })
    }

    private fun readStringArray(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }

    private fun writeStringArray(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }

    private fun writeActivities(items: List<SavedActivity>) {
        val arr = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("planned", item.planned)
                    put("risks", item.risks)
                    put("controls", item.controls)
                })
            }
        }
        prefs.edit().putString(KEY_SAVED_ACTIVITIES, arr.toString()).apply()
    }

    companion object {
        const val KEY_JOB_NUMBER = "job_number"
        const val KEY_CLIENT = "client"
        const val KEY_PROJECT_NAME = "project_name"
        const val KEY_CONTRACTS_MANAGER = "contracts_manager"
        const val KEY_SITE_SUPERVISOR = "site_supervisor"
        private const val KEY_SAVED_ACTIVITIES = "saved_activities"

        val DETAIL_CATEGORIES = listOf(
            KEY_JOB_NUMBER to "Job Number",
            KEY_CLIENT to "Client",
            KEY_PROJECT_NAME to "Project Name",
            KEY_CONTRACTS_MANAGER to "Contracts Manager",
            KEY_SITE_SUPERVISOR to "Site Supervisor"
        )
    }
}
