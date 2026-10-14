package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MusicAutoplaySchedulerTest {
    @Test public void startupDelayRunsBeforeFirstTrack() {
        MusicAutoplayScheduler scheduler = new MusicAutoplayScheduler();
        scheduler.startSession(false, 2);
        AtomicInteger starts = new AtomicInteger();
        assertEquals(MusicAutoplayScheduler.Tick.STARTING,
                scheduler.tick(false, false, true, () -> 100, () -> 20, () -> { starts.incrementAndGet(); return true; }, 5));
        assertEquals(MusicAutoplayScheduler.Tick.STARTING,
                scheduler.tick(false, false, true, () -> 100, () -> 20, () -> { starts.incrementAndGet(); return true; }, 5));
        assertEquals(MusicAutoplayScheduler.Tick.STARTED,
                scheduler.tick(false, false, true, () -> 100, () -> 20, () -> { starts.incrementAndGet(); return true; }, 5));
        assertEquals(1, starts.get());
    }

    @Test public void pauseNeverStartsOrConsumesThePendingInterval() {
        MusicAutoplayScheduler scheduler = new MusicAutoplayScheduler();
        scheduler.startSession(false, 0);
        assertEquals(MusicAutoplayScheduler.Tick.STARTED,
                scheduler.tick(false, false, true, () -> 0, () -> 4, () -> true, 3));
        assertEquals(MusicAutoplayScheduler.Tick.PAUSED,
                scheduler.tick(true, false, true, () -> 0, () -> 4, () -> failStart(), 3));
        assertEquals(-1, scheduler.remainingTicks());
        assertEquals(MusicAutoplayScheduler.Tick.BLOCKED,
                scheduler.tick(false, false, false, () -> 0, () -> 4, () -> failStart(), 3));
        assertEquals(MusicAutoplayScheduler.Tick.STARTING,
                scheduler.tick(false, false, true, () -> 2, () -> 4, () -> failStart(), 3));
    }

    @Test public void naturalCompletionUsesIndependentIntervalSupplier() {
        MusicAutoplayScheduler scheduler = new MusicAutoplayScheduler();
        scheduler.startSession(false, 10);
        assertEquals(MusicAutoplayScheduler.Tick.PLAYING,
                scheduler.tick(false, true, true, () -> 1, () -> 2, () -> false, 4));
        assertEquals(MusicAutoplayScheduler.Tick.INTERVAL,
                scheduler.tick(false, false, true, () -> 10, () -> 3, () -> false, 4));
        assertEquals(2, scheduler.remainingTicks());
    }

    private static boolean failStart() { fail("pause must block playback"); return false; }
}
