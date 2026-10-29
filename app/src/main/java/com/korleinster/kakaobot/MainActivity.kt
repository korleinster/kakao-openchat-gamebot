package com.korleinster.kakaobot

import android.app.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.time.*
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var engine: BotEngine
    private lateinit var status: TextView
    private lateinit var connection: TextView
    private lateinit var targetRoom: EditText
    private lateinit var pair: TextView
    private lateinit var history: TextView
    private lateinit var definitions: LinearLayout
    private lateinit var running: Switch
    private lateinit var schedules: Switch
    private lateinit var morning: Switch
    private lateinit var empty: Switch
    private lateinit var confirm: Button
    private var refreshing = false
    private var lastDefinitionSnapshot = ""
    private val handler = Handler(Looper.getMainLooper())
    private val statusChanged: () -> Unit = { handler.post { refresh() } }
    private val refreshTask = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 1000) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = BotEngine.get(this)
        window.statusBarColor = Color.rgb(246, 248, 250)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(30)); setBackgroundColor(Color.rgb(246,248,250)) }
        scroll.addView(content); setContentView(scroll)
        fun text(value: String, size: Float = 16f): TextView = TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(28,45,57)); setPadding(0,dp(7),0,dp(7)); content.addView(this) }
        fun button(label: String, block: () -> Unit): Button = Button(this).apply { text = label; isAllCaps = false; contentDescription = label; setOnClickListener { block() }; content.addView(this) }
        fun toggle(label: String, block: (Boolean) -> Unit): Switch = Switch(this).apply {
            text = label; textSize = 16f; setPadding(0,dp(10),0,dp(10)); setOnCheckedChangeListener { _, value -> if (!refreshing) block(value) }; content.addView(this)
        }
        text("게임방 도우미",28f)
        text("시험 버전 ${engine.versionName} · 한 번에 한 방 연결")
        status = text("연결 상태 확인 중",19f).apply { tag = "connection-headline" }
        connection = text("연결 상태 확인 중",14f).apply { tag = "connection-details" }
        text("연결한 대화에만 답해. 방 연결 중에는 새 카카오톡 알림의 마지막 메시지에서 연결 코드만 검사하고 다른 대화는 저장하지 않아.",14f)
        button("알림 접근 허용 화면") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button("정시 알림 권한 확인") {
            if (Build.VERSION.SDK_INT >= 31 && !engine.exactAllowed()) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            else toast("정시 알림 권한 있음")
        }
        text("방 연결",21f)
        targetRoom = EditText(this).apply {
            hint = "대상 오픈채팅방 이름"; setText(engine.prefs.targetRoom); isSingleLine = true
            contentDescription = "대상 오픈채팅방 이름"; tag = "target-room-input"; content.addView(this)
        }
        text("방 이름을 입력하고 새 코드를 만들면 기존 방 연결과 대기 중인 예약 발송이 해제돼.",13f)
        button("입력한 방으로 연결 코드 만들기") {
            val room = try { RoomAdmission.normalizeName(targetRoom.text.toString()) } catch (_: IllegalArgumentException) { toast("방 이름을 1~80자로 입력해줘"); return@button }
            targetRoom.setText(room)
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(targetRoom.windowToken, 0)
            targetRoom.clearFocus()
            engine.submit { engine.beginPair(room); handler.post { refresh() } }
        }.apply { tag = "create-pairing-code" }
        pair = text("연결 코드를 만든 뒤 다른 계정에서 대상 방에 입력해줘.").apply { tag = "pairing-instructions" }
        confirm = button("이 방으로 연결 확인") { engine.submit { engine.approvePair(); handler.post { refresh() } } }.apply { tag = "confirm-room" }
        running = toggle("명령 응답 실행") { enabled ->
            if (enabled && (engine.prefs.bound.isBlank() || engine.prefs.collision)) { toast("방을 먼저 연결해줘"); refresh() }
            else engine.submit { engine.prefs.running = enabled; engine.changedSettings(); handler.post { refresh() } }
        }
        schedules = toggle("정기 일정 알림 사용") { enabled ->
            if (enabled && !engine.exactAllowed()) { toast("정시 알림 권한을 먼저 허용해줘"); refresh() }
            else engine.submit { engine.prefs.scheduleEnabled = enabled; engine.changedSettings(); handler.post { refresh() } }
        }
        morning = toggle("매일 오전 7시 일정 모아 알림") { enabled -> engine.submit { engine.prefs.morningEnabled = enabled; engine.changedSettings(); handler.post { refresh() } } }
        empty = toggle("일정 없는 날도 아침 안내") { enabled -> engine.submit { engine.prefs.sendEmpty = enabled; engine.changedSettings(); handler.post { refresh() } } }
        button("30초 후 시험 알림 예약") { engine.submit { engine.localTest(); handler.post { refresh() } } }
        text("등록 일정",21f)
        text("실제 게임 일정은 아직 등록되지 않았어. 확인된 시각만 입력해줘. 일정은 한국 시간 기준이야.",14f)
        button("월드보스·정기 콘텐츠 추가") { showScheduleDialog() }
        button("기간제 컬렉션 추가") { showCollectionDialog() }
        definitions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; content.addView(this) }
        text("최근 처리 기록",21f)
        history = text("기록 없음",13f)
        text("상태는 자동으로 갱신돼. ‘답장 준비’는 최근 알림 기준이고 실제 도착은 !핑으로 확인해줘. 앱을 강제 중지하거나 답장 연결이 사라지면 새 방 알림이 필요해.",13f)
        button("라이선스·원본 정보") { AlertDialog.Builder(this).setTitle("오픈소스 고지").setMessage(assets.open("third-party-notices.txt").bufferedReader().readText()).setPositiveButton("닫기", null).show() }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onResume() {
        super.onResume(); engine.addStatusListener(statusChanged)
        handler.removeCallbacks(refreshTask); handler.post(refreshTask)
        engine.submit { engine.requestListenerConnection(); engine.tick() }
    }
    override fun onPause() { engine.removeStatusListener(statusChanged); handler.removeCallbacks(refreshTask); super.onPause() }
    override fun onDestroy() { engine.removeStatusListener(statusChanged); handler.removeCallbacksAndMessages(null); super.onDestroy() }
    private fun refresh() {
        if (!::status.isInitialized) return
        refreshing = true
        status.text = engine.statusLabel()
        connection.text = engine.connectionDetails()
        val room = engine.prefs.targetRoom
        val seconds = engine.connectionState().remainingSeconds(System.currentTimeMillis())
        pair.text = when {
            engine.prefs.pairingIssue.isNotBlank() && seconds > 0 -> "연결 코드는 받았어.\n${engine.prefs.pairingIssue}\n이 상태에서는 연결 완료로 처리하지 않아. 최근 처리 기록을 확인해줘."
            engine.prefs.code.isNotBlank() && seconds > 0 -> "다른 계정에서 ‘$room’ 방에 입력\n!연결 ${engine.prefs.code}\n남은 시간 ${seconds / 60}분 ${seconds % 60}초\n이 앱을 열어둬도 코드 수신 상태가 바로 표시돼."
            engine.prefs.candidate.isNotBlank() -> "연결 코드가 도착했어.\n카카오톡이 방 이름을 주지 않을 수 있으니 직접 코드를 보낸 ‘$room’ 대화가 맞는지 확인한 뒤 아래 버튼을 눌러줘."
            engine.prefs.bound.isNotBlank() -> "‘$room’ 방 연결 확인됨"
            engine.prefs.code.isNotBlank() -> "연결 코드가 만료됐어. 새 코드를 만들어줘."
            else -> "연결 코드를 만든 뒤 다른 계정에서 입력해줘."
        }
        confirm.isEnabled = engine.prefs.candidate.isNotBlank() && !engine.prefs.collision
        running.isChecked = engine.prefs.running; schedules.isChecked = engine.prefs.scheduleEnabled
        morning.isChecked = engine.prefs.morningEnabled; empty.isChecked = engine.prefs.sendEmpty
        history.text = engine.db.logs().ifBlank { "기록 없음" }
        val defs = engine.db.definitions(); val snapshot = defs.joinToString { it.toJson() }
        if (snapshot != lastDefinitionSnapshot) {
            lastDefinitionSnapshot = snapshot; definitions.removeAllViews()
            defs.forEach { d ->
                definitions.addView(TextView(this).apply { text = "${d.name}\n" + if(d.category == "collection") "기준 ${d.anchor} · ${d.intervalMonths}개월 · ${d.noticeDays}일 전" else "${d.time} · 요일 ${d.weekdays.sortedBy { it.value }.joinToString { it.value.toString() }}"; textSize = 15f; setPadding(0,dp(10),0,0) })
                definitions.addView(Button(this).apply { text = "${d.name} 삭제"; setOnClickListener { engine.submit { engine.db.removeDefinition(d.id); engine.tick(); handler.post { refresh() } } } })
            }
        }
        refreshing = false
    }
    private fun field(parent: LinearLayout, hint: String, value: String = ""): EditText = EditText(this).apply { this.hint = hint; setText(value); isSingleLine = true; parent.addView(this) }
    private fun form() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
    private fun showScheduleDialog() {
        val form = form(); val name = field(form,"콘텐츠 이름")
        val kind = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("월드보스","정기 콘텐츠")); form.addView(this) }
        val time = field(form,"시작 시각 HH:mm"); val days=field(form,"요일 1=월 … 7=일 (예: 135)","1234567")
        val dialog=AlertDialog.Builder(this).setTitle("한국 시간 일정 등록").setView(form).setPositiveButton("저장",null).setNegativeButton("취소",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val selectedDays = days.text.toString().trim().map { DayOfWeek.of(it.digitToInt()) }.toSet()
                val d=Definition(UUID.randomUUID().toString(),name.text.toString().trim(),if(kind.selectedItemPosition==0) "world_boss" else "content",LocalTime.parse(time.text.toString().trim()),selectedDays)
                if(engine.db.definitions().size >= 30) throw IllegalArgumentException()
                engine.submit { engine.db.saveDefinition(d); engine.tick(); handler.post { refresh() } }; dialog.dismiss()
            } catch(_:Exception) { toast("이름·시각·요일을 확인해줘 (최대 30개)") }
        } };dialog.show()
    }
    private fun showCollectionDialog() {
        val form=form();val name=field(form,"컬렉션 원본 이름");val anchor=field(form,"기준일 YYYY-MM-DD HH:mm");val months=field(form,"갱신 주기 개월 수 (1~24)");val days=field(form,"며칠 전 안내 (0~90)")
        val dialog=AlertDialog.Builder(this).setTitle("공통 컬렉션 주기 등록").setView(form).setPositiveButton("저장",null).setNegativeButton("취소",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val d=Definition(UUID.randomUUID().toString(),name.text.toString().trim(),"collection",anchor=LocalDateTime.parse(anchor.text.toString().trim().replace(' ','T')),intervalMonths=months.text.toString().toLong(),noticeDays=days.text.toString().toLong())
                if(engine.db.definitions().size >= 30) throw IllegalArgumentException()
                engine.submit { engine.db.saveDefinition(d);engine.tick();handler.post { refresh() } };dialog.dismiss()
            } catch(_:Exception) { toast("기준일·개월 수·안내 시점을 모두 입력해줘") }
        } };dialog.show()
    }
}
