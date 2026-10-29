package com.korleinster.kakaobot

import android.app.*
import android.content.*
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.time.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArraySet

class BotApp : Application() { override fun onCreate() { super.onCreate(); BotEngine.get(this) } }

class BotEngine private constructor(private val context: Application) {
    companion object {
        @Volatile private var instance: BotEngine? = null
        fun get(context: Context): BotEngine = instance ?: synchronized(this) { instance ?: BotEngine(context.applicationContext as Application).also { instance = it } }
    }
    val prefs = BotPrefs(context)
    val db = BotDb(context)
    val transport = SessionTransport(context)
    val versionName: String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    private val statusListeners = CopyOnWriteArraySet<() -> Unit>()
    private val executor = Executors.newSingleThreadExecutor()
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val alarmIntent: PendingIntent get() = PendingIntent.getBroadcast(context, 1, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    init { db.recoverAfterProcessLoss(); submit { tick() } }
    fun submit(block: () -> Unit) { executor.execute { try { block() } catch (_: Exception) { db.log("작업 오류 · 개인정보 미기록"); report() } } }
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
    fun addStatusListener(listener: () -> Unit) { statusListeners.add(listener) }
    fun removeStatusListener(listener: () -> Unit) { statusListeners.remove(listener) }
    fun requestListenerConnection() {
        if (notificationAccessGranted() && !NotificationBridge.connected) {
            android.service.notification.NotificationListenerService.requestRebind(ComponentName(context, NotificationBridge::class.java))
        }
    }
    fun beginPair(room: String = prefs.targetRoom) {
        prefs.startPairing(room, pairingCode(), System.currentTimeMillis())
        db.cancelAll(); transport.clear(); db.log("대상 방 연결 코드 생성"); tick()
    }
    fun approvePair() {
        if (prefs.candidate.isBlank() || !transport.available(prefs.candidate)) { db.log("방 알림을 먼저 받아야 함"); report(); return }
        prefs.bound = prefs.candidate; prefs.candidate = ""; prefs.code = ""; prefs.codeExpires = 0
        prefs.collision = false; db.log("대상 방 연결 확인 완료"); tick()
    }
    fun pairingReplyUnavailable(expectedCode: String, diagnostics: String, reason: String) {
        if (expectedCode.isBlank() || prefs.code != expectedCode || System.currentTimeMillis() >= prefs.codeExpires) return
        prefs.pairingIssue = reason
        prefs.lastTargetReceivedAt = System.currentTimeMillis()
        db.log("연결 코드 수신 · 답장 준비 실패"); db.log(diagnostics); report()
    }
    fun changedSettings() { prefs.generation += 1; db.cancelAll(); tick() }
    fun onTargetMessage(identity: String, eventId: String, text: String, cap: ReplyCapability, processCommand: Boolean) {
        val now = Instant.now()
        if (processCommand && prefs.code.isNotBlank() && System.currentTimeMillis() < prefs.codeExpires && text.trim() == "!연결 ${prefs.code}") {
            if (prefs.candidate.isNotBlank() && prefs.candidate != identity) { prefs.collision = true; db.log("동일 이름 방 식별 충돌"); report(); return }
            transport.accept(cap); prefs.candidate = identity; prefs.code = ""; prefs.pairingIssue = ""; prefs.lastTargetReceivedAt = System.currentTimeMillis()
            db.log("연결 후보 수신 · 앱에서 확인 필요"); report(); return
        }
        if (prefs.bound.isBlank()) return
        if (identity != prefs.bound) { prefs.collision = true; transport.clear(); db.log("방 식별 충돌 · 발송 차단"); report(); return }
        transport.accept(cap)
        if (!processCommand) { tick(); return }
        prefs.lastTargetReceivedAt = System.currentTimeMillis()
        if (!prefs.running || prefs.collision || !db.observe(eventId, now)) { report(); return }
        val command = text.trim()
        val response = when (command) {
            "!오늘", "!월보", "!정기", "!컬렉션" -> scheduleReply(command, now)
            "!알림테스트" -> {
                if (!exactAllowed()) "앱에서 정시 알림 권한을 먼저 허용해줘." else {
                    val due = now.plusSeconds(30)
                    db.enqueue(Notice("test:$eventId", null, 0, due, due.plusSeconds(60), "[시험 알림] 예약한 시험 알림이야. 화면이 꺼진 상태에서도 도착하는지 확인해줘.", "TEST"), prefs.generation)
                    "30초 뒤 연결한 방에 시험 알림을 보낼게."
                }
            }
            else -> GameCommands.reply(command)
        }
        if (response != null) {
            db.enqueue(Notice("command:$eventId", null, 0, now, now.plusSeconds(60), response, "COMMAND"), prefs.generation)
            db.log("대상 방 명령 처리")
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
        db.enqueue(Notice("test:${UUID.randomUUID()}", null, 0, due, due.plusSeconds(60), "[시험 알림] 앱에서 예약한 30초 시험 알림", "TEST"), prefs.generation); tick()
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
    fun notificationAccessGranted(): Boolean {
        val component = ComponentName(context, NotificationBridge::class.java)
        return if (Build.VERSION.SDK_INT >= 27) context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        else Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    }
    fun connectionState(): ConnectionState = ConnectionState(notificationAccessGranted(), NotificationBridge.connected,
        prefs.bound.isNotBlank(), prefs.candidate.isNotBlank(), prefs.running, prefs.collision,
        prefs.code, prefs.codeExpires, transport.available(prefs.bound), transport.check(prefs.bound), prefs.pairingIssue)
    fun statusLabel(): String = connectionState().phase(System.currentTimeMillis()).label
    fun connectionDetails(): String {
        val now = System.currentTimeMillis(); val state = connectionState()
        val roomState = when {
            state.collision -> "식별 충돌 · 재연결 필요"
            state.bound -> "확인됨"
            state.candidate -> "코드 수신됨 · 아래에서 연결 확인"
            state.pairingIssue.isNotBlank() && now < state.codeExpires -> "코드 수신됨 · 답장 기능 확보 필요"
            state.code.isNotBlank() && now < state.codeExpires -> "코드 수신 대기 · ${state.remainingSeconds(now)}초 남음"
            state.code.isNotBlank() -> "코드 만료 · 다시 만들기"
            else -> "연결 안 됨"
        }
        val replyState = when {
            !state.bound && state.pairingIssue.isNotBlank() && now < state.codeExpires -> state.pairingIssue
            !state.bound -> "방 연결 확인 필요"
            !state.hasReply -> "새 대상 방 알림 필요"
            state.replyBlock != null -> state.replyBlock
            transport.sourcePresent(prefs.bound) -> "준비됨 · 최근 알림 기준"
            else -> "최근 답장 정보 보관 중 · 새 알림으로 갱신 가능"
        }
        val last = prefs.lastTargetReceivedAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(GAME_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) } ?: "아직 없음"
        return "알림 접근: ${if(state.accessGranted) "허용됨" else "허용 필요"}\n" +
            "알림 수신 연결: ${if(state.listenerConnected) "연결됨" else "연결 대기"}\n" +
            "대상 방: ${prefs.targetRoom}\n방 연결: $roomState\n답장 준비: $replyState\n" +
            "명령 응답: ${if(state.running) "켜짐" else "꺼짐"}\n마지막 방 알림: $last"
    }
    fun report() {
        val state = connectionState(); val now = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("app_version", versionName); put("target_room", prefs.targetRoom); put("status", state.phase(now).label)
            put("notification_access_granted", state.accessGranted); put("connection_phase", state.phase(now).name)
            put("pairing_issue", prefs.pairingIssue)
            put("pairing_remaining_seconds", state.remainingSeconds(now)); put("last_target_received_at", prefs.lastTargetReceivedAt)
            put("running", prefs.running); put("schedule_enabled", prefs.scheduleEnabled); put("notification_connected", NotificationBridge.connected)
            put("bound", prefs.bound.isNotBlank()); put("candidate", prefs.candidate.isNotBlank()); put("pairing_command", if (prefs.code.isNotBlank() && System.currentTimeMillis() < prefs.codeExpires) "!연결 ${prefs.code}" else JSONObject.NULL)
            put("session_available", transport.available(prefs.bound)); put("identity_collision", prefs.collision); put("exact_alarm_allowed", exactAllowed())
            put("counts", db.counts()); put("updated_at", Instant.now().toString())
        }
        val target = File(context.filesDir, "status.json"); val temp = File(context.filesDir, "status.tmp")
        temp.writeText(json.toString(2)); if (!temp.renameTo(target)) { target.writeText(json.toString(2)); temp.delete() }
        statusListeners.forEach { it() }
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
