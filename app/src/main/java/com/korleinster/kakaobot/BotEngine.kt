package com.korleinster.kakaobot

import android.app.*
import android.content.*
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.time.*
import java.util.UUID
import java.util.concurrent.Executors

class BotApp : Application() { override fun onCreate() { super.onCreate(); BotEngine.get(this) } }

class BotEngine private constructor(private val context: Application) {
    companion object {
        @Volatile private var instance: BotEngine? = null
        fun get(context: Context): BotEngine = instance ?: synchronized(this) { instance ?: BotEngine(context.applicationContext as Application).also { instance = it } }
    }
    val prefs = BotPrefs(context)
    val db = BotDb(context)
    val transport = SessionTransport(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val alarmIntent: PendingIntent get() = PendingIntent.getBroadcast(context, 1, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    init { db.recoverAfterProcessLoss(); submit { tick() } }
    fun submit(block: () -> Unit) { executor.execute { try { block() } catch (_: Exception) { db.log("작업 오류 · 개인정보 미기록"); report() } } }
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    fun beginPair() {
        prefs.running = false; prefs.scheduleEnabled = false; prefs.generation += 1; db.cancelAll()
        prefs.bound = ""; prefs.candidate = ""; prefs.collision = false
        prefs.code = pairingCode(); prefs.codeExpires = System.currentTimeMillis() + 300_000
        transport.clear(); db.log("시험 방 연결 코드 생성"); tick()
    }
    fun approvePair() {
        if (prefs.candidate.isBlank() || !transport.available(prefs.candidate)) { db.log("방 알림을 먼저 받아야 함"); report(); return }
        prefs.bound = prefs.candidate; prefs.candidate = ""; prefs.code = ""; prefs.codeExpires = 0
        prefs.collision = false; db.log("봇테스트 방 연결 확인 완료"); tick()
    }
    fun changedSettings() { prefs.generation += 1; db.cancelAll(); tick() }
    fun onTargetMessage(identity: String, eventId: String, text: String, cap: ReplyCapability, processCommand: Boolean) {
        val now = Instant.now()
        if (processCommand && prefs.code.isNotBlank() && System.currentTimeMillis() < prefs.codeExpires && text.trim() == "!연결 ${prefs.code}") {
            if (prefs.candidate.isNotBlank() && prefs.candidate != identity) { prefs.collision = true; db.log("동일 이름 방 식별 충돌"); report(); return }
            transport.accept(cap); prefs.candidate = identity; prefs.code = ""; db.log("연결 후보 수신 · 앱에서 확인 필요"); report(); return
        }
        if (prefs.bound.isBlank()) return
        if (identity != prefs.bound) { prefs.collision = true; transport.clear(); db.log("방 식별 충돌 · 발송 차단"); report(); return }
        transport.accept(cap)
        if (!processCommand) { tick(); return }
        if (!prefs.running || prefs.collision || !db.observe(eventId, now)) { report(); return }
        val command = text.trim()
        val response = when (command) {
            "!오늘", "!월보", "!정기", "!컬렉션" -> scheduleReply(command, now)
            "!알림테스트" -> {
                if (!exactAllowed()) "앱에서 정시 알림 권한을 먼저 허용해줘." else {
                    val due = now.plusSeconds(30)
                    db.enqueue(Notice("test:$eventId", null, 0, due, due.plusSeconds(60), "[봇테스트] 예약한 시험 알림이야. 화면이 꺼진 상태에서도 도착하는지 확인해줘.", "TEST"), prefs.generation)
                    "30초 뒤 봇테스트 방에 시험 알림을 보낼게."
                }
            }
            else -> GameCommands.reply(command)
        }
        if (response != null) {
            db.enqueue(Notice("command:$eventId", null, 0, now, now.plusSeconds(60), response, "COMMAND"), prefs.generation)
            db.log("시험 방 명령 처리")
        }
        tick()
    }
    private fun scheduleReply(command: String, now: Instant): String {
        val category = when(command) { "!월보" -> "world_boss"; "!정기" -> "content"; "!컬렉션" -> "collection"; else -> null }
        val defs = db.definitions().filter { it.enabled && (category == null || it.category == category) }
        if (defs.isEmpty()) return if (category == "collection") "아직 컬렉션 기준일·주기가 등록되지 않았어." else "아직 확인된 일정이 등록되지 않았어."
        val today = now.atZone(GAME_ZONE).toLocalDate()
        val events = Planner.occurrences(defs, now).filter { it.start >= now && (category == "collection" || it.start.atZone(GAME_ZONE).toLocalDate() == today) }.take(15)
        return if (events.isEmpty()) "오늘 남은 등록 일정이 없어." else "[등록 일정 · 한국 시간]\n" + events.joinToString("\n") { "${it.start.atZone(GAME_ZONE).toLocalDate()} ${it.start.atZone(GAME_ZONE).toLocalTime()} ${it.source.name}" }
    }
    fun localTest() {
        if (!prefs.running || prefs.bound.isBlank() || prefs.collision) { db.log("봇 실행과 방 연결 확인 필요"); report(); return }
        if (!exactAllowed()) { db.log("정시 알림 권한 필요"); report(); return }
        val due = Instant.now().plusSeconds(30)
        db.enqueue(Notice("test:${UUID.randomUUID()}", null, 0, due, due.plusSeconds(60), "[봇테스트] 앱에서 예약한 30초 시험 알림", "TEST"), prefs.generation); tick()
    }
    fun tick() {
        val now = Instant.now()
        db.expire(now)
        val planned = if (prefs.running && prefs.scheduleEnabled && prefs.bound.isNotBlank()) Planner.plan(db.definitions(), now, prefs.morningEnabled, prefs.sendEmpty).map { it.copy(id="${prefs.bound.take(16)}:${it.id}") } else emptyList()
        db.reconcile(planned, prefs.generation)
        if (prefs.running && prefs.bound.isNotBlank() && !prefs.collision) {
            db.due(now).forEach { d ->
                if (d.generation != prefs.generation || !db.sourceValid(d.notice)) { db.mark(d.notice.id, DispatchState.CANCELLED); return@forEach }
                if (d.notice.kind in setOf("TEST", "REMINDER", "BRIEFING") && !exactAllowed()) { db.mark(d.notice.id, DispatchState.WAITING_SESSION, "exact_permission_required"); return@forEach }
                val unavailable = transport.check(prefs.bound)
                if (unavailable != null) { db.mark(d.notice.id, DispatchState.WAITING_SESSION, unavailable); return@forEach }
                val at = Instant.now()
                if (!db.claim(d.notice.id, at, prefs.generation)) return@forEach
                if (!prefs.running || prefs.collision || !db.markDispatching(d, Instant.now())) return@forEach
                val body = if (d.notice.kind == "REMINDER" && db.definitions().firstOrNull { it.id == d.notice.sourceId }?.category != "collection") {
                    val seconds = Duration.between(Instant.now(), d.notice.due.plusSeconds(300)).seconds.coerceAtLeast(0)
                    d.notice.body + "\n남은 시간: ${seconds / 60}분 ${seconds % 60}초"
                } else d.notice.body
                try {
                    val accepted = transport.invoke(prefs.bound, body)
                    db.mark(d.notice.id, if (accepted) DispatchState.REQUEST_ACCEPTED else DispatchState.FAILED_PRE_SEND)
                    db.log(if (accepted) "카카오톡 전송 요청 수락 · 도착 확인은 별도" else "전송 결과 불명 · 자동 재전송 없음")
                } catch (_: Exception) {
                    db.mark(d.notice.id, DispatchState.DELIVERY_UNKNOWN, "invoke_exception")
                    db.log("전송 결과 불명 · 자동 재전송 없음")
                }
            }
        }
        alarm.cancel(alarmIntent)
        if (prefs.running && exactAllowed()) db.nextWake(Instant.now())?.let { next ->
            try { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarmIntent) } catch (_: SecurityException) { db.log("정시 알림 권한 필요") }
        }
        report()
    }
    fun statusLabel(): String = when {
        !NotificationBridge.connected -> "알림 접근 연결 필요"
        prefs.collision -> "같은 이름의 방 충돌 · 발송 차단"
        prefs.bound.isBlank() && prefs.candidate.isNotBlank() -> "방 후보 수신 · 연결 확인 버튼 필요"
        prefs.bound.isBlank() -> "방 연결 대기"
        !prefs.running -> "봇 일시정지"
        !transport.available(prefs.bound) -> "새 방 알림 대기"
        else -> "봇테스트 연결 · 실행 중"
    }
    fun report() {
        val json = JSONObject().apply {
            put("app_version", "0.1.0-test"); put("target_room", TEST_ROOM); put("status", statusLabel())
            put("running", prefs.running); put("schedule_enabled", prefs.scheduleEnabled); put("notification_connected", NotificationBridge.connected)
            put("bound", prefs.bound.isNotBlank()); put("candidate", prefs.candidate.isNotBlank()); put("pairing_command", if (prefs.code.isNotBlank() && System.currentTimeMillis() < prefs.codeExpires) "!연결 ${prefs.code}" else JSONObject.NULL)
            put("session_available", transport.available(prefs.bound)); put("identity_collision", prefs.collision); put("exact_alarm_allowed", exactAllowed())
            put("counts", db.counts()); put("updated_at", Instant.now().toString())
        }
        val target = File(context.filesDir, "status.json"); val temp = File(context.filesDir, "status.tmp")
        temp.writeText(json.toString(2)); if (!temp.renameTo(target)) { target.writeText(json.toString(2)); temp.delete() }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { val result = goAsync(); BotEngine.get(context).submit { try { BotEngine.get(context).tick() } finally { result.finish() } } }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result = goAsync(); BotEngine.get(context).submit { try { BotEngine.get(context).tick() } finally { result.finish() } }
    }
}
