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
            args.getString("prepare_role")?.let { name -> engine.prefs.role=RoomRole.valueOf(name) }
            engine.beginPair(room!!);prepared=engine;done.countDown()
        }
        assertTrue(done.await(5,TimeUnit.SECONDS))
        val engine=prepared!!
        assertEquals(RoomAdmission.normalizeName(room!!),engine.prefs.targetRoom)
        assertTrue(engine.prefs.code.matches(Regex("[0-9]{6}")))
        assertFalse(engine.prefs.running);assertFalse(engine.prefs.scheduleEnabled)
    }

    /** Explicitly opted-in owner-device UI test. Normal test runs never replace the real binding. */
    @Test fun selectedRoomCreatesCodeAndUpdatesVisibleState() {
        val room = InstrumentationRegistry.getArguments().getString("pair_test_room")
        Assume.assumeTrue("Pass pair_test_room only for an authorized live pairing test", !room.isNullOrBlank())
        val unlocked = !context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked
        val interactive = context.getSystemService(android.os.PowerManager::class.java).isInteractive
        Assume.assumeTrue("Owner must unlock the phone and keep its display on for this foreground UI test", unlocked && interactive)
        val engine=BotEngine.get(context).selected
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<EditText>("target-room-input").setText(room)
                activity.window.decorView.findViewWithTag<Button>("create-pairing-code").performClick()
            }
            val completed=CountDownLatch(1);engine.submit { completed.countDown() }
            assertTrue(completed.await(5,TimeUnit.SECONDS)); instrumentation.waitForIdleSync()
            var before=""
            scenario.onActivity { activity ->
                val details=activity.window.decorView.findViewWithTag<TextView>("connection-details").text.toString()
                assertTrue(details.contains("대상 방: ${RoomAdmission.normalizeName(room!!)}"))
                assertTrue(details.contains("방 연결: 코드 수신 대기"))
                // Listener binding is asynchronous on an instrumented process start.
                assertEquals(engine.statusLabel(),activity.window.decorView.findViewWithTag<TextView>("connection-headline").text.toString())
                before=activity.window.decorView.findViewWithTag<TextView>("pairing-instructions").text.toString()
            }
            // Track the resumed Activity rather than a stale instance after recreation.
            var after=before
            val deadline=android.os.SystemClock.elapsedRealtime()+4000
            while (after==before && android.os.SystemClock.elapsedRealtime()<deadline) {
                Thread.sleep(250)
                scenario.onActivity { activity -> after=activity.window.decorView.findViewWithTag<TextView>("pairing-instructions").text.toString() }
            }
            assertNotEquals(before,after)
        }
        assertEquals(RoomAdmission.normalizeName(room!!),engine.prefs.targetRoom)
        assertFalse(engine.prefs.running); assertFalse(engine.prefs.scheduleEnabled)
    }
}
