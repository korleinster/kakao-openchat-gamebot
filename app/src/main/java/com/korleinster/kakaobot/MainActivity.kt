package com.korleinster.kakaobot

import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Native controls retain accessibility semantics, keyboard support and system pickers. */
class MainActivity : ComponentActivity() {
    private lateinit var owner: BotEngine
    private lateinit var page: LinearLayout
    private var screen = "rooms"
    private val handler = Handler(Looper.getMainLooper())
    private val statusViews = mutableMapOf<String, TextView>()
    private var detailStatus: TextView? = null
    private var pairingText: TextView? = null
    private var pairingConfirm: Button? = null
    private var renderedRoom: String? = null
    private var renderedAccess = ""
    private fun accessSnapshot() = "${owner.consentAccepted()}|${owner.notificationAccessGranted()}|${owner.exactAllowed()}"
    private val changed: () -> Unit = { handler.post { refreshStatus() } }
    private val ticker = object : Runnable { override fun run() { refreshStatus(); handler.postDelayed(this, 1000) } }
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val background get() = Color.parseColor(if(dark) "#111A1C" else "#F4F7F7")
    private val surface get() = Color.parseColor(if(dark) "#1C282B" else "#FFFFFF")
    private val ink get() = Color.parseColor(if(dark) "#E9F2F1" else "#172C31")
    private val secondary get() = Color.parseColor(if(dark) "#A5B8B9" else "#566E72")
    private val accent get() = Color.parseColor(if(dark) "#7CDBCC" else "#006B5E")
    private val soft get() = Color.parseColor(if(dark) "#243D3C" else "#E3F2EE")
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun round(color: Int, radius: Int = 20) = GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun layout(parent: LinearLayout, horizontal: Boolean = false, padding: Int = 0): LinearLayout = LinearLayout(this).apply {
        orientation=if(horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        setPadding(dp(padding),dp(padding),dp(padding),dp(padding));parent.addView(this)
    }
    private fun text(parent: LinearLayout, value: String, size: Float=15f, bold:Boolean=false, muted:Boolean=false): TextView = TextView(this).apply {
        text=value;textSize=size;setTextColor(if(muted) secondary else ink);if(bold) setTypeface(typeface,Typeface.BOLD)
        setPadding(0,dp(5),0,dp(5));parent.addView(this)
    }
    private fun button(parent: LinearLayout,label:String,primary:Boolean=false,tagValue:String?=null, action:()->Unit): Button = Button(this).apply {
        text=label;isAllCaps=false;textSize=15f;minHeight=dp(50);minimumHeight=dp(50)
        setTextColor(if(primary) (if(dark) Color.parseColor("#102B28") else Color.WHITE) else accent)
        background=round(if(primary) accent else soft,14);setPadding(dp(16),dp(10),dp(16),dp(10))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) }
        tag=tagValue;setOnClickListener { action() };parent.addView(this)
    }
    private fun card(parent: LinearLayout = page): LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;background=round(surface);setPadding(dp(18),dp(16),dp(18),dp(18))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) };parent.addView(this)
    }
    private fun toggle(parent:LinearLayout,label:String,value:Boolean,enabled:Boolean=true,action:(Boolean)->Unit): Switch = Switch(this).apply {
        text=label;textSize=16f;setTextColor(ink);minHeight=dp(52);isChecked=value;isEnabled=enabled
        setOnCheckedChangeListener { _, selected -> action(selected) };parent.addView(this)
    }
    private fun update(room:BotEngine,action:(BotEngine)->Unit) { room.submit { action(room);handler.post { render() } } }
    private fun toast(message:String) = Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    private fun launch(intent: Intent) { try { startActivity(intent) } catch(_:ActivityNotFoundException) { toast("이 기기에서 설정 화면을 열 수 없어요. Android 설정에서 직접 확인해 주세요.") } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);owner=BotEngine.get(this);screen=savedInstanceState?.getString("screen") ?: "rooms";render()
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(screen!="rooms") { screen="rooms";render() } else finish() }
        })
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("screen",screen);super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume();owner.addStatusListener(changed);handler.post(ticker);owner.submit { owner.requestListenerConnection();owner.tick() } }
    override fun onPause() { owner.removeStatusListener(changed);handler.removeCallbacks(ticker);super.onPause() }
    override fun onDestroy() { owner.removeStatusListener(changed);handler.removeCallbacksAndMessages(null);super.onDestroy() }
    private fun render() {
        statusViews.clear();detailStatus=null;pairingText=null;pairingConfirm=null
        renderedRoom=owner.selectedOrNull?.roomId;renderedAccess=accessSnapshot()
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(this@MainActivity.background) }
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        setContentView(root)
        val header=layout(root,true,20).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(com.korleinster.kakaobot.R.drawable.ic_bot);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(36),dp(36)))
        text(header,"  채팅봇",24f,true)
        val scroll=ScrollView(this).apply { isFillViewport=true;clipToPadding=false }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),dp(18)) };scroll.addView(page)
        when(screen) { "detail" -> showDetail();"alerts" -> showAlerts();"settings" -> showSettings();else -> showRooms() }
        val nav=layout(root,true,8).apply { setBackgroundColor(surface) }
        listOf("rooms" to "채팅방","alerts" to "알림","settings" to "설정").forEach { (key,label) ->
            val item=TextView(this).apply {
                text=label;textSize=15f;gravity=Gravity.CENTER;minHeight=dp(52);setTextColor(if(screen==key || (screen=="detail" && key=="rooms")) accent else secondary)
                if(screen==key) setTypeface(typeface,Typeface.BOLD);isClickable=true;isFocusable=true;tag="nav-$key"
                setOnClickListener { screen=key;render() }
            };nav.addView(item,LinearLayout.LayoutParams(0,-2,1f))
        }
        root.requestApplyInsets();refreshStatus()
    }
    private fun permissionNotice() {
        if(owner.consentAccepted() && owner.notificationAccessGranted()) return
        val c=card();text(c,"연결 준비",17f,true)
        text(c,if(!owner.consentAccepted()) "알림을 사용하는 방법을 확인하고 동의하면 채팅방을 연결할 수 있어요." else "카카오톡 알림을 받아 답장하려면 알림 접근을 허용해 주세요.",muted=true)
        button(c,if(!owner.consentAccepted()) "알림 사용 안내 확인" else "알림 접근 허용",true) { requestNotificationAccess() }
    }
    private fun showRooms() {
        text(page,"필요한 만큼, 가볍게",27f,true);text(page,"채팅방마다 필요한 기능과 말투를 골라 보세요.",muted=true)
        permissionNotice()
        val rooms=owner.rooms()
        if(rooms.isEmpty()) { val c=card();text(c,"첫 채팅방을 연결해 볼까요?",20f,true);text(c,"폰의 카카오톡 계정이 참여한 대화를 연결할 수 있어요. 다른 계정으로 연결 코드를 보내면 돼요.",muted=true) }
        rooms.forEach { room ->
            val c=card();text(c,room.prefs.targetRoom,20f,true)
            statusViews[room.roomId]=text(c,room.statusLabel(),14f,muted=true)
            text(c,room.prefs.features.joinToString(" · ") { it.label }.ifBlank { "선택한 기능 없음" },14f)
            text(c,"말투 · ${room.prefs.tone.label}",13f,muted=true)
            button(c,"방 설정",tagValue="open-room-${room.roomId}") { owner.submit { owner.selectRoom(room.roomId);handler.post { screen="detail";render() } } }
        }
        button(page,"새 채팅방 연결",true,"add-room") { showRoomDialog() }
        text(page,"방 이름만으로 자동 연결하지 않아요. 연결 코드와 앱에서의 확인으로 대화를 구분해요.",13f,muted=true)
    }
    private fun chooseRoom() {
        val rooms=owner.rooms();if(rooms.isEmpty()) { showRoomDialog();return }
        AlertDialog.Builder(this).setTitle("채팅방 선택").setItems(rooms.map { it.prefs.targetRoom }.toTypedArray()) { _,index ->
            owner.submit { owner.selectRoom(rooms[index].roomId);handler.post { render() } }
        }.setNegativeButton("취소",null).show()
    }
    private fun showDetail() {
        val room=owner.selectedOrNull ?: run { screen="rooms";showRooms();return }
        button(page,"‹ 채팅방 목록") { screen="rooms";render() }
        text(page,room.prefs.targetRoom,27f,true)
        val state=card();detailStatus=text(state,room.statusLabel(),17f,true).apply { tag="connection-headline" }
        toggle(state,"이 채팅방에서 봇 사용",room.prefs.running) { enabled ->
            if(enabled && (!owner.consentAccepted() || room.prefs.bound.isBlank() || room.prefs.collision)) { toast("알림 사용 동의와 채팅방 연결을 먼저 확인해 주세요.");render() }
            else update(room) { it.prefs.running=enabled;it.changedSettings() }
        }.tag="room-running"
        if(room.prefs.bound.isBlank() || room.prefs.code.isNotBlank() || room.prefs.candidate.isNotBlank()) {
            pairingText=text(state,"",14f).apply { tag="pairing-instructions" }
            pairingConfirm=button(state,"이 대화로 연결 확인",true,"confirm-room") { update(room) { it.approvePair() } }
            button(state,"연결 코드 만들기",tagValue="create-pairing-code") { requestPairing(room) }
        }
        val features=card();text(features,"이 방에서 사용할 기능",19f,true);text(features,"필요한 기능을 각각 선택하세요. 변경한 뒤 저장하면 적용돼요.",14f,muted=true)
        val draft=room.prefs.features.toMutableSet()
        BotFeature.entries.forEach { feature ->
            val box=CheckBox(this).apply { text=feature.label;textSize=16f;setTextColor(ink);minHeight=dp(48);isChecked=feature in draft;tag="feature-${feature.name}"
                setOnCheckedChangeListener { _, checked -> if(checked) draft.add(feature) else draft.remove(feature) } }
            features.addView(box);text(features,feature.description,13f,muted=true)
        }
        val tones=card();text(tones,"봇 말투",19f,true);text(tones,"명령 답장과 예약 알림에 적용돼요.",14f,muted=true)
        var selectedTone=room.prefs.tone
        val group=RadioGroup(this);tones.addView(group)
        BotTone.entries.forEach { tone ->
            val option=RadioButton(this).apply { id=View.generateViewId();text="${tone.label} · ${tone.description}";textSize=15f;setTextColor(ink);minHeight=dp(48);tag="tone-${tone.name}" }
            group.addView(option);if(tone==selectedTone) group.check(option.id)
        }
        text(tones,"말투 미리보기",13f,true,muted=true)
        val preview=text(tones,BotMessages.preview(selectedTone),15f).apply { tag="tone-preview";background=round(soft,12);setPadding(dp(14),dp(12),dp(14),dp(12)) }
        group.setOnCheckedChangeListener { _,id -> val view=group.findViewById<RadioButton>(id);selectedTone=BotTone.valueOf(view.tag.toString().removePrefix("tone-"));preview.text=BotMessages.preview(selectedTone) }
        button(page,"기능과 말투 저장",true,"save-room-preferences") { val selected=draft.toSet();val tone=selectedTone;room.submit { room.saveConfiguration(selected,tone);handler.post { render();toast("방 설정을 저장했어요.") } } }
        val connection=card();text(connection,"연결 관리",19f,true)
        button(connection,"연결 상태 자세히") { AlertDialog.Builder(this).setTitle("연결 상태").setMessage(room.connectionDetails()).setPositiveButton("확인",null).show() }
        button(connection,"30초 알림으로 연결 확인") { update(room) { it.localTest() };toast("연결·권한 상태에 따라 시험 알림을 예약해요.") }
        button(connection,"앱 연결 해제") { confirm("앱 연결 해제","이 방의 응답과 예약 발송을 중지해요. 등록 알림은 보관하며 카카오톡 방에서 나가지는 않아요.") { update(room) { it.disconnectRoom() } } }
        button(connection,"이 방 설정 삭제") { confirm("방 설정 삭제","이 앱에 저장된 방 설정·등록 알림·발송 기록을 삭제해요. 이 작업은 되돌릴 수 없어요.") { owner.submit { owner.removeRoom(room.roomId);handler.post { screen="rooms";render() } } } }
    }
    private fun pairingInstructions(room:BotEngine):String {
        val p=room.prefs;val seconds=room.connectionState().remainingSeconds(System.currentTimeMillis())
        return when {
            p.candidate.isNotBlank() -> "코드를 받았어요. 직접 코드를 보낸 ‘${p.targetRoom}’ 대화가 맞는지 확인하고 연결해 주세요."
            p.pairingIssue.isNotBlank() && seconds>0 -> p.pairingIssue
            p.code.isNotBlank() && seconds>0 -> "다른 계정에서 ‘${p.targetRoom}’에 보내 주세요.\n!연결 ${p.code}\n남은 시간 ${seconds/60}분 ${seconds%60}초"
            p.code.isNotBlank() -> "연결 코드가 만료됐어요. 새 코드를 만들어 주세요."
            else -> "이 앱에서 코드를 만들고 다른 계정에서 해당 대화에 보내 주세요."
        }
    }
    private fun refreshStatus() {
        if(!::owner.isInitialized) return
        if(renderedRoom!=owner.selectedOrNull?.roomId || renderedAccess!=accessSnapshot()) { render();return }
        owner.rooms().forEach { room -> statusViews[room.roomId]?.text=room.statusLabel() }
        owner.selectedOrNull?.let { room -> detailStatus?.text=room.statusLabel();pairingText?.text=pairingInstructions(room);pairingConfirm?.isEnabled=room.prefs.candidate.isNotBlank() && !room.prefs.collision }
    }
    private fun showRoomDialog() {
        if(!owner.consentAccepted()) { showConsent();return }
        val form=form();val name=field(form,"카카오톡에 표시된 방 이름")
        text(form,"새 방은 기능을 선택한 뒤 연결할 수 있어요. 이미 연결한 다른 방은 계속 동작해요.",14f,muted=true)
        val dialog=AlertDialog.Builder(this).setTitle("새 채팅방 연결").setView(form).setPositiveButton("연결 코드 만들기",null).setNegativeButton("취소",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val roomName=runCatching { RoomAdmission.normalizeName(name.text.toString()) }.getOrElse { toast("방 이름을 1~80자로 입력해 주세요.");return@setOnClickListener }
            if(owner.rooms().size>=20) { toast("최대 20개 방까지 등록할 수 있어요.");return@setOnClickListener }
            owner.submit { val room=owner.addRoom();room.beginPair(roomName);handler.post { screen="detail";render() } };dialog.dismiss()
        } };dialog.show()
    }
    private fun requestPairing(room:BotEngine) {
        if(!owner.consentAccepted()) { showConsent();return }
        update(room) { it.beginPair() }
    }
    private fun showAlerts() {
        text(page,"알림",27f,true)
        val room=owner.selectedOrNull ?: run { val c=card();text(c,"채팅방을 먼저 연결해 주세요.",18f,true);button(c,"채팅방 연결",true) { showRoomDialog() };return }
        button(page,"${room.prefs.targetRoom}  ▾",tagValue="select-room") { chooseRoom() }
        val controls=card();text(controls,"예약 알림 발송",19f,true)
        text(controls,"기능 선택과 별도로 발송을 켜야 알림을 보내요. 이 방의 봇 사용도 켜져 있어야 해요.",14f,muted=true)
        toggle(controls,"예약 알림 발송",room.prefs.scheduleEnabled) { enabled ->
            when {
                enabled && !owner.consentAccepted() -> { showConsent();render() }
                enabled && !FeaturePolicy.hasAlerts(room.prefs.features) -> { toast("방 설정에서 알림 기능을 먼저 선택해 주세요.");render() }
                enabled && !owner.exactAllowed() -> { requestExactAccess();render() }
                else -> update(room) { it.prefs.scheduleEnabled=enabled;it.changedSettings() }
            }
        }.tag="scheduled-sending"
        if(BotFeature.DAILY_SUMMARY in room.prefs.features) {
            button(controls,"아침 요약 시각 · ${room.prefs.summaryTime}") { timePicker(room.prefs.summaryTime) { time -> update(room) { it.prefs.summaryTime=time;it.changedSettings() } } }
            toggle(controls,"일정이 없는 날도 요약 보내기",room.prefs.sendEmpty) { selected -> update(room) { it.prefs.sendEmpty=selected;it.changedSettings() } }
        }
        button(page,"알림 만들기",true,"add-reminder") {
            AlertDialog.Builder(this).setTitle("알림 방식").setItems(arrayOf("반복 알림 · 요일과 시각","주기 알림 · 몇 개월마다")) { _,which -> reminderForm(room,null,which==1) }.setNegativeButton("취소",null).show()
        }
        val defs=room.db.definitions()
        if(defs.isEmpty()) { val c=card();text(c,"등록된 알림이 없어요",19f,true);text(c,"모임, 운동, 갱신일처럼 기억할 일을 추가해 보세요.",muted=true) }
        defs.forEach { d ->
            val c=card();text(c,d.name,19f,true);text(c,if(d.isMonthly) "주기 알림 · ${d.intervalMonths}개월마다\n기준 ${d.anchor?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))} · ${d.noticeDays}일 전 안내" else "반복 알림 · ${d.weekdays.sortedBy { it.value }.joinToString(" · ") { dayLabel(it) }}\n${d.time} · ${d.leadMinutes}분 전 안내",14f,muted=true)
            val feature=if(d.isMonthly) BotFeature.CYCLE_REMINDERS else BotFeature.REPEATING_REMINDERS
            if(feature !in room.prefs.features) text(c,"이 방에서 ${feature.label} 기능이 꺼져 있어요.",13f,muted=true)
            toggle(c,"이 알림 사용",d.enabled) { enabled -> update(room) { it.db.saveDefinition(d.copy(enabled=enabled,revision=d.revision+1));it.changedSettings() } }
            val row=layout(c,true)
            button(row,"수정") { reminderForm(room,d,d.isMonthly) }.layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(6);topMargin=dp(8) }
            button(row,"삭제") { confirm("알림 삭제","‘${d.name}’ 알림을 삭제할까요?") { update(room) { it.db.removeDefinition(d.id);it.tick() } } }.layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6);topMargin=dp(8) }
        }
        text(page,"시각은 한국 시간 기준이에요. 메신저 알림·절전 설정에 따라 늦거나 도착하지 않을 수 있어요.",13f,muted=true)
    }
    private fun dayLabel(day:DayOfWeek)=listOf("월","화","수","목","금","토","일")[day.value-1]
    private fun timePicker(initial:LocalTime,save:(LocalTime)->Unit) { TimePickerDialog(this,{_,h,m->save(LocalTime.of(h,m))},initial.hour,initial.minute,true).show() }
    private fun form()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(8),dp(22),dp(12)) }
    private fun field(parent:LinearLayout,hint:String,value:String="",numeric:Boolean=false):EditText {
        val label=text(parent,hint,14f,true,muted=true)
        return EditText(this).apply {
            id=View.generateViewId();label.labelFor=id
            this.hint=if(numeric) "숫자 입력" else "이름 입력";setText(value);isSingleLine=true;minHeight=dp(52)
            setTextColor(ink);setHintTextColor(secondary)
            inputType=if(numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            parent.addView(this)
        }
    }
    private fun reminderForm(room:BotEngine,existing:Definition?,monthly:Boolean) {
        val form=form();val scroll=ScrollView(this).apply { addView(form) }
        val name=field(form,"알림 이름",existing?.name ?: "")
        var time=existing?.anchor?.toLocalTime() ?: existing?.time ?: LocalTime.of(18,0)
        val at=button(form,"시각 · $time") { }
        at.setOnClickListener { timePicker(time) { time=it;at.text="시각 · $time" } }
        val selectedDays=(existing?.weekdays ?: DayOfWeek.values().toSet()).toMutableSet()
        var date=existing?.anchor?.toLocalDate() ?: LocalDate.now(APP_ZONE)
        var months:EditText?=null;var noticeDays:EditText?=null;var lead:EditText?=null
        if(monthly) {
            val dateButton=button(form,"기준일 · $date") { }
            dateButton.setOnClickListener { DatePickerDialog(this,{_,y,m,d->date=LocalDate.of(y,m+1,d);dateButton.text="기준일 · $date"},date.year,date.monthValue-1,date.dayOfMonth).show() }
            months=field(form,"반복 주기 · 개월 (1~24)",existing?.intervalMonths?.toString() ?: "2",true)
            noticeDays=field(form,"며칠 전 안내 (0~90)",existing?.noticeDays?.toString() ?: "0",true)
            text(form,"월말을 기준일로 선택해도 원래 기준일을 유지하며 계산해요.",13f,muted=true)
        } else {
            text(form,"반복 요일",15f,true)
            listOf(DayOfWeek.values().take(4),DayOfWeek.values().drop(4)).forEach { days ->
                val row=layout(form,true)
                days.forEach { day -> val box=CheckBox(this).apply { text=dayLabel(day);contentDescription="${dayLabel(day)}요일";minHeight=dp(48);setTextColor(ink);isChecked=day in selectedDays;setOnCheckedChangeListener {_,yes->if(yes) selectedDays.add(day) else selectedDays.remove(day)} };row.addView(box,LinearLayout.LayoutParams(0,-2,1f)) }
            }
            lead=field(form,"미리 안내 · 분 (0~1440)",existing?.leadMinutes?.toString() ?: "5",true)
            text(form,"0분이면 정각에 안내해요.",13f,muted=true)
        }
        val dialog=AlertDialog.Builder(this).setTitle(if(existing==null) { if(monthly) "주기 알림 만들기" else "반복 알림 만들기" } else "알림 수정").setView(scroll).setPositiveButton("저장",null).setNegativeButton("취소",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                require(existing!=null || room.db.definitions().size<30)
                val title=RoomAdmission.normalizeName(name.text.toString())
                val d=Definition(existing?.id ?: UUID.randomUUID().toString(),title,if(monthly) "monthly" else "weekly",time,selectedDays,
                    if(monthly) date.atTime(time) else null,months?.text?.toString()?.toLong() ?: 0,noticeDays?.text?.toString()?.toLong() ?: 0,
                    existing?.enabled ?: true,(existing?.revision ?: 0)+1,lead?.text?.toString()?.toLong() ?: 5)
                update(room) { it.db.saveDefinition(d);it.changedSettings() };dialog.dismiss()
            } catch(_:Exception) { toast("이름·요일·숫자 범위를 확인해 주세요. 방마다 최대 30개 알림을 등록할 수 있어요.") }
        } };dialog.show()
    }
    private fun showSettings() {
        text(page,"설정",27f,true)
        val access=card();text(access,"사용 준비",19f,true)
        text(access,"알림 사용 동의 · ${if(owner.consentAccepted()) "완료" else "확인 필요"}\n알림 접근 · ${if(owner.notificationAccessGranted()) "허용됨" else "허용 필요"}\n정확한 알림 · ${if(owner.exactAllowed()) "허용됨" else "알림 기능 사용 시 필요"}",14f,muted=true)
        button(access,"알림 사용 안내·동의") { showConsent() }
        button(access,"알림 접근 설정") { requestNotificationAccess() }
        button(access,"정확한 알림 설정") { requestExactAccess() }
        val privacy=card();text(privacy,"개인정보와 데이터",19f,true)
        text(privacy,"대화 처리는 기기 안에서 이루어져요. 답장과 알림은 연결한 카카오톡 대화로 전송돼요.",14f,muted=true)
        button(privacy,"개인정보 처리방침") { showAsset("개인정보 처리방침","privacy-ko.txt") }
        button(privacy,"알림 사용 동의 철회") { confirm("동의 철회","모든 방의 처리와 발송을 중지해요. 저장된 설정은 보관하며 다시 동의하면 사용할 수 있어요.") { owner.submit { owner.revokeConsent();handler.post { render() } } } }
        button(privacy,"앱 데이터 모두 삭제") { confirm("앱 데이터 모두 삭제","이 기기에 저장된 모든 방·알림·발송 기록과 동의 정보를 삭제해요. 되돌릴 수 없어요. Android의 알림 접근 권한은 설정에서 별도로 해제할 수 있어요.") { owner.submit { owner.eraseAllData();handler.post { screen="rooms";render() } } } }
        val about=card();text(about,"채팅봇",19f,true);text(about,"버전 ${owner.versionName}\n개발 · korleinster",14f,muted=true)
        button(about,"도움말") { AlertDialog.Builder(this).setTitle("채팅봇 사용 방법").setMessage("1. 알림 사용 안내를 확인하고 알림 접근을 허용해요.\n2. 다른 계정에서 해당 방에 연결 코드를 보내고 앱에서 확인해요.\n3. 방별 기능·말투를 저장하고 봇 사용을 켜요.\n4. 알림 탭에서 알림을 등록하고 예약 발송을 켜요.\n\n메신저의 메시지 알림이 꺼져 있거나 해당 대화를 열어두면 새 알림을 받지 못할 수 있어요. !핑으로 실제 답장을 확인해 주세요.").setPositiveButton("확인",null).show() }
        button(about,"문의·문제 제보") { launch(Intent(Intent.ACTION_VIEW,Uri.parse(PrivacyText.SUPPORT_URL))) }
        button(about,"오픈소스 라이선스") { showAsset("오픈소스 라이선스","third-party-notices.txt") }
    }
    private fun requestNotificationAccess() { if(!owner.consentAccepted()) showConsent(true) else launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    private fun requestExactAccess() {
        if(Build.VERSION.SDK_INT>=31) launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
        else toast("이 Android 버전은 별도 정확한 알림 권한이 필요하지 않아요.")
    }
    private fun showConsent(openSettings:Boolean=false) {
        AlertDialog.Builder(this).setTitle("카카오톡 알림 사용 안내").setMessage(PrivacyText.DISCLOSURE)
            .setPositiveButton("동의하고 계속") { _,_->owner.submit { owner.acceptConsent();handler.post { render();if(openSettings && !owner.notificationAccessGranted()) launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } } }
            .setNegativeButton("나중에",null).setNeutralButton("처리방침") {_,_->showAsset("개인정보 처리방침","privacy-ko.txt")}.show()
    }
    private fun showAsset(title:String,name:String) { AlertDialog.Builder(this).setTitle(title).setMessage(assets.open(name).bufferedReader().use { it.readText() }).setPositiveButton("확인",null).show() }
    private fun confirm(title:String,message:String,yes:()->Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("확인") { _,_->yes() }.setNegativeButton("취소",null).show() }
}
