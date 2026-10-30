package com.korleinster.kakaobot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProductStorageDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix="disposable-product-${UUID.randomUUID()}"
    private lateinit var db:BotDb
    @Before fun setup() { db=BotDb(context,"$prefix.db") }
    @After fun cleanup() { db.close();context.deleteDatabase("$prefix.db");listOf("prefs","consent","directory").forEach { context.deleteSharedPreferences("$prefix-$it") } }
    @Test fun legacyFeaturesMigrateAndExplicitEmptySelectionStaysEmpty() {
        context.getSharedPreferences("$prefix-prefs",0).edit().putString("role","FUN").putBoolean("morning",true).commit()
        val p=BotPrefs(context,"$prefix-prefs")
        assertEquals(setOf(BotFeature.DICE,BotFeature.DRAW),p.features)
        p.features=emptySet()
        assertTrue(BotPrefs(context,"$prefix-prefs").features.isEmpty())
    }
    @Test fun savingToneAndFeaturesPreservesBindingAndCanDisableAllScheduledFunctions() {
        val p=BotPrefs(context,"$prefix-prefs");p.bound="synthetic-chat";p.running=true;p.scheduleEnabled=true
        p.saveConfiguration(setOf(BotFeature.DICE),BotTone.ELDER)
        val reopened=BotPrefs(context,"$prefix-prefs")
        assertEquals("synthetic-chat",reopened.bound);assertTrue(reopened.running);assertFalse(reopened.scheduleEnabled)
        assertEquals(setOf(BotFeature.DICE),reopened.features);assertEquals(BotTone.ELDER,reopened.tone)
    }
    @Test fun consentRequiresExplicitAcceptanceAndCanBeRevoked() {
        val c=AppConsent(context,"$prefix-consent");assertFalse(c.accepted)
        c.accept();assertTrue(AppConsent(context,"$prefix-consent").accepted)
        c.revoke();assertFalse(AppConsent(context,"$prefix-consent").accepted)
    }
    @Test fun removingAllProfilesDoesNotResurrectLegacyRoomOnReopen() {
        val dir=RoomDirectory(context,"$prefix-directory")
        val id=dir.add();dir.remove("legacy")
        assertEquals(listOf(id),dir.ids());dir.remove(id)
        val reopened=RoomDirectory(context,"$prefix-directory")
        assertTrue(reopened.ids().isEmpty());assertNull(reopened.selected)
        assertEquals(1,reopened.add().let { reopened.ids().size })
    }
    @Test fun deletionAndRetentionRemoveStoredContentsWhileKeepingCurrentWork() {
        val now=Instant.now();val old=now.minus(Duration.ofDays(8))
        db.saveDefinition(Definition("source","기억할 일","weekly"))
        db.enqueue(Notice("old",null,0,old.minusSeconds(1),old,"synthetic-old-body","TEST"),1);db.mark("old",DispatchState.REQUEST_ACCEPTED)
        db.enqueue(Notice("future",null,0,now.plusSeconds(60),now.plusSeconds(120),"synthetic-future-body","TEST"),1)
        db.observe("old-input-hash",old);db.log("synthetic-record")
        db.prune(now)
        assertNull(db.find("old"));assertNotNull(db.find("future"))
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM observed",null).use { assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0)) }
        db.clearAll();assertTrue(db.definitions().isEmpty());assertNull(db.find("future"));assertEquals("",db.logs())
    }
    @Test fun oldScheduleJsonReceivesDefaultLeadWithoutChangingItsAnchor() {
        val d=Definition("old-source","예전 알림","collection",anchor=LocalDateTime.of(2026,1,31,9,0),intervalMonths=4)
        val j=JSONObject(d.toJson());j.remove("leadMinutes")
        val restored=definitionFromJson(j.toString())
        assertEquals(5L,restored.leadMinutes);assertEquals(d.anchor,restored.anchor);assertEquals(d.intervalMonths,restored.intervalMonths)
    }
}
