package com.korleinster.kakaobot

import org.junit.Assert.*
import org.junit.Test

class MultiRoomPolicyTest {
    @Test fun everyFeatureCanBeEnabledIndependently() {
        for(mask in 0 until (1 shl BotFeature.entries.size)) {
            val enabled=BotFeature.entries.filterIndexed { index,_ -> mask and (1 shl index) != 0 }.toSet()
            assertEquals(BotFeature.DICE in enabled,FeaturePolicy.allows(enabled,"!주사위 100"))
            assertEquals(BotFeature.DRAW in enabled,FeaturePolicy.allows(enabled,"!뽑기 가 나"))
            assertEquals(BotFeature.CYCLE_REMINDERS in enabled,FeaturePolicy.allows(enabled,"!주기"))
            assertEquals(FeaturePolicy.hasAlerts(enabled),FeaturePolicy.allows(enabled,"!오늘"))
            assertTrue(FeaturePolicy.allows(enabled,"!핑"));assertTrue(FeaturePolicy.allows(enabled,"!도움말"))
        }
    }
    @Test fun helpContainsOnlyEnabledCommands() {
        val dice=setOf(BotFeature.DICE)
        assertTrue(FeaturePolicy.help(dice).contains("!주사위"))
        assertFalse(FeaturePolicy.help(dice).contains("!뽑기"));assertFalse(FeaturePolicy.help(dice).contains("!알림"))
        assertFalse(FeaturePolicy.allows(dice,"!월보"))
    }
    @Test fun migrationPreservesPreviousRoleAndMorningChoices() {
        assertEquals(setOf(BotFeature.DICE,BotFeature.DRAW),FeaturePolicy.migrateRole("FUN",true))
        assertFalse(BotFeature.DICE in FeaturePolicy.migrateRole("GAME_ALERTS",false))
        assertTrue(BotFeature.DAILY_SUMMARY in FeaturePolicy.migrateRole("MIXED",true))
        assertFalse(BotFeature.DAILY_SUMMARY in FeaturePolicy.migrateRole("MIXED",false))
    }
    @Test fun parserHandlesWhitespaceWithoutAdmittingSimilarCommands() {
        assertTrue(FeaturePolicy.allows(setOf(BotFeature.DICE),"  !주사위\t100"))
        assertFalse(FeaturePolicy.allows(BotFeature.entries.toSet(),"!핑몰래"))
    }
    @Test fun selectedRoomDoesNotAffectConversationRouting() {
        val bindings=listOf("local-a" to "chat-a", "local-b" to "chat-b")
        assertEquals("local-b",RoomRouting.owner("chat-b",bindings))
        assertNull(RoomRouting.owner("other-chat",bindings));assertNull(RoomRouting.owner("",listOf("local-a" to "")))
    }
    @Test fun duplicateConversationBindingsAreAmbiguous() {
        val bindings=listOf("local-a" to "same-chat", "local-b" to "same-chat")
        assertTrue(RoomRouting.ambiguous("same-chat",bindings));assertNull(RoomRouting.owner("same-chat",bindings))
    }
}
