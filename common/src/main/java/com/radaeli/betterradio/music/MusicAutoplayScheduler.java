package com.radaeli.betterradio.music;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Loader-independent timer for startup delay, between-track intervals and pause blocking. */
public final class MusicAutoplayScheduler {
    public enum Tick { PAUSED, BLOCKED, PLAYING, STARTING, INTERVAL, STARTED, NO_TRACK }

    private int remainingTicks = -1;
    private boolean observedPlayback;

    public void startSession(boolean paused, int initialDelayTicks) {
        remainingTicks = paused ? -1 : Math.max(0, initialDelayTicks);
        observedPlayback = false;
    }

    /** Manual playback counts as playback for the next natural completion. */
    public void manualStart() {
        observedPlayback = true;
        remainingTicks = -1;
    }

    public void pause() {
        observedPlayback = false;
        remainingTicks = -1;
    }

    /** Replaces a pending silence interval after its preference changes. */
    public void reschedule(int delayTicks) {
        if (!observedPlayback && remainingTicks >= 0) remainingTicks = Math.max(0, delayTicks);
    }

    public void reset() {
        remainingTicks = -1;
        observedPlayback = false;
    }

    public int remainingTicks() { return remainingTicks; }
    public boolean observedPlayback() { return observedPlayback; }

    public Tick tick(boolean paused, boolean hasPlayback, boolean mayAdvance,
                     IntSupplier initialDelay, IntSupplier nextInterval,
                     BooleanSupplier startNext, int retryDelayTicks) {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(nextInterval, "nextInterval");
        Objects.requireNonNull(startNext, "startNext");
        if (paused) {
            pause();
            return Tick.PAUSED;
        }
        if (hasPlayback) {
            observedPlayback = true;
            remainingTicks = -1;
            return Tick.PLAYING;
        }
        if (!mayAdvance) return Tick.BLOCKED;

        Tick waitingState;
        if (observedPlayback) {
            observedPlayback = false;
            remainingTicks = Math.max(0, nextInterval.getAsInt());
            waitingState = Tick.INTERVAL;
        } else if (remainingTicks < 0) {
            remainingTicks = Math.max(0, initialDelay.getAsInt());
            waitingState = Tick.STARTING;
        } else {
            waitingState = Tick.STARTING;
        }

        if (remainingTicks > 0) {
            remainingTicks--;
            return waitingState;
        }
        if (startNext.getAsBoolean()) {
            manualStart();
            return Tick.STARTED;
        }
        remainingTicks = Math.max(1, retryDelayTicks);
        return Tick.NO_TRACK;
    }
}
