package com.korleinster.kakaobot

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ProductBehaviorTest {
    private val now=Instant.parse("2026-10-30T08:00:00Z")
    @Test fun allFiveTonesKeepPayloadFactsIntactAndHaveDistinctPreviews() {
        assertEquals(5,BotTone.entries.size)
        val payload="[반복 알림] 산책\n시작: 18:00\n결과: 37 / 100"
        BotTone.entries.forEach { tone ->
            assertTrue(BotMessages.render(tone,MessageKind.REMINDER,payload).endsWith(payload))
            assertEquals(BotMessages.preview(tone),BotMessages.preview(tone))
        }
        assertEquals(5,BotTone.entries.map { BotMessages.preview(it) }.distinct().size)
        assertEquals(5,BotTone.entries.map { BotMessages.render(it,MessageKind.PING,"") }.distinct().size)
    }
    @Test fun weeklyReminderAcceptsZeroLeadAndHasADeliveryWindow() {
        val d=Definition("zero","산책","weekly",LocalTime.of(18,0),leadMinutes=0)
        val n=Planner.plan(listOf(d),now,false).first { it.due.atZone(APP_ZONE).toLocalDate()==now.atZone(APP_ZONE).toLocalDate() }
        assertEquals(LocalTime.of(18,0),n.due.atZone(APP_ZONE).toLocalTime())
        assertTrue(n.expires>n.due);assertTrue(n.body.startsWith("[반복 알림]"))
    }
    @Test fun customLeadAndSummaryTimeAreNotHardcoded() {
        val d=Definition("ten","산책","weekly",LocalTime.of(18,0),leadMinutes=10)
        val planned=Planner.plan(listOf(d),now,true,briefingTime=LocalTime.of(17,30))
        assertEquals(LocalTime.of(17,50),planned.first { it.kind=="REMINDER" }.due.atZone(APP_ZONE).toLocalTime())
        assertEquals(LocalTime.of(17,30),planned.first { it.kind=="BRIEFING" }.due.atZone(APP_ZONE).toLocalTime())
    }
    @Test fun oldAndGenericMonthCyclesKeepTheSameCalendarAnchor() {
        val old=Definition("cycle","갱신","collection",anchor=LocalDateTime.of(2026,1,31,9,0),intervalMonths=2)
        val generic=old.copy(category="monthly")
        assertEquals(Planner.occurrences(listOf(old),now).map { it.start },Planner.occurrences(listOf(generic),now).map { it.start })
        assertTrue(Planner.plan(listOf(generic),now,false).all { it.body.startsWith("[주기 알림]") })
    }
    @Test fun consentIsRequiredEvenIfNotificationAccessWasAlreadyGranted() {
        val state=ConnectionState(true,true,true,false,true,false,"",0,true,null,consented=false)
        assertEquals(ConnectionPhase.CONSENT_REQUIRED,state.phase(0))
    }
}
