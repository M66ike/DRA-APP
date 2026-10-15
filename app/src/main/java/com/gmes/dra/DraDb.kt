package com.gmes.dra

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DraDb(context: Context) : SQLiteOpenHelper(context, "gmes_dra.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE dra (
                id TEXT PRIMARY KEY,
                date TEXT NOT NULL,
                status TEXT NOT NULL,
                json TEXT NOT NULL,
                updated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(record: DraRecord) {
        val updated = record.copy(updatedAt = System.currentTimeMillis())
        val values = ContentValues().apply {
            put("id", updated.id)
            put("date", updated.date)
            put("status", updated.status)
            put("json", updated.toJson())
            put("updated", updated.updatedAt)
        }
        writableDatabase.insertWithOnConflict("dra", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun get(id: String): DraRecord? {
        readableDatabase.query(
            "dra", arrayOf("json"), "id=?", arrayOf(id), null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) DraRecord.fromJson(cursor.getString(0)) else null
        }
    }

    fun listAll(): List<DraRecord> {
        val results = mutableListOf<DraRecord>()
        readableDatabase.query(
            "dra", arrayOf("json"), null, null, null, null, "date DESC, updated DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { DraRecord.fromJson(cursor.getString(0)) }.getOrNull()?.let(results::add)
            }
        }
        return results
    }

    fun delete(id: String) {
        writableDatabase.delete("dra", "id=?", arrayOf(id))
    }
}
