package com.korleinster.kakaobot

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DomainTest {
    private fun korean(s: String) = LocalDateTime.parse(s).atZone(APP_ZONE).toInstant()
    private fun boss(time: String="12:00", revision: Int=1) = Definition("boss-1","시험 월드보스","world_boss",LocalTime.parse(time), revision=revision)
    @Test fun reminderIsDueExactlyFiveMinutesBeforeAndExpiresAfterOneMinute() {
        val now=korean("2026-10-29T11:55:00")
        val n=Planner.plan(listOf(boss()),now,false).single { it.due==now }
        assertEquals(now.plusSeconds(60),n.expires)
        assertFalse(DispatchRules.canClaim(DispatchState.PENDING,n.due,n.expires,now.minusNanos(1)))
        assertTrue(DispatchRules.canClaim(DispatchState.PENDING,n.due,n.expires,now))
        assertFalse(DispatchRules.canClaim(DispatchState.PENDING,n.due,n.expires,now.plusSeconds(60)))
    }
    @Test fun oldRemindersDoNotReplayAfterStartingLate() {
        val now=korean("2026-10-29T11:57:00")
        assertFalse(Planner.plan(listOf(boss()),now,false).any { it.id.contains("2026-10-29") })
    }
    @Test fun koreanMorningUsesKoreanDateAndDoesNotListAlreadyStartedEvents() {
        val now=korean("2026-10-29T07:01:00")
        val defs=listOf(boss("06:00"),Definition("c","정기","content",LocalTime.of(20,0)))
        val n=Planner.plan(defs,now,true).single { it.id=="briefing:2026-10-29" }
        assertEquals(korean("2026-10-29T07:00:00"),n.due)
        assertFalse(n.body.contains("06:00"));assertTrue(n.body.contains("20:00"))
    }
    @Test fun weekdayRuleDoesNotMoveWithUtcDateBoundary() {
        val d=boss("00:05").copy(weekdays=setOf(DayOfWeek.THURSDAY))
        val now=Instant.parse("2026-10-28T14:59:59Z")
        assertEquals(Instant.parse("2026-10-28T15:05:00Z"),Planner.occurrences(listOf(d),now).first { it.start>now }.start)
    }
    @Test fun monthEndCyclesAlwaysUseOriginalAnchorIncludingLeapYear() {
        val d=Definition("col","공통 컬렉션","collection",anchor=LocalDateTime.parse("2024-01-31T05:00"),intervalMonths=1,noticeDays=3)
        val occurrences=Planner.occurrences(listOf(d),korean("2024-02-20T00:00"))
        assertEquals(korean("2024-02-29T05:00"),occurrences.single { it.key.endsWith(":1") }.start)
        assertEquals(korean("2024-03-31T05:00"),occurrences.single { it.key.endsWith(":2") }.start)
    }
    @Test fun twoAndFourMonthsAreCalendarMonthsNotSixtyOrOneHundredTwentyDays() {
        for(interval in listOf(2L,4L)) {
            val d=Definition("c$interval","공통","collection",anchor=LocalDateTime.parse("2026-01-31T05:00"),intervalMonths=interval,noticeDays=0)
            val event=Planner.occurrences(listOf(d),korean("2026-02-01T00:00")).single { it.key.endsWith(":1") }
            assertEquals(korean(if(interval==2L) "2026-03-31T05:00" else "2026-05-31T05:00"),event.start)
        }
    }
    @Test fun changingTimePreservesLogicalIdentityAndChangesRevisionAndDue() {
        val now=korean("2026-10-29T01:00")
        val before=Planner.plan(listOf(boss()),now,false).first()
        val after=Planner.plan(listOf(boss("13:00",2)),now,false).first()
        assertEquals(before.id,after.id);assertNotEquals(before.due,after.due);assertEquals(2,after.revision)
    }
    @Test fun disabledAndEmptySchedulesDoNotCreateUnrequestedMorningMessages() {
        assertTrue(Planner.plan(emptyList(),Instant.now(),true).isEmpty())
        assertTrue(Planner.plan(listOf(boss().copy(enabled=false)),Instant.now(),true).isEmpty())
    }
    @Test fun dispatchLossIsUnknownAndNeverAutomaticallyClaimable() {
        val now=Instant.now()
        assertEquals(DispatchState.DELIVERY_UNKNOWN,DispatchRules.afterProcessLoss(DispatchState.DISPATCHING))
        for(state in listOf(DispatchState.DELIVERY_UNKNOWN,DispatchState.REQUEST_ACCEPTED,DispatchState.CANCELLED)) {
            assertFalse(DispatchRules.canClaim(state,now.minusSeconds(5),now.plusSeconds(30),now))
            assertEquals(state,DispatchRules.afterProcessLoss(state))
        }
        assertEquals(DispatchState.PENDING,DispatchRules.afterProcessLoss(DispatchState.CLAIMED))
    }
    @Test fun raffleUsesOnlyExplicitUniqueCandidatesAndDiceRejectsInvalidBounds() {
        repeat(100) {
            val reply=GameCommands.reply("!뽑기 철수 영희 민수")!!
            assertTrue(listOf("철수","영희","민수").any { name->reply.startsWith("뽑기 결과: $name\n") })
            val roll=GameCommands.reply("!주사위 100")!!.substringAfter(": ").substringBefore(" /").toInt()
            assertTrue(roll in 1..100)
        }
        assertTrue(GameCommands.reply("!뽑기 철수 철수")!!.contains("서로 다른"))
        assertTrue(GameCommands.reply("!주사위 -1")!!.startsWith("사용법"))
        assertNull(GameCommands.reply("일반 대화"))
    }
    @Test fun weeklyEventBeyondTwoDaysStillCreatesAFutureWakeup() {
        val d=boss().copy(weekdays=setOf(DayOfWeek.WEDNESDAY))
        val planned=Planner.plan(listOf(d),korean("2026-10-29T10:00"),false)
        assertTrue(planned.any { it.due==korean("2026-11-04T11:55") })
    }
    @Test fun distantCollectionIsPlannedEvenWithoutAnotherNearTermEvent() {
        val d=Definition("future","컬렉션","collection",anchor=LocalDateTime.parse("2026-12-31T05:00"),intervalMonths=4,noticeDays=3)
        val planned=Planner.plan(listOf(d),korean("2026-10-29T10:00"),false)
        assertTrue(planned.any { it.due==korean("2026-12-28T05:00") })
    }

}
