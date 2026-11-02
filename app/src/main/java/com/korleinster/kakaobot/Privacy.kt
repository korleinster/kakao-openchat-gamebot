package com.korleinster.kakaobot

import android.content.Context

@android.annotation.SuppressLint("ApplySharedPref")
class AppConsent(context: Context, file: String = "privacy-consent") {
    private val p = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    val accepted: Boolean get() = p.getInt("version", 0) == 1
    fun accept() { check(p.edit().putInt("version",1).putLong("acceptedAt",System.currentTimeMillis()).commit()) }
    fun revoke() { check(p.edit().clear().commit()) }
}
object PrivacyText {
    const val DISCLOSURE = "알림 접근을 사용하면 카카오톡 알림의 대화 식별 정보와 최신 메시지를 기기 안에서 확인합니다. 앱을 닫거나 화면을 꺼도 연결한 대화의 명령을 처리하고 예약 알림을 전송합니다.\n\n방을 연결하는 동안에는 새 카카오톡 알림에서 정확한 연결 코드만 찾습니다. 연결한 뒤에는 선택한 대화의 명령만 처리합니다. 다른 앱의 알림은 처리하지 않습니다.\n\n이 앱은 자체 서버로 대화를 업로드하지 않습니다. 사용자가 선택한 서버와의 웹 동기화은 알림 일정·요약 설정·목록 버전과 반영 확인만 HTTPS로 주고받습니다. 동기화를 켜면 알림 변경을 자동으로 주고받고 대화나 발송 내역은 올리지 않습니다. 답장과 알림은 카카오톡을 통해 해당 대화 참여자에게 전송됩니다. 설정·등록 알림과 발송 내용은 기기에 저장하며, 7일 지난 완료 발송 기록과 1일 지난 중복 확인 값은 앱 동작 시 정리합니다. 설정에서 동의를 철회하거나 데이터를 삭제할 수 있습니다."
    const val POLICY_URL = "https://github.com/korleinster/kakao-openchat-gamebot/blob/main/docs/privacy-policy.md"
    const val SUPPORT_URL = "https://github.com/korleinster/kakao-openchat-gamebot/issues"
}
