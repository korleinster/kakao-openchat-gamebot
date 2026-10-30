package com.korleinster.kakaobot

import org.junit.Assert.*
import org.junit.Test

class MultiRoomPolicyTest {
    @Test fun entertainmentRoomCannotExecuteScheduleCommands() {
        assertTrue(RoomRole.FUN.allows("!주사위 100"))
        assertTrue(RoomRole.FUN.allows("!뽑기 가 나"))
        listOf("!오늘","!월보","!정기","!컬렉션","!알림테스트").forEach { assertFalse(RoomRole.FUN.allows(it)) }
        assertFalse(RoomRole.FUN.help().contains("!월보"))
    }
    @Test fun announcementRoomDoesNotExecuteGames() {
        assertTrue(RoomRole.GAME_ALERTS.allows("!월보"))
        assertFalse(RoomRole.GAME_ALERTS.allows("!뽑기 가 나"))
        assertFalse(RoomRole.GAME_ALERTS.help().contains("!주사위"))
    }
    @Test fun mixedRolePreservesExistingCommandsAndRejectsUnknownOnes() {
        listOf("!핑","!도움말","!주사위 100","!오늘","!알림테스트").forEach { assertTrue(RoomRole.MIXED.allows(it)) }
        assertFalse(RoomRole.MIXED.allows("!unknown"))
        assertFalse(RoomRole.MIXED.allows("!핑몰래"))
    }
    @Test fun selectedRoomDoesNotAffectConversationRouting() {
        val bindings=listOf("local-a" to "chat-a", "local-b" to "chat-b")
        assertEquals("local-b",RoomRouting.owner("chat-b",bindings))
        assertNull(RoomRouting.owner("other-chat",bindings))
        assertNull(RoomRouting.owner("",listOf("local-a" to "")))
    }
    @Test fun duplicateConversationBindingsAreAmbiguous() {
        val bindings=listOf("local-a" to "same-chat", "local-b" to "same-chat")
        assertTrue(RoomRouting.ambiguous("same-chat",bindings))
        assertNull(RoomRouting.owner("same-chat",bindings))
    }
}
