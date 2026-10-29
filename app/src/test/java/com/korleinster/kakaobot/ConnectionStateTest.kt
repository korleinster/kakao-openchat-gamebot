package com.korleinster.kakaobot

import org.junit.Assert.*
import org.junit.Test

class ConnectionStateTest {
    private val waiting = ConnectionState(true, true, false, false, false, false, "123456", 300_000, false, "연결 대기")
    @Test fun permissionAndRoomPairingAreDistinctRatherThanPretendingTheRoomIsConnected() {
        assertEquals(ConnectionPhase.PAIRING, waiting.phase(0))
        assertEquals(ConnectionPhase.ACCESS_REQUIRED, waiting.copy(accessGranted=false).phase(0))
        assertEquals(ConnectionPhase.LISTENER_WAITING, waiting.copy(listenerConnected=false).phase(0))
    }
    @Test fun countdownExpiresInPlaceAndCandidateReceptionChangesPhaseImmediately() {
        assertEquals(300L, waiting.remainingSeconds(0))
        assertEquals(299L, waiting.remainingSeconds(1000))
        assertEquals(ConnectionPhase.EXPIRED, waiting.phase(300_000))
        assertEquals(0L, waiting.remainingSeconds(400_000))
        val received = waiting.copy(candidate=true, code="")
        assertEquals(ConnectionPhase.CANDIDATE, received.phase(10))
        assertEquals(0L, received.remainingSeconds(10))
    }
    @Test fun confirmationIsSeparateFromReplyCapabilityAndCommandExecution() {
        val paired = waiting.copy(bound=true, code="", hasReply=true, replyBlock=null)
        assertEquals(ConnectionPhase.PAUSED, paired.phase(0))
        assertEquals(ConnectionPhase.READY, paired.copy(running=true).phase(0))
        assertEquals(ConnectionPhase.SESSION_WAITING, paired.copy(running=true,hasReply=false).phase(0))
        assertEquals(ConnectionPhase.BLOCKED, paired.copy(running=true,replyBlock="폰 잠금 해제 필요").phase(0))
        assertEquals(ConnectionPhase.COLLISION, paired.copy(running=true,collision=true).phase(0))
    }
    @Test fun receivedCodeWithoutReplyCapabilityIsShownAsBlockedInsteadOfWaitingForever() {
        val blocked=waiting.copy(pairingIssue="답장 입력 없음")
        assertEquals(ConnectionPhase.REPLY_UNAVAILABLE,blocked.phase(1000))
        assertEquals(ConnectionPhase.EXPIRED,blocked.phase(300_000))
    }
}
