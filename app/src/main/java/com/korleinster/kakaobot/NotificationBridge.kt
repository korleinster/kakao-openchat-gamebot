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
    companion object { @Volatile var connected = false }
    override fun onListenerConnected() {
        connected = true
        val engine = BotEngine.get(this)
        engine.submit {
            engine.db.log("알림 접근 연결됨")
            activeNotifications?.forEach { inspect(it, false) }
            engine.tick()
        }
    }
    override fun onListenerDisconnected() {
        connected = false
        BotEngine.get(this).submit { val e = BotEngine.get(this); e.transport.clear(); e.db.log("알림 접근 연결 끊김"); e.report() }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) = inspect(sbn, true)
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.kakao.talk") return
        BotEngine.get(this).transport.sourceRemoved(sbn.key)
    }
    private fun inspect(sbn: StatusBarNotification, processCommand: Boolean) {
        if (sbn.packageName != "com.kakao.talk") return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val group = if (n.extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)) n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) else null
        val roomCandidates = listOf(n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE), n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT), n.extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT), if (group == true) n.extras.getCharSequence(Notification.EXTRA_TITLE) else null).mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
        val evidence = when {
            !n.shortcutId.isNullOrBlank() -> "shortcut:${n.shortcutId}"
            !sbn.tag.isNullOrBlank() -> "tag:${sbn.tag}:id:${sbn.id}"
            else -> "notification:${sbn.key}"
        }
        val identity = digest("${sbn.packageName}|${sbn.user.hashCode()}|$evidence")
        val engine = BotEngine.get(this)
        val prefs = engine.prefs
        val known = RoomAdmission.namedTarget(roomCandidates) || RoomAdmission.knownIdentity(identity, prefs.bound, prefs.candidate)
        // Kakao 26.9.1 omits the room title for 1:1 open chats. During a live pairing
        // window inspect only the latest text for the exact one-time code, never history.
        if (!known) {
            if (!RoomAdmission.pairingEligible(processCommand, group, roomCandidates.isNotEmpty(),
                    !n.shortcutId.isNullOrBlank() || !sbn.tag.isNullOrBlank(), prefs.code, prefs.codeExpires, System.currentTimeMillis())) return
            if (!RoomAdmission.matchesCode(n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(), prefs.code)) return
        }
        val style = if (Build.VERSION.SDK_INT >= 28) try { Notification.Builder.recoverBuilder(this, n).style as? Notification.MessagingStyle } catch (_: Exception) { null } else null
        val message = style?.messages?.lastOrNull { it.text != null }
        val text = message?.text?.toString() ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val timestamp = message?.timestamp?.takeIf { it > 0 } ?: sbn.postTime
        val actions = (n.actions?.toList() ?: emptyList()) + Notification.WearableExtender(n).actions
        val action = actions.firstOrNull { a -> a.actionIntent?.creatorPackage == "com.kakao.talk" && a.remoteInputs?.any { it.allowFreeFormInput } == true } ?: run {
            BotEngine.get(this).submit { BotEngine.get(this).db.log("시험 방 알림: 답장 기능 없음"); BotEngine.get(this).report() }; return
        }
        val eventId = digest("${sbn.key}|$timestamp|$text")
        val cap = ReplyCapability(identity, sbn.key, action)
        engine.submit { engine.onTargetMessage(identity, eventId, text, cap, processCommand) }
    }
}
