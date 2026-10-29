/* Portions adapted from bssm-oss/kakao-talk-auto-bot (MIT), commit a10b27b.
 * Copyright (c) 2026 kakao-talk-auto-bot contributors.
 * Original SessionManager.kt and SessionReplier.kt; permission text in licenses/.
 */
package com.korleinster.kakaobot

import android.app.KeyguardManager
import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

class ReplyCapability(val identity: String, val notificationKey: String, val action: Notification.Action, @Volatile var sourcePresent: Boolean = true)
class SessionTransport(private val context: Context) {
    private val capabilities = ConcurrentHashMap<String, ReplyCapability>()
    fun accept(cap: ReplyCapability) { capabilities[cap.identity] = cap }
    fun sourceRemoved(key: String): Boolean {
        val matching = capabilities.values.filter { it.notificationKey == key }
        matching.forEach { it.sourcePresent = false }
        return matching.isNotEmpty()
    }
    fun sourcePresent(identity: String): Boolean = capabilities[identity]?.sourcePresent == true
    fun clear() = capabilities.clear()
    fun available(identity: String): Boolean = identity.isNotBlank() && capabilities.containsKey(identity)
    fun check(identity: String): String? {
        val cap = capabilities[identity] ?: return "연결 대기"
        if (cap.action.actionIntent.creatorPackage != "com.kakao.talk") return "대상 검증 실패"
        if (cap.action.remoteInputs.isNullOrEmpty()) return "답장 기능 없음"
        if (!NotificationBridge.connected) return "알림 연결 대기"
        if (Build.VERSION.SDK_INT >= 31 && cap.action.isAuthenticationRequired && context.getSystemService(KeyguardManager::class.java).isDeviceLocked) return "폰 잠금 해제 필요"
        return null
    }
    /** Called only after the durable DISPATCHING marker. A normal return is not a delivery receipt. */
    fun invoke(identity: String, text: String): Boolean {
        val cap = capabilities[identity] ?: return false
        val inputs = cap.action.remoteInputs?.filterNot { it.isDataOnly }?.toTypedArray() ?: return false
        if (cap.action.actionIntent.creatorPackage != "com.kakao.talk" || inputs.isEmpty()) return false
        val result = Bundle()
        inputs.forEach { result.putCharSequence(it.resultKey, text) }
        val intent = Intent()
        RemoteInput.addResultsToIntent(inputs, intent, result)
        cap.action.actionIntent.send(context, 0, intent)
        return true
    }
}
