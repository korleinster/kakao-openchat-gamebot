package com.korleinster.kakaobot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DeliveryDatabaseTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var name:String
    private lateinit var db:BotDb
    @Before fun setup() { name="disposable-${UUID.randomUUID()}.db";db=BotDb(context,name) }
    @After fun cleanup() { db.close();context.deleteDatabase(name) }
    private fun notice(id:String="one",source:String?=null,revision:Int=0):Notice { val now=Instant.now();return Notice(id,source,revision,now.minusSeconds(1),now.plusSeconds(300),"시험", "TEST") }
    @Test fun onlyOneConcurrentWorkerCanClaimTheSameDelivery() {
        val n=notice();db.enqueue(n,1)
        val start=CountDownLatch(1);val done=CountDownLatch(12);val wins=AtomicInteger();val pool=Executors.newFixedThreadPool(12)
        repeat(12) { pool.execute { try { start.await();if(db.claim(n.id,Instant.now(),1))wins.incrementAndGet() } finally { done.countDown() } } }
        start.countDown();assertTrue(done.await(10,TimeUnit.SECONDS));pool.shutdownNow()
        assertEquals(1,wins.get())
    }
    @Test fun interruptedDispatchSurvivesDatabaseReopenAsUnknown() {
        val n=notice();db.enqueue(n,1);assertTrue(db.claim(n.id,Instant.now(),1));assertTrue(db.markDispatching(db.find(n.id)!!,Instant.now()))
        db.close();db=BotDb(context,name);db.recoverAfterProcessLoss()
        assertEquals(DispatchState.DELIVERY_UNKNOWN,db.find(n.id)!!.state)
        assertFalse(db.claim(n.id,Instant.now(),1))
    }
    @Test fun cancelledOrRevisedSourceCannotDispatchAClaimedStaleNotice() {
        val source=Definition("source","일정","content",LocalTime.NOON)
        db.saveDefinition(source)
        val n=notice(source=source.id,revision=1);db.enqueue(n,1);assertTrue(db.claim(n.id,Instant.now(),1))
        db.saveDefinition(source.copy(revision=2))
        assertFalse(db.markDispatching(db.find(n.id)!!,Instant.now()))
        assertNotEquals(DispatchState.DISPATCHING,db.find(n.id)!!.state)
    }
    @Test fun reconciliationCannotReplayAlreadyRequestedDelivery() {
        val n=notice().copy(kind="REMINDER");db.enqueue(n,1);db.mark(n.id,DispatchState.REQUEST_ACCEPTED)
        db.reconcile(listOf(n.copy(body="변경됨",revision=2)),2)
        assertEquals(DispatchState.REQUEST_ACCEPTED,db.find(n.id)!!.state)
        assertFalse(db.claim(n.id,Instant.now(),2))
    }
    @Test fun cancelledStaleAlarmAndDuplicateInputAreSuppressed() {
        val n=notice();db.enqueue(n,1);db.cancelAll();assertFalse(db.claim(n.id,Instant.now(),1))
        assertTrue(db.observe("synthetic-message-hash",Instant.now()));assertFalse(db.observe("synthetic-message-hash",Instant.now()))
    }
}
