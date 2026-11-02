package com.korleinster.kakaobot

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class WebListDeviceTest {
    @Test fun replacementIsDurableCancelsOldSchedulesAndIsIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "web-list-test-${UUID.randomUUID()}.db"
        val db = BotDb(context, name)
        try {
            val old = Definition("old", "기존 알림", "weekly", LocalTime.NOON)
            db.saveDefinition(old)
            val now = Instant.now()
            db.enqueue(Notice("old-notice", old.id, 1, now, now.plusSeconds(60), "기존", "REMINDER"), 0)
            val summary = JSONObject().put("enabled", true).put("time", "08:00").put("sendEmpty", false)
            val fresh = old.copy(id="new",name="웹 알림")
            assertTrue(db.applyWebList(1, listOf(fresh), summary))
            assertEquals(listOf(fresh), db.definitions())
            assertTrue(db.due(now).isEmpty())
            assertEquals(1L, db.webVersion())
            assertEquals("08:00", db.webSummary()!!.getString("time"))
            assertFalse(db.applyWebList(1, emptyList(), summary))
            assertEquals(listOf(fresh), db.definitions())
            assertTrue(db.applyWebList(2, emptyList(), summary))
            assertTrue(db.definitions().isEmpty())
            assertFalse(db.applyWebList(1, listOf(old), summary))
            val reopened=BotDb(context,name)
            try { assertEquals(2L,reopened.webVersion());assertTrue(reopened.definitions().isEmpty());assertEquals("08:00",reopened.webSummary()!!.getString("time")) }
            finally { reopened.close() }
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun invalidReplacementKeepsPreviousList() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "web-list-test-${UUID.randomUUID()}.db";val db=BotDb(context,name)
        try {
            val old=Definition("old","기존","weekly");db.saveDefinition(old)
            try { db.applyWebList(1,listOf(old,old),JSONObject());fail("Duplicate IDs must be rejected") } catch(_:IllegalArgumentException) { }
            assertEquals(listOf(old),db.definitions());assertEquals(0L,db.webVersion())
        } finally { db.close();context.deleteDatabase(name) }
    }
}
