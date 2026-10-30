package com.korleinster.kakaobot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MultiRoomStorageDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix="disposable-multi-${UUID.randomUUID()}"
    private lateinit var a:BotDb
    private lateinit var b:BotDb
    @Before fun setup() { a=BotDb(context,"$prefix-a.db");b=BotDb(context,"$prefix-b.db") }
    @After fun cleanup() {
        a.close();b.close();context.deleteDatabase("$prefix-a.db");context.deleteDatabase("$prefix-b.db")
        listOf("$prefix-directory","$prefix-a","$prefix-b").forEach { context.deleteSharedPreferences(it) }
    }
    @Test fun registryKeepsLegacyAndPersistsIndependentProfiles() {
        val directory=RoomDirectory(context,"$prefix-directory")
        assertEquals(listOf("legacy"),directory.ids())
        val id=directory.add()
        val reopened=RoomDirectory(context,"$prefix-directory")
        assertEquals(listOf("legacy",id),reopened.ids());assertEquals(id,reopened.selected)
        reopened.selected="legacy";assertEquals("legacy",directory.selected)
    }
    @Test fun pairingAndRoleChangeInOneProfileCannotResetAnother() {
        val first=BotPrefs(context,"$prefix-a");val second=BotPrefs(context,"$prefix-b")
        first.bound="chat-a";first.running=true;first.scheduleEnabled=true;first.features=setOf(BotFeature.REPEATING_REMINDERS,BotFeature.CYCLE_REMINDERS)
        second.startPairing("別の試験部屋","123456",1000);second.features=setOf(BotFeature.DICE,BotFeature.DRAW)
        assertEquals("chat-a",first.bound);assertTrue(first.running);assertTrue(first.scheduleEnabled)
        assertEquals(setOf(BotFeature.REPEATING_REMINDERS,BotFeature.CYCLE_REMINDERS),first.features);assertEquals(setOf(BotFeature.DICE,BotFeature.DRAW),BotPrefs(context,"$prefix-b").features)
    }
    @Test fun identicalDeliveryIdsAndCancellationRemainInsideTheirRoom() {
        val now=Instant.now()
        val n=Notice("same-occurrence",null,0,now.minusSeconds(1),now.plusSeconds(300),"synthetic", "TEST")
        a.enqueue(n,1);b.enqueue(n,1);a.cancelAll()
        assertEquals(DispatchState.CANCELLED,a.find(n.id)!!.state)
        assertEquals(DispatchState.PENDING,b.find(n.id)!!.state)
        assertTrue(b.claim(n.id,Instant.now(),1))
        assertTrue(a.observe("same-event",now));assertTrue(b.observe("same-event",now))
    }
    @Test fun scheduleAndHistoryCannotLeakBetweenRoomDatabases() {
        a.saveDefinition(Definition("same-id","A 일정","content",LocalTime.NOON))
        b.saveDefinition(Definition("same-id","B 일정","content",LocalTime.NOON))
        a.log("A-only-record");b.log("B-only-record");a.removeDefinition("same-id")
        assertTrue(a.definitions().isEmpty());assertEquals("B 일정",b.definitions().single().name)
        assertFalse(a.logs().contains("B-only"));assertFalse(b.logs().contains("A-only"))
    }
}
