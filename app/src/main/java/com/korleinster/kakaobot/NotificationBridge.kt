/* Portions adapted from bssm-oss/kakao-talk-auto-bot (MIT), commit a10b27b.
 * Copyright (c) 2026 kakao-talk-auto-bot contributors.
 * Original NotificationListener.kt; permission text in licenses/.
 */
package com.korleinster.kakaobot

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationBridge : NotificationListenerService() {
    companion object {
        @Volatile var connected = false
        @Volatile private var current: NotificationBridge? = null
        fun refreshCurrent() { current?.refreshActive() }
    }
    private fun refreshActive() {
        val engine = BotEngine.get(this)
        if(!engine.consentAccepted()) return
        engine.submit { activeNotifications?.forEach { inspect(it, false) }; engine.tick() }
    }
    override fun onListenerConnected() {
        connected = true; current = this
        val engine = BotEngine.get(this)
        engine.submit {
            engine.db.log("알림 접근 연결됨")
            activeNotifications?.forEach { inspect(it, false) }
            engine.tick()
        }
    }
    override fun onListenerDisconnected() {
        connected = false
        BotEngine.get(this).submit { val e = BotEngine.get(this); e.clearReplyCapabilities(); e.db.log("알림 접근 연결 끊김"); e.report() }
    }
    override fun onDestroy() {
        connected = false; if(current === this) current = null
        val engine = BotEngine.get(this)
        engine.submit { engine.clearReplyCapabilities(); engine.report() }
        super.onDestroy()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) { BotEngine.get(this).submit { inspect(sbn, true) } }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.kakao.talk") return
        val engine = BotEngine.get(this)
        engine.submit { if (engine.rooms().map { it.transport.sourceRemoved(sbn.key) }.any { it }) engine.report() }
    }
    private fun inspect(sbn: StatusBarNotification, processCommand: Boolean) {
        if (sbn.packageName != "com.kakao.talk") return
        if(!BotEngine.get(this).consentAccepted()) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // EXTRA_TITLE can contain only the sender even when isGroupConversation=true.
        // Only a structured conversation title is evidence of the room name.
        val conversationTitle = n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()?.takeIf(String::isNotBlank)
        val evidence = when {
            !n.shortcutId.isNullOrBlank() -> "shortcut:${n.shortcutId}"
            !sbn.tag.isNullOrBlank() -> "tag:${sbn.tag}:id:${sbn.id}"
            else -> "notification:${sbn.key}"
        }
        val identity = digest("${sbn.packageName}|${sbn.user.hashCode()}|$evidence")
        val root = BotEngine.get(this)
        val rooms = root.rooms()
        val now = System.currentTimeMillis()
        val bindings = rooms.map { it.roomId to it.prefs.bound } + rooms.filter { it.prefs.candidate.isNotBlank() }.map { it.roomId to it.prefs.candidate }
        if(RoomRouting.ambiguous(identity, bindings)) {
            rooms.filter { it.prefs.bound == identity || it.prefs.candidate == identity }.forEach { it.prefs.collision = true; it.transport.clear() }
            root.report(); return
        }
        val owner = RoomRouting.owner(identity, bindings)?.let { id -> rooms.first { it.roomId == id } }
        val pairing = rooms.singleOrNull { it.prefs.code.isNotBlank() && now < it.prefs.codeExpires }
        // Unknown conversations are inspected only while one explicit pairing is active.
        if(owner == null && pairing == null) return
        val style = if (Build.VERSION.SDK_INT >= 28) try { Notification.Builder.recoverBuilder(this, n).style as? Notification.MessagingStyle } catch (_: Exception) { null } else null
        val message = style?.messages?.lastOrNull { it.text != null }
        val text = message?.text?.toString() ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        // Only the latest text can consume the currently active pairing code.
        val pairingPost = pairing?.let { RoomAdmission.currentPairingPost(processCommand, sbn.postTime, it.prefs.codeExpires, now) } == true
        val matchesPair = pairingPost && pairing != null && RoomAdmission.matchesCode(text, pairing.prefs.code)
        if(matchesPair && owner != null && owner.roomId != pairing!!.roomId) {
            pairing.pairingReplyUnavailable(pairing.prefs.code, "중복 대화 연결 차단", "이미 다른 방 설정에 연결된 대화예요. 기존 방 설정에서 사용할 기능을 바꿔 주세요.")
            return
        }
        val engine = if(matchesPair) pairing!! else owner ?: return
        val prefs = engine.prefs
        val pairingMessage = matchesPair
        if(owner == null) {
            val titleMatches = conversationTitle?.let { RoomAdmission.namedTarget(listOf(it), prefs.targetRoom) } == true
            if(!RoomAdmission.pairingEligible(pairingPost,
                    !n.shortcutId.isNullOrBlank() || !sbn.tag.isNullOrBlank(), prefs.code, prefs.codeExpires, now,
                    conversationTitle != null && !titleMatches)) return
        }
        val currentCode = prefs.code
        val timestamp = message?.timestamp?.takeIf { it > 0 } ?: sbn.postTime
        val actions = (n.actions?.toList() ?: emptyList()) + Notification.WearableExtender(n).actions
        val action = actions.firstOrNull { a ->
            a.actionIntent?.creatorPackage == "com.kakao.talk" && a.remoteInputs?.any { ReplyInputPolicy.usable(it.allowFreeFormInput, it.isDataOnly, it.resultKey) } == true
        } ?: run {
            val diagnostics = "답장 검사 " + actions.mapIndexed { index, a -> "$index:k${if(a.actionIntent?.creatorPackage == "com.kakao.talk") 1 else 0}/r${a.remoteInputs?.size ?: 0}/f${a.remoteInputs?.count { it.allowFreeFormInput } ?: 0}" }.joinToString(" ")
            val hasTextInput = actions.any { a -> a.remoteInputs?.any { ReplyInputPolicy.usable(it.allowFreeFormInput,it.isDataOnly,it.resultKey) } == true }
            val reason = if (hasTextInput) "답장 버튼의 카카오톡 소유자 정보를 확인할 수 없어." else "이 알림에서 사용할 텍스트 답장 기능을 확보하지 못했어."
            if (pairingMessage) engine.pairingReplyUnavailable(currentCode, diagnostics, reason)
            else { engine.db.log("대상 방 알림: 답장 기능 없음"); engine.report() }
            return
        }
        val eventId = digest("${sbn.key}|$timestamp|$text")
        val cap = ReplyCapability(identity, sbn.key, action)
        engine.onTargetMessage(identity, eventId, text, cap, processCommand || pairingMessage)
    }
}
