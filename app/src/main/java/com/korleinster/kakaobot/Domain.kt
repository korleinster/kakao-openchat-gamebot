package com.korleinster.kakaobot

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.*
import java.text.Normalizer

const val DEFAULT_TARGET_ROOM = "새 채팅방"
val APP_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

enum class BotFeature(val label: String, val description: String) {
    REPEATING_REMINDERS("반복 알림", "선택한 요일과 시각에 알림"),
    CYCLE_REMINDERS("주기 알림", "기준일부터 몇 개월마다 알림"),
    DAILY_SUMMARY("아침 요약", "매일 지정한 시각에 오늘의 일정 안내"),
    DICE("주사위", "주사위를 굴려 무작위 숫자 선택"),
    DRAW("뽑기", "입력한 후보 중 한 명 선택")
}

object FeaturePolicy {
    val defaultFeatures = setOf(BotFeature.REPEATING_REMINDERS, BotFeature.CYCLE_REMINDERS, BotFeature.DICE, BotFeature.DRAW)
    fun migrateRole(role: String, morning: Boolean): Set<BotFeature> {
        val result = when(role) {
            "FUN" -> setOf(BotFeature.DICE, BotFeature.DRAW)
            "GAME_ALERTS" -> setOf(BotFeature.REPEATING_REMINDERS, BotFeature.CYCLE_REMINDERS)
            else -> defaultFeatures
        }
        return if(morning && role != "FUN") result + BotFeature.DAILY_SUMMARY else result
    }
    fun hasAlerts(features: Set<BotFeature>): Boolean = features.any { it in setOf(BotFeature.REPEATING_REMINDERS, BotFeature.CYCLE_REMINDERS, BotFeature.DAILY_SUMMARY) }
    fun token(command: String): String = command.trim().split(Regex("\\s+"), limit=2).first()
    fun allows(features: Set<BotFeature>, command: String): Boolean = when(token(command)) {
        "!핑", "!도움말" -> true
        "!오늘", "!알림", "!알림테스트" -> hasAlerts(features)
        "!월보", "!정기" -> BotFeature.REPEATING_REMINDERS in features
        "!주기", "!컬렉션" -> BotFeature.CYCLE_REMINDERS in features
        "!주사위" -> BotFeature.DICE in features
        "!뽑기" -> BotFeature.DRAW in features
        else -> false
    }
    fun help(features: Set<BotFeature>): String = buildList {
        add("!핑 · !도움말")
        if(hasAlerts(features)) add("!오늘 · !알림 · !알림테스트")
        if(BotFeature.CYCLE_REMINDERS in features) add("!주기")
        if(BotFeature.DICE in features) add("!주사위 [최댓값]")
        if(BotFeature.DRAW in features) add("!뽑기 후보1 후보2 ...")
    }.joinToString("\n")
}

object RoomRouting {
    /** Conversation identity, never display name or the room selected in the UI. */
    fun owner(identity: String, bindings: List<Pair<String, String>>): String? =
        bindings.filter { it.second.isNotBlank() && it.second == identity }.singleOrNull()?.first
    fun ambiguous(identity: String, bindings: List<Pair<String, String>>): Boolean =
        bindings.count { it.second.isNotBlank() && it.second == identity } > 1
}

object RoomAdmission {
    fun normalizeName(name: String): String = Normalizer.normalize(name.trim(), Normalizer.Form.NFC).also {
        require(it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl))
    }
    fun namedTarget(names: List<String>, target: String): Boolean = names.any {
        Normalizer.normalize(it.trim(), Normalizer.Form.NFC) == Normalizer.normalize(target.trim(), Normalizer.Form.NFC)
    }
    fun knownIdentity(identity: String, bound: String, candidate: String): Boolean =
        (bound.isNotBlank() && identity == bound) || (candidate.isNotBlank() && identity == candidate)
    fun pairingEligible(processCommand: Boolean, stableIdentity: Boolean, code: String,
                        expires: Long, now: Long, explicitRoomMismatch: Boolean = false): Boolean =
        processCommand && stableIdentity && !explicitRoomMismatch &&
            code.matches(Regex("[0-9]{6}")) && now < expires
    fun currentPairingPost(processCommand: Boolean, postTime: Long, expires: Long, now: Long): Boolean =
        processCommand || (now < expires && postTime >= expires - 300_000 && postTime <= now)
    fun matchesCode(text: String?, code: String): Boolean =
        code.matches(Regex("[0-9]{6}")) && text?.trim() == "!연결 $code"
}

object ReplyInputPolicy {
    fun usable(freeForm: Boolean, dataOnly: Boolean, resultKey: String): Boolean =
        freeForm && !dataOnly && resultKey.isNotBlank()
}

enum class ConnectionPhase(val label: String) {
    CONSENT_REQUIRED("알림 사용 동의 필요"), ACCESS_REQUIRED("알림 접근 허용 필요"), LISTENER_WAITING("알림 수신 연결 대기"),
    COLLISION("방 식별 충돌 · 발송 중지"), CANDIDATE("연결 코드 수신 · 앱에서 확인 필요"),
    PAIRING("연결 코드 수신 대기"), REPLY_UNAVAILABLE("코드 수신됨 · 답장 기능 확인 필요"), EXPIRED("연결 코드 만료 · 다시 만들기"),
    UNBOUND("대상 방 연결 필요"), PAUSED("방 연결 확인됨 · 응답 꺼짐"),
    SESSION_WAITING("새 대상 방 알림 대기"), BLOCKED("답장 준비에 필요한 조치 확인"),
    READY("답장 준비됨 · 응답 실행 중")
}

data class ConnectionState(
    val accessGranted: Boolean, val listenerConnected: Boolean,
    val bound: Boolean, val candidate: Boolean, val running: Boolean, val collision: Boolean,
    val code: String, val codeExpires: Long, val hasReply: Boolean, val replyBlock: String?, val pairingIssue: String = "", val consented: Boolean = true
) {
    fun phase(now: Long): ConnectionPhase = when {
        !consented -> ConnectionPhase.CONSENT_REQUIRED
        !accessGranted -> ConnectionPhase.ACCESS_REQUIRED
        !listenerConnected -> ConnectionPhase.LISTENER_WAITING
        collision -> ConnectionPhase.COLLISION
        !bound && candidate -> ConnectionPhase.CANDIDATE
        !bound && code.isNotBlank() && now < codeExpires && pairingIssue.isNotBlank() -> ConnectionPhase.REPLY_UNAVAILABLE
        !bound && code.isNotBlank() && now < codeExpires -> ConnectionPhase.PAIRING
        !bound && code.isNotBlank() -> ConnectionPhase.EXPIRED
        !bound -> ConnectionPhase.UNBOUND
        !running -> ConnectionPhase.PAUSED
        !hasReply -> ConnectionPhase.SESSION_WAITING
        replyBlock != null -> ConnectionPhase.BLOCKED
        else -> ConnectionPhase.READY
    }
    fun remainingSeconds(now: Long): Long = if (code.isBlank()) 0 else ((codeExpires - now + 999) / 1000).coerceAtLeast(0)
}

data class Definition(
    val id: String,
    val name: String,
    val category: String,
    val time: LocalTime = LocalTime.NOON,
    val weekdays: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val anchor: LocalDateTime? = null,
    val intervalMonths: Long = 0,
    val noticeDays: Long = 0,
    val enabled: Boolean = true,
    val revision: Int = 1,
    val leadMinutes: Long = 5
) {
    val isMonthly: Boolean get() = category in setOf("monthly", "collection")
    init {
        require(name.isNotBlank() && name.length <= 80)
        require(category in setOf("weekly", "monthly", "world_boss", "content", "collection"))
        require(leadMinutes in 0..1440)
        if (isMonthly) {
            require(anchor != null && intervalMonths in 1..24 && noticeDays in 0..90)
        } else require(weekdays.isNotEmpty())
    }
}

data class Occurrence(val key: String, val source: Definition, val start: Instant)
data class Notice(
    val id: String, val sourceId: String?, val revision: Int,
    val due: Instant, val expires: Instant, val body: String, val kind: String
)

object Planner {
    fun occurrences(defs: List<Definition>, now: Instant, days: Long = 8): List<Occurrence> {
        val today = now.atZone(APP_ZONE).toLocalDate()
        return defs.filter { it.enabled }.flatMap { d ->
            if (d.isMonthly) {
                val anchor = d.anchor!!
                val difference = java.time.temporal.ChronoUnit.MONTHS.between(
                    java.time.YearMonth.from(anchor), java.time.YearMonth.from(today))
                val middle = (difference / d.intervalMonths).coerceAtLeast(0)
                val margin = (d.noticeDays / 28 / d.intervalMonths + 3)
                ((middle - 2).coerceAtLeast(0)..(middle + margin)).map { n ->
                    Occurrence("${d.id}:cycle:$n", d, anchor.plusMonths(n * d.intervalMonths).atZone(APP_ZONE).toInstant())
                }
            } else {
                (-1L..days).mapNotNull { offset ->
                    val date = today.plusDays(offset)
                    if (date.dayOfWeek in d.weekdays) Occurrence("${d.id}:$date", d, date.atTime(d.time).atZone(APP_ZONE).toInstant()) else null
                }
            }
        }.sortedBy { it.start }
    }

    fun plan(defs: List<Definition>, now: Instant, morning: Boolean, sendEmpty: Boolean = false, briefingTime: LocalTime = LocalTime.of(7,0)): List<Notice> {
        val events = occurrences(defs, now)
        val horizon = now.plus(Duration.ofDays(8))
        val notices = events.map { o ->
            val collection = o.source.isMonthly
            val due = if (collection) o.start.minus(Duration.ofDays(o.source.noticeDays)) else o.start.minusSeconds(o.source.leadMinutes * 60)
            val expiry = if (collection) due.plusSeconds(600) else if(o.source.leadMinutes == 0L) due.plusSeconds(60) else minOf(due.plusSeconds(60), o.start)
            val label = if (collection) "주기 알림" else "반복 알림"
            val start = o.start.atZone(APP_ZONE)
            val body = if (collection) "[$label] ${o.source.name}\n예정: ${start.toLocalDate()} ${start.toLocalTime()} (한국 시간)" else
                "[$label] ${o.source.name}\n시작: ${start.toLocalTime()} (한국 시간)"
            Notice("${o.key}:reminder", o.source.id, o.source.revision, due, expiry, body, "REMINDER")
        }.filter { it.expires > now }
        if (!morning) return notices
        val today = now.atZone(APP_ZONE).toLocalDate()
        val briefings = (0L..8L).mapNotNull { n ->
            val date = today.plusDays(n)
            val due = date.atTime(briefingTime).atZone(APP_ZONE).toInstant()
            val relevant = events.filter { !it.source.isMonthly && it.start.atZone(APP_ZONE).toLocalDate() == date && it.start >= maxOf(now, due) }
            if (due.plusSeconds(600) <= now || due > horizon || (!sendEmpty && relevant.isEmpty())) null else {
                val lines = relevant.map { "${it.start.atZone(APP_ZONE).toLocalTime()} ${it.source.name}" }
                val text = "[오늘의 일정 · $date]\n" + (if (lines.isEmpty()) "등록된 일정 없음" else lines.joinToString("\n"))
                Notice("briefing:$date", null, 0, due, due.plusSeconds(600), text, "BRIEFING")
            }
        }
        return notices + briefings
    }
}

enum class DispatchState { PENDING, WAITING_SESSION, CLAIMED, DISPATCHING, REQUEST_ACCEPTED, DELIVERY_UNKNOWN, FAILED_PRE_SEND, EXPIRED, CANCELLED }
object DispatchRules {
    fun canClaim(state: DispatchState, due: Instant, expires: Instant, now: Instant): Boolean =
        state in setOf(DispatchState.PENDING, DispatchState.WAITING_SESSION) && due <= now && now < expires
    fun afterProcessLoss(state: DispatchState): DispatchState = when (state) {
        DispatchState.DISPATCHING -> DispatchState.DELIVERY_UNKNOWN
        DispatchState.CLAIMED -> DispatchState.PENDING
        else -> state
    }
}

fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
fun pairingCode(random: SecureRandom = SecureRandom()): String = "%06d".format(random.nextInt(1_000_000))

object GameCommands {
    fun reply(text: String, random: SecureRandom = SecureRandom()): String? {
        val parts = text.trim().split(Regex("\\s+"))
        return when (parts.firstOrNull()) {
            "!핑" -> "퐁! 연결 정상"
            "!도움말" -> "사용할 수 있는 명령\n!핑 · !오늘 · !알림 · !주기\n!주사위 [최댓값] · !뽑기 이름1 이름2 ...\n!알림테스트 (30초 뒤 시험 알림)"
            "!주사위" -> {
                val maximum = if (parts.size == 1) 6 else parts.getOrNull(1)?.toIntOrNull()
                if (parts.size > 2 || maximum == null || maximum !in 2..1_000_000) "사용법: !주사위 또는 !주사위 100 (2~1000000)" else "주사위: ${random.nextInt(maximum) + 1} / $maximum"
            }
            "!뽑기" -> {
                val pool = parts.drop(1)
                if (pool.size !in 2..20 || pool.any { it.length > 30 } || pool.distinct().size != pool.size)
                    "서로 다른 후보 2~20개를 입력해 주세요. 예: !뽑기 가 나 다" else "뽑기 결과: ${pool[random.nextInt(pool.size)]}\n입력 후보 ${pool.size}명 중 추첨"
            }
            else -> null
        }
    }
}
