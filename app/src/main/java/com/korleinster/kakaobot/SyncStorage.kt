package com.korleinster.kakaobot

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

object SyncEndpoint {
    fun normalize(raw: String): String {
        val u=URI(raw.trim())
        require(u.scheme=="https" && !u.host.isNullOrBlank() && u.rawUserInfo==null && u.rawQuery==null && u.rawFragment==null)
        require(u.port==-1 || u.port in 1..65535)
        require(!u.rawPath.contains("..") && !u.rawPath.contains('%'))
        val base=u.toASCIIString().trimEnd('/')
        return if(u.path.isNullOrEmpty() || u.path=="/") "$base/api/chatbot" else base
    }
}

fun sameJson(a:Any?,b:Any?):Boolean = when {
    a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() && a.keys().asSequence().all { sameJson(a.get(it),b.get(it)) }
    a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all { sameJson(a.get(it),b.get(it)) }
    a is Number && b is Number -> a.toDouble()==b.toDouble()
    else -> a==b
}
fun BotDb.syncPending():JSONObject? = readableDatabase.rawQuery("SELECT pending FROM sync_queue WHERE singleton=1",null).use { if(it.moveToFirst() && !it.isNull(0)) JSONObject(it.getString(0)) else null }
fun BotDb.syncConflict():JSONObject? = readableDatabase.rawQuery("SELECT conflict FROM sync_queue WHERE singleton=1",null).use { if(it.moveToFirst() && !it.isNull(0)) JSONObject(it.getString(0)) else null }
private fun BotDb.pending(value:JSONObject?) {
    writableDatabase.execSQL("INSERT OR IGNORE INTO sync_queue(singleton) VALUES(1)")
    writableDatabase.execSQL("UPDATE sync_queue SET pending=? WHERE singleton=1",arrayOf(value?.toString()))
}
fun BotDb.setSyncConflict(value:JSONObject?) {
    writableDatabase.execSQL("INSERT OR IGNORE INTO sync_queue(singleton) VALUES(1)")
    writableDatabase.execSQL("UPDATE sync_queue SET conflict=? WHERE singleton=1",arrayOf(value?.toString()))
}
fun BotDb.setLocalSummary(value:JSONObject, version:Long=webVersion()) {
    writableDatabase.insertWithOnConflict("web_list",null,ContentValues().apply {put("singleton",1);put("version",version);put("summary",value.toString())},android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
}
fun BotEngine.currentReminderList():JSONObject = JSONObject().put("definitions",JSONArray(db.definitions().map {JSONObject(it.toJson())}))
    .put("summary",db.webSummary() ?: JSONObject().put("enabled",BotFeature.DAILY_SUMMARY in prefs.features).put("time",prefs.summaryTime.toString()).put("sendEmpty",prefs.sendEmpty))
fun BotDb.queueSync(list:JSONObject, base:Long=webVersion()) { pending(JSONObject().put("expectedVersion",base).put("mutationId",UUID.randomUUID().toString()).put("list",list)) }
fun BotEngine.editReminderList(baseVersion:Long?=null,action:()->Unit) {
    db.writableDatabase.beginTransaction()
    try {action();if(prefs.webToken.isNotBlank()) db.queueSync(currentReminderList(),baseVersion ?: db.webVersion());db.writableDatabase.setTransactionSuccessful()}
    finally {db.writableDatabase.endTransaction()}
    changedSettings();if(prefs.webToken.isNotBlank()) ListDelivery.sync(this)
}
/** Recheck the durable queue after I/O, so an in-flight response cannot erase a newer edit. */
fun BotDb.acceptSync(snapshot:JSONObject,sentMutation:String?=null):Boolean {
    val version=snapshot.getLong("version");require(version>=0)
    val parsed=WebList.parse(JSONObject().put("version",version+1).put("list",snapshot.getJSONObject("list")))
    val sql=writableDatabase;sql.beginTransaction()
    try {
        val queued=syncPending()
        if(queued!=null && queued.optString("mutationId")!=sentMutation && !sameJson(queued.getJSONObject("list"),snapshot.getJSONObject("list"))) {
            if(sentMutation!=null) {
                queued.put("expectedVersion",version);pending(queued)
                setLocalSummary(webSummary() ?: parsed.summary,version)
            } else if(version!=queued.getLong("expectedVersion")) setSyncConflict(snapshot)
            sql.setTransactionSuccessful();return false
        }
        if(version<webVersion() || (version==webVersion() && queued==null && webSummary()!=null)) {sql.setTransactionSuccessful();return false}
        sql.delete("definitions",null,null);parsed.definitions.forEach {saveDefinition(it)}
        setLocalSummary(parsed.summary,version);pending(null);setSyncConflict(null)
        sql.execSQL("UPDATE deliveries SET state='CANCELLED' WHERE kind IN ('REMINDER','BRIEFING') AND state IN ('PENDING','WAITING_SESSION','CLAIMED')")
        sql.setTransactionSuccessful();return true
    } finally {sql.endTransaction()}
}
fun BotDb.resolveLocal() {
    val remote=syncConflict() ?: return;val local=syncPending() ?: return
    writableDatabase.beginTransaction()
    try {queueSync(local.getJSONObject("list"),remote.getLong("version"));setSyncConflict(null);writableDatabase.setTransactionSuccessful()}
    finally {writableDatabase.endTransaction()}
}
fun BotDb.resolveRemote() { val remote=syncConflict() ?: return;acceptSync(remote,syncPending()?.getString("mutationId")) }
