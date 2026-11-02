package com.korleinster.kakaobot
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SyncStorageDeviceTest {
    @Test fun upgradesLegacyDatabaseWithoutLosingRemindersOrSendingHistory() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="legacy-sync-${UUID.randomUUID()}.db"
        val file=c.getDatabasePath(name);file.parentFile!!.mkdirs()
        val legacy=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null)
        legacy.execSQL("CREATE TABLE definitions(id TEXT PRIMARY KEY,revision INTEGER NOT NULL,enabled INTEGER NOT NULL,json TEXT NOT NULL)")
        legacy.execSQL("CREATE TABLE deliveries(id TEXT PRIMARY KEY,source_id TEXT,revision INTEGER NOT NULL,generation INTEGER NOT NULL,due INTEGER NOT NULL,expires INTEGER NOT NULL,body TEXT NOT NULL,kind TEXT NOT NULL,state TEXT NOT NULL,error TEXT)")
        legacy.execSQL("CREATE TABLE observed(id TEXT PRIMARY KEY,seen INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE records(seq INTEGER PRIMARY KEY AUTOINCREMENT,at INTEGER NOT NULL,code TEXT NOT NULL)")
        val old=JSONObject(Definition("old","기존 알림","weekly").toJson()).apply {remove("leadMinutes")}
        legacy.execSQL("INSERT INTO definitions VALUES(?,?,?,?)",arrayOf("old",1,1,old.toString()))
        legacy.execSQL("INSERT INTO deliveries VALUES('sent',NULL,0,0,1,2,'기존 기록','COMMAND','REQUEST_ACCEPTED',NULL)")
        legacy.version=1;legacy.close()
        val upgraded=BotDb(c,name)
        try {assertEquals("기존 알림",upgraded.definitions().single().name);assertEquals(5L,upgraded.definitions().single().leadMinutes);assertEquals(1,upgraded.counts()["REQUEST_ACCEPTED"]);assertNull(upgraded.syncPending());assertNull(upgraded.syncConflict())}
        finally{upgraded.close();c.deleteDatabase(name)}
    }
    private fun list(name:String)=JSONObject().put("definitions",JSONArray().put(JSONObject(Definition("entry",name,"weekly").toJson())))
        .put("summary",JSONObject().put("enabled",false).put("time","07:00").put("sendEmpty",false))
    private fun remote(v:Long,name:String)=JSONObject().put("version",v).put("list",list(name))
    private fun useDb(test:(BotDb,String)->Unit) {
        val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="sync-test-${UUID.randomUUID()}.db";val db=BotDb(c,name)
        try {test(db,name)}finally{db.close();c.deleteDatabase(name)}
    }
    @Test fun localQueueSurvivesReopenAndRepeatedPullCannotCancelSchedules()=useDb {db,name->
        assertTrue(db.acceptSync(remote(1,"server")))
        val now=Instant.now();db.enqueue(Notice("pending","entry",1,now,now.plusSeconds(60),"test","REMINDER"),0)
        assertFalse(db.acceptSync(remote(1,"server")));assertEquals(1,db.due(now).size)
        db.saveDefinition(Definition("entry","offline","weekly"));db.queueSync(list("offline"))
        val reopened=BotDb(InstrumentationRegistry.getInstrumentation().targetContext,name)
        try {assertEquals("offline",reopened.definitions().single().name);assertEquals(1L,reopened.syncPending()!!.getLong("expectedVersion"))}finally{reopened.close()}
    }
    @Test fun responseDuringNewerEditCannotEraseLocalWork()=useDb {db,_->
        db.acceptSync(remote(1,"initial"));db.queueSync(list("first"));val sent=db.syncPending()!!.getString("mutationId")
        db.saveDefinition(Definition("entry","second","weekly"));db.queueSync(list("second"));val second=db.syncPending()!!.getString("mutationId")
        assertFalse(db.acceptSync(remote(2,"first"),sent));assertEquals("second",db.definitions().single().name)
        assertEquals(second,db.syncPending()!!.getString("mutationId"));assertEquals(2L,db.syncPending()!!.getLong("expectedVersion"))
        assertFalse(db.acceptSync(remote(3,"other writer")));assertNotNull(db.syncConflict());assertEquals("second",db.definitions().single().name)
        db.resolveRemote();assertEquals("other writer",db.definitions().single().name);assertNull(db.syncPending());assertNull(db.syncConflict())
    }
    @Test fun explicitLocalResolutionRebasesAndAcknowledgesOnlyItsOwnMutation()=useDb {db,_->
        db.acceptSync(remote(1,"initial"));db.queueSync(list("local"));db.setSyncConflict(remote(2,"web"));db.resolveLocal()
        val pending=db.syncPending()!!;assertEquals(2L,pending.getLong("expectedVersion"));assertNull(db.syncConflict())
        assertTrue(db.acceptSync(remote(3,"local"),pending.getString("mutationId")));assertNull(db.syncPending());assertEquals(3L,db.webVersion())
        db.queueSync(list("private"));db.setSyncConflict(remote(4,"remote"));db.clearAll();assertNull(db.syncPending());assertNull(db.syncConflict())
    }
    @Test fun malformedRemoteCannotEraseQueuedOfflineChanges()=useDb {db,_->
        db.acceptSync(remote(1,"initial"));db.queueSync(list("offline"))
        val queued=db.syncPending()!!.toString()
        val bad=remote(2,"invalid");val defs=bad.getJSONObject("list").getJSONArray("definitions");defs.put(defs.getJSONObject(0))
        try {db.acceptSync(bad);fail("Duplicate remote IDs accepted")}catch(_:IllegalArgumentException){}
        assertEquals(queued,db.syncPending()!!.toString());assertEquals("initial",db.definitions().single().name);assertEquals(1L,db.webVersion())
    }
    @Test fun remoteUpdateWhileAnEditorIsOpenRequiresAnExplicitChoice()=useDb {db,_->
        db.acceptSync(remote(1,"opened editor"));val editorBase=db.webVersion()
        db.acceptSync(remote(2,"new remote value"))
        db.saveDefinition(Definition("entry","local editor value","weekly"));db.queueSync(list("local editor value"),editorBase)
        assertEquals(1L,db.syncPending()!!.getLong("expectedVersion"))
        assertFalse(db.acceptSync(remote(2,"new remote value")))
        assertEquals("local editor value",db.definitions().single().name);assertNotNull(db.syncConflict())
    }
}
