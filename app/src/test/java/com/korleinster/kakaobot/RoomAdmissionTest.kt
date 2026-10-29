package com.korleinster.kakaobot

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer

class RoomAdmissionTest {
    @Test fun unnamedConversationNeedsLiveCodeStableIdentityAndNewNotification() {
        fun eligible(process: Boolean = true, wrongRoom: Boolean = false,
                     stable: Boolean = true, expires: Long = 200) =
            RoomAdmission.pairingEligible(process, stable, "123456", expires, 100, wrongRoom)
        assertTrue(eligible())
        assertFalse(eligible(process = false))
        assertFalse(eligible(wrongRoom = true))
        assertFalse(eligible(stable = false))
        assertFalse(eligible(expires = 100))
    }
    @Test fun onlyExactOneTimeCodeMatchesWithoutReusingEmptyCode() {
        assertTrue(RoomAdmission.matchesCode(" !연결 123456 ", "123456"))
        assertFalse(RoomAdmission.matchesCode("!연결 1234567", "123456"))
        assertFalse(RoomAdmission.matchesCode("다른 메시지 !연결 123456", "123456"))
        assertFalse(RoomAdmission.matchesCode("!연결 ", ""))
        assertFalse(RoomAdmission.matchesCode(null, "123456"))
    }
    @Test fun identityAndRoomNameStayScopedAfterPairing() {
        assertTrue(RoomAdmission.knownIdentity("one", "one", ""))
        assertFalse(RoomAdmission.knownIdentity("two", "one", ""))
        assertFalse(RoomAdmission.knownIdentity("", "", ""))
        assertTrue(RoomAdmission.namedTarget(listOf(Normalizer.normalize(DEFAULT_TARGET_ROOM, Normalizer.Form.NFD)), DEFAULT_TARGET_ROOM))
        assertFalse(RoomAdmission.namedTarget(listOf("봇테스트2", "다른 방"), DEFAULT_TARGET_ROOM))
    }
    @Test fun configuredGroupNameReplacesTheDefaultFilterWithoutAdmittingOtherRooms() {
        val target = "별도 시험방"
        assertTrue(RoomAdmission.namedTarget(listOf("참여자", target), target))
        assertFalse(RoomAdmission.namedTarget(listOf(DEFAULT_TARGET_ROOM), target))
        assertFalse(RoomAdmission.namedTarget(listOf("별도 시험방2"), target))
        assertTrue(RoomAdmission.namedTarget(listOf(Normalizer.normalize(target, Normalizer.Form.NFD)), target))
    }
    @Test fun targetNameNormalizesWhitespaceAndRejectsInvalidNames() {
        assertEquals("다른 방", RoomAdmission.normalizeName(" 다른 방 "))
        for (name in listOf(" ", "a".repeat(81), "다른\n방")) {
            try { RoomAdmission.normalizeName(name); fail("Invalid target accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun groupSenderNameIsNotRequiredToMatchTheConfiguredRoom() {
        // Real Kakao group notifications can expose a sender title but no room title.
        assertTrue(RoomAdmission.pairingEligible(true, true, "123456", 200, 100))
        assertTrue(RoomAdmission.matchesCode("!연결 123456", "123456"))
        assertFalse(RoomAdmission.pairingEligible(true, true, "123456", 200, 100, explicitRoomMismatch=true))
        assertFalse(RoomAdmission.pairingEligible(false, true, "123456", 200, 100))
    }
    @Test fun activeNotificationCanRecoverOnlyTheCurrentPairingWindow() {
        assertTrue(RoomAdmission.currentPairingPost(false, 1000, 301_000, 2000))
        assertFalse(RoomAdmission.currentPairingPost(false, 999, 301_000, 2000))
        assertFalse(RoomAdmission.currentPairingPost(false, 1000, 301_000, 301_000))
        assertFalse(RoomAdmission.currentPairingPost(false, 3000, 301_000, 2000))
    }
    @Test fun onlyFreeFormTextInputsCanBecomeReplyCapabilities() {
        assertTrue(ReplyInputPolicy.usable(true,false,"reply"))
        assertFalse(ReplyInputPolicy.usable(false,false,"reply"))
        assertFalse(ReplyInputPolicy.usable(true,true,"reply"))
        assertFalse(ReplyInputPolicy.usable(true,false,""))
    }
}
