package com.korleinster.kakaobot

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

// Synchronous commits intentionally persist dispatch gates before external invocation.
@android.annotation.SuppressLint("ApplySharedPref")
class BotPrefs(context: Context, file: String = "bot-settings") {
    private val p = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    val targetRoom: String get() = p.getString("targetRoom", DEFAULT_TARGET_ROOM)!!
    var pairingIssue: String get() = p.getString("pairingIssue", "")!!; set(v) { p.edit().putString("pairingIssue", v).commit() }
    var running: Boolean get() = p.getBoolean("running", false); set(v) { p.edit().putBoolean("running", v).commit() }
    var scheduleEnabled: Boolean get() = p.getBoolean("schedule", false); set(v) { p.edit().putBoolean("schedule", v).commit() }
    var morningEnabled: Boolean get() = p.getBoolean("morning", false); set(v) { p.edit().putBoolean("morning", v).commit() }
    var sendEmpty: Boolean get() = p.getBoolean("empty", false); set(v) { p.edit().putBoolean("empty", v).commit() }
    var bound: String get() = p.getString("bound", "")!!; set(v) { p.edit().putString("bound", v).commit() }
    var candidate: String get() = p.getString("candidate", "")!!; set(v) { p.edit().putString("candidate", v).commit() }
    var collision: Boolean get() = p.getBoolean("collision", false); set(v) { p.edit().putBoolean("collision", v).commit() }
    var code: String get() = p.getString("code", "")!!; set(v) { p.edit().putString("code", v).commit() }
    var codeExpires: Long get() = p.getLong("codeExpires", 0); set(v) { p.edit().putLong("codeExpires", v).commit() }
    var generation: Long get() = p.getLong("generation", 0); set(v) { p.edit().putLong("generation", v).commit() }
    var lastTargetReceivedAt: Long get() = p.getLong("lastTargetReceivedAt", 0); set(v) { p.edit().putLong("lastTargetReceivedAt", v).commit() }
    fun startPairing(room: String, newCode: String, now: Long) {
        val normalized = RoomAdmission.normalizeName(room)
        require(newCode.matches(Regex("[0-9]{6}")))
        check(p.edit().putString("targetRoom", normalized).putBoolean("running", false)
            .putBoolean("schedule", false).putLong("generation", generation + 1)
            .putString("bound", "").putString("candidate", "").putBoolean("collision", false)
            .putString("code", newCode).putLong("codeExpires", now + 300_000)
            .putLong("lastTargetReceivedAt", 0).putString("pairingIssue", "").commit())
    }
}

fun Definition.toJson(): String = JSONObject().apply {
    put("id", id); put("name", name); put("category", category); put("time", time.toString())
    put("weekdays", JSONArray(weekdays.map { it.value })); put("anchor", anchor?.toString() ?: JSONObject.NULL)
    put("intervalMonths", intervalMonths); put("noticeDays", noticeDays); put("enabled", enabled); put("revision", revision)
}.toString()
fun definitionFromJson(text: String): Definition = JSONObject(text).let { j ->
    val days = j.getJSONArray("weekdays")
    Definition(j.getString("id"), j.getString("name"), j.getString("category"), LocalTime.parse(j.getString("time")),
        (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
        if (j.isNull("anchor")) null else LocalDateTime.parse(j.getString("anchor")),
        j.getLong("intervalMonths"), j.getLong("noticeDays"), j.getBoolean("enabled"), j.getInt("revision"))
}

data class Delivery(val notice: Notice, val state: DispatchState, val generation: Long)

class BotDb(context: Context, name: String = "bot.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE definitions(id TEXT PRIMARY KEY, revision INTEGER NOT NULL, enabled INTEGER NOT NULL, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE deliveries(id TEXT PRIMARY KEY, source_id TEXT, revision INTEGER NOT NULL, generation INTEGER NOT NULL, due INTEGER NOT NULL, expires INTEGER NOT NULL, body TEXT NOT NULL, kind TEXT NOT NULL, state TEXT NOT NULL, error TEXT)")
        db.execSQL("CREATE INDEX delivery_due ON deliveries(state,due)")
        db.execSQL("CREATE TABLE observed(id TEXT PRIMARY KEY, seen INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE records(seq INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, code TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun recoverAfterProcessLoss() {
        writableDatabase.execSQL("UPDATE deliveries SET state='DELIVERY_UNKNOWN', error='process_lost_during_dispatch' WHERE state='DISPATCHING'")
        writableDatabase.execSQL("UPDATE deliveries SET state='PENDING' WHERE state='CLAIMED'")
    }
    fun definitions(): List<Definition> = readableDatabase.rawQuery("SELECT json FROM definitions ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(definitionFromJson(c.getString(0))) }
    }
    fun saveDefinition(d: Definition) {
        writableDatabase.insertWithOnConflict("definitions", null, ContentValues().apply {
            put("id", d.id); put("revision", d.revision); put("enabled", if (d.enabled) 1 else 0); put("json", d.toJson())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun removeDefinition(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("definitions", "id=?", arrayOf(id))
            db.execSQL("UPDATE deliveries SET state='CANCELLED' WHERE source_id=? AND state IN ('PENDING','WAITING_SESSION','CLAIMED')", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun sourceValid(n: Notice): Boolean = n.sourceId == null || readableDatabase.rawQuery(
        "SELECT 1 FROM definitions WHERE id=? AND revision=? AND enabled=1", arrayOf(n.sourceId, n.revision.toString())
    ).use { it.moveToFirst() }
    fun observe(id: String, now: Instant): Boolean {
        val db = writableDatabase
        db.delete("observed", "seen<?", arrayOf(now.minus(Duration.ofDays(1)).toEpochMilli().toString()))
        return db.insertWithOnConflict("observed", null, ContentValues().apply { put("id", id); put("seen", now.toEpochMilli()) }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }
    private fun values(n: Notice, gen: Long) = ContentValues().apply {
        put("id", n.id); put("source_id", n.sourceId); put("revision", n.revision); put("generation", gen)
        put("due", n.due.toEpochMilli()); put("expires", n.expires.toEpochMilli()); put("body", n.body); put("kind", n.kind); put("state", "PENDING")
    }
    fun enqueue(n: Notice, gen: Long) {
        writableDatabase.insertWithOnConflict("deliveries", null, values(n, gen), SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun reconcile(notices: List<Notice>, gen: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ids = notices.map { it.id }.toSet()
            val obsolete = db.rawQuery("SELECT id FROM deliveries WHERE kind IN ('REMINDER','BRIEFING') AND state IN ('PENDING','WAITING_SESSION')", null).use { c ->
                buildList { while (c.moveToNext()) if (c.getString(0) !in ids) add(c.getString(0)) }
            }
            obsolete.forEach { db.execSQL("UPDATE deliveries SET state='CANCELLED' WHERE id=?", arrayOf(it)) }
            notices.forEach { n ->
                val v = values(n, gen)
                val inserted = db.insertWithOnConflict("deliveries", null, v, SQLiteDatabase.CONFLICT_IGNORE)
                if (inserted == -1L) db.update("deliveries", v, "id=? AND state IN ('PENDING','WAITING_SESSION','CANCELLED','EXPIRED')", arrayOf(n.id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun read(c: android.database.Cursor): Delivery {
        fun s(key: String) = c.getString(c.getColumnIndexOrThrow(key))
        fun l(key: String) = c.getLong(c.getColumnIndexOrThrow(key))
        val sourceIndex = c.getColumnIndexOrThrow("source_id")
        return Delivery(Notice(s("id"), if (c.isNull(sourceIndex)) null else s("source_id"), l("revision").toInt(), Instant.ofEpochMilli(l("due")), Instant.ofEpochMilli(l("expires")), s("body"), s("kind")), DispatchState.valueOf(s("state")), l("generation"))
    }
    fun find(id: String): Delivery? = readableDatabase.rawQuery("SELECT * FROM deliveries WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) read(it) else null }
    fun due(now: Instant): List<Delivery> = readableDatabase.rawQuery("SELECT * FROM deliveries WHERE state IN ('PENDING','WAITING_SESSION') AND due<=? AND expires>? ORDER BY due LIMIT 30", arrayOf(now.toEpochMilli().toString(), now.toEpochMilli().toString())).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }
    fun claim(id: String, now: Instant, generation: Long): Boolean = writableDatabase.update("deliveries", ContentValues().apply { put("state", "CLAIMED") },
        "id=? AND state IN ('PENDING','WAITING_SESSION') AND due<=? AND expires>? AND generation=?", arrayOf(id, now.toEpochMilli().toString(), now.toEpochMilli().toString(), generation.toString())) == 1
    fun markDispatching(d: Delivery, now: Instant): Boolean {
        if (!sourceValid(d.notice)) { mark(d.notice.id, DispatchState.CANCELLED, "source_changed"); return false }
        return writableDatabase.update("deliveries", ContentValues().apply { put("state", "DISPATCHING") },
            "id=? AND state='CLAIMED' AND generation=? AND expires>? AND (source_id IS NULL OR EXISTS (SELECT 1 FROM definitions d WHERE d.id=deliveries.source_id AND d.revision=deliveries.revision AND d.enabled=1))", arrayOf(d.notice.id, d.generation.toString(), now.toEpochMilli().toString())) == 1
    }
    fun mark(id: String, state: DispatchState, error: String? = null) {
        writableDatabase.update("deliveries", ContentValues().apply { put("state", state.name); put("error", error) }, "id=?", arrayOf(id))
    }
    fun cancelAll() = writableDatabase.execSQL("UPDATE deliveries SET state='CANCELLED' WHERE state IN ('PENDING','WAITING_SESSION','CLAIMED')")
    fun expire(now: Instant) = writableDatabase.execSQL("UPDATE deliveries SET state='EXPIRED' WHERE state IN ('PENDING','WAITING_SESSION') AND expires<=?", arrayOf(now.toEpochMilli()))
    fun nextWake(now: Instant): Long? = readableDatabase.rawQuery("SELECT MIN(CASE WHEN due>? THEN due ELSE expires END) FROM deliveries WHERE state IN ('PENDING','WAITING_SESSION') AND expires>?", arrayOf(now.toEpochMilli().toString(), now.toEpochMilli().toString())).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
    fun log(code: String) {
        val db = writableDatabase
        db.insert("records", null, ContentValues().apply { put("at", System.currentTimeMillis()); put("code", code.take(100)) })
        db.execSQL("DELETE FROM records WHERE seq NOT IN (SELECT seq FROM records ORDER BY seq DESC LIMIT 100)")
    }
    fun logs(): String = readableDatabase.rawQuery("SELECT at,code FROM records ORDER BY seq DESC LIMIT 12", null).use { c ->
        buildList { while (c.moveToNext()) add("${Instant.ofEpochMilli(c.getLong(0)).atZone(GAME_ZONE).toLocalTime().withNano(0)}  ${c.getString(1)}") }.joinToString("\n")
    }
    fun counts(): JSONObject = JSONObject().apply {
        readableDatabase.rawQuery("SELECT state,COUNT(*) FROM deliveries GROUP BY state", null).use { c -> while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
    }
}
