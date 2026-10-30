package com.korleinster.kakaobot

import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ConnectionSettingsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var file: String
    @Before fun setup() { file = "disposable-settings-${UUID.randomUUID()}" }
    @After fun cleanup() { context.deleteSharedPreferences(file) }

    @Test fun roomSwitchClearsOldBindingAndSendingGatesButKeepsSchedulePreferences() {
        val p = BotPrefs(context, file)
        p.bound="old-room"; p.candidate="old-candidate"; p.running=true
        p.scheduleEnabled=true; p.morningEnabled=true; p.collision=true; p.lastTargetReceivedAt=100; p.pairingIssue="old failure"
        p.startPairing(" 새 시험방 ", "123456", 1000)
        val restored = BotPrefs(context, file)
        assertEquals("새 시험방", restored.targetRoom)
        assertEquals("", restored.bound); assertEquals("", restored.candidate)
        assertFalse(restored.running); assertFalse(restored.scheduleEnabled); assertFalse(restored.collision)
        assertTrue(restored.morningEnabled)
        assertEquals(0L, restored.lastTargetReceivedAt); assertEquals(1L, restored.generation)
        assertEquals(301_000L, restored.codeExpires)
        assertEquals("",restored.pairingIssue)
    }

    @Test fun invalidRoomCannotClearAnExistingBinding() {
        val p=BotPrefs(context,file); p.bound="old-room"; p.running=true
        try { p.startPairing(" ","123456",1000); fail("Blank room accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("old-room",p.bound); assertTrue(p.running)
    }
    @Test fun kakaoPackageMustBeVisibleForLiveOwnerDeviceTests() {
        Assume.assumeTrue("Requires the owner's KakaoTalk device", InstrumentationRegistry.getArguments().containsKey("pair_test_room"))
        val info=context.packageManager.getApplicationInfo("com.kakao.talk",0)
        assertTrue(info.uid>0)
    }

    /** Debug-only owner-device preparation; no exported production control endpoint. */
    @Test fun prepareAuthorizedPairingWithoutScreenAccess() {
        val room=InstrumentationRegistry.getArguments().getString("prepare_pairing_room")
        Assume.assumeTrue("Explicit owner-device preparation only", !room.isNullOrBlank())
        assertTrue(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val done=CountDownLatch(1);val root=BotEngine.get(context)
        val args=InstrumentationRegistry.getArguments()
        var prepared:BotEngine?=null
        root.submit {
            val engine=if(args.getString("prepare_new_room")=="true") root.addRoom() else root.selected
            engine.requestListenerConnection()
            args.getString("prepare_role")?.let { name -> engine.prefs.features=FeaturePolicy.migrateRole(name,false) }
            engine.beginPair(room!!);prepared=engine;done.countDown()
        }
        assertTrue(done.await(5,TimeUnit.SECONDS))
        val engine=prepared!!
        assertEquals(RoomAdmission.normalizeName(room!!),engine.prefs.targetRoom)
        assertTrue(engine.prefs.code.matches(Regex("[0-9]{6}")))
        assertFalse(engine.prefs.running);assertFalse(engine.prefs.scheduleEnabled)
    }

    /** Opt-in owner test configuration; keeps pairing and all other rooms intact. */
    @Test fun configureAuthorizedOwnerRoomWithoutChangingBinding() {
        val args=InstrumentationRegistry.getArguments()
        val id=args.getString("owner_test_room")
        Assume.assumeTrue("Explicit owner-room test setup only",!id.isNullOrBlank())
        assertTrue(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val root=BotEngine.get(context);assertTrue(root.consentAccepted())
        val room=root.rooms().single { it.roomId==id }
        val before=room.prefs.bound
        val features=args.getString("owner_features")!!.split(',').filter { it.isNotBlank() }.map { BotFeature.valueOf(it) }.toSet()
        val tone=BotTone.valueOf(args.getString("owner_tone")!!)
        val done=CountDownLatch(1)
        root.submit {
            room.saveConfiguration(features,tone)
            args.getString("owner_schedule_enabled")?.let { value ->
                require(value=="true" || value=="false")
                room.prefs.scheduleEnabled=value.toBoolean();room.changedSettings()
            }
            done.countDown()
        }
        assertTrue(done.await(5,TimeUnit.SECONDS));assertEquals(before,room.prefs.bound)
        assertEquals(features,room.prefs.features);assertEquals(tone,room.prefs.tone)
        args.getString("owner_schedule_enabled")?.let { assertEquals(it.toBoolean(),room.prefs.scheduleEnabled) }
    }

    /** Explicit foreground check using a disposable profile, preserving existing rooms. */
    @Test fun ownerDeviceShowsFeatureCheckboxesAndTonePreview() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("owner_ui_test")=="true")
        Assume.assumeFalse(context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)
        Assume.assumeTrue(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
        val root=BotEngine.get(context)
        Assume.assumeTrue("Owner must consent in the app before this UI test",root.consentAccepted())
        val original=root.selectedOrNull?.roomId
        val before=root.rooms().associate { it.roomId to listOf(it.prefs.bound,it.prefs.running.toString(),it.prefs.features.toString(),it.prefs.tone.name) }
        var temp:BotEngine?=null
        fun work(action:()->Unit) { val done=CountDownLatch(1);root.submit { action();done.countDown() };assertTrue(done.await(5,TimeUnit.SECONDS)) }
        work { temp=root.addRoom();temp!!.prefs.features=emptySet() }
        val room=temp!!
        try {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
                scenario.onActivity { it.window.decorView.findViewWithTag<Button>("open-room-${room.roomId}").performClick() }
                instrumentation.waitForIdleSync()
                val deadline=android.os.SystemClock.elapsedRealtime()+5000
                var ready=false
                while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
                    scenario.onActivity { ready=it.window.decorView.findViewWithTag<android.widget.CheckBox>("feature-DICE")!=null }
                    if(!ready) Thread.sleep(100)
                }
                assertTrue(ready)
                scenario.onActivity { activity ->
                    val view=activity.window.decorView
                    BotFeature.entries.forEach { assertNotNull(view.findViewWithTag<android.widget.CheckBox>("feature-${it.name}")) }
                    BotTone.entries.forEach { assertNotNull(view.findViewWithTag<android.widget.RadioButton>("tone-${it.name}")) }
                    val preview=view.findViewWithTag<TextView>("tone-preview")
                    val previous=preview.text.toString()
                    view.findViewWithTag<android.widget.RadioButton>("tone-GIRLFRIEND").performClick()
                    assertNotEquals(previous,preview.text.toString())
                    view.findViewWithTag<android.widget.CheckBox>("feature-DICE").performClick()
                    assertTrue(room.prefs.features.isEmpty())
                    view.findViewWithTag<Button>("save-room-preferences").performClick()
                }
                work { };instrumentation.waitForIdleSync()
                assertEquals(setOf(BotFeature.DICE),room.prefs.features);assertEquals(BotTone.GIRLFRIEND,room.prefs.tone)
                before.forEach { (id,value) -> val current=root.rooms().first { it.roomId==id };assertEquals(value,listOf(current.prefs.bound,current.prefs.running.toString(),current.prefs.features.toString(),current.prefs.tone.name)) }
            }
        } finally { work { root.removeRoom(room.roomId);if(original!=null) root.selectRoom(original) } }
    }
}
