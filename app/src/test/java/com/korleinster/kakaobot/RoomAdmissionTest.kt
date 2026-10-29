package com.korleinster.kakaobot

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer

class RoomAdmissionTest {
    @Test fun unnamedOneToOneNeedsLiveCodeStableIdentityAndNewNotification() {
        fun eligible(process: Boolean = true, group: Boolean? = false, room: Boolean = false,
                     stable: Boolean = true, expires: Long = 200) =
            RoomAdmission.pairingEligible(process, group, room, stable, "123456", expires, 100)
        assertTrue(eligible())
        assertFalse(eligible(process = false))
        assertFalse(eligible(group = true))
        assertFalse(eligible(group = null))
        assertFalse(eligible(room = true))
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
        assertTrue(RoomAdmission.namedTarget(listOf(Normalizer.normalize(TEST_ROOM, Normalizer.Form.NFD))))
        assertFalse(RoomAdmission.namedTarget(listOf("봇테스트2", "다른 방")))
    }
}
