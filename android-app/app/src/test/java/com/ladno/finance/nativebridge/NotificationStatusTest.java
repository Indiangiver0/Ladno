package com.ladno.finance;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Pure JVM tests of the production monitor; no Android service or real burst is simulated. */
public final class NotificationStatusTest {
    @Test public void successAfterOverflowKeepsWarning() {
        NotificationStatus status = new NotificationStatus();
        AtomicReference<String> shown = new AtomicReference<>();
        status.overflow(() -> shown.set("overflow"));
        status.success(overflowed -> shown.set(overflowed ? "overflow" : null));
        assertEquals("overflow", shown.get());
    }

    @Test public void rejectionCannotPublishBetweenSuccessfulCheckAndClear() throws Exception {
        NotificationStatus status = new NotificationStatus();
        AtomicReference<String> shown = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch successfulCheck = new CountDownLatch(1);
        CountDownLatch releaseSuccessfulPublication = new CountDownLatch(1);
        Thread successful = new Thread(() -> {
            try {
                status.success(overflowed -> {
                    successfulCheck.countDown();
                    try {
                        if (!releaseSuccessfulPublication.await(5, TimeUnit.SECONDS)) throw new AssertionError("Publication not released");
                    } catch (InterruptedException e) { throw new AssertionError(e); }
                    shown.set(overflowed ? "overflow" : null);
                });
            } catch (Throwable e) { failure.set(e); }
        });
        Thread rejected = new Thread(() -> {
            try { status.overflow(() -> shown.set("overflow")); }
            catch (Throwable e) { failure.set(e); }
        });
        successful.start();
        try {
            assertTrue(successfulCheck.await(5, TimeUnit.SECONDS));
            rejected.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            // Observe actual monitor contention, rather than assuming a scheduling delay.
            while (rejected.isAlive() && rejected.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield();
            assertEquals(Thread.State.BLOCKED, rejected.getState());
        } finally {
            releaseSuccessfulPublication.countDown();
            successful.join(5000);
            if (rejected.getState() != Thread.State.NEW) rejected.join(5000);
        }
        assertFalse(successful.isAlive());
        assertFalse(rejected.isAlive());
        assertNull(failure.get());
        assertEquals("overflow", shown.get());
    }
}
