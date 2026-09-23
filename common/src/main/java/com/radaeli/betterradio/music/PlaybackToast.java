package com.radaeli.betterradio.music;

import java.util.Objects;

/** Tracks playback state and action messages for a short, non-refreshing toast. */
public final class PlaybackToast {
    public enum State {
        PREVIOUS("better_radio.status.previous"),
        NEXT("better_radio.status.next"),
        PLAYING("better_radio.status.playing"),
        PAUSED("better_radio.status.paused"),
        MUTED("better_radio.status.muted");

        private final String translationKey;

        State(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private static final long DISPLAY_MILLIS = 2_000L;
    private static final long FADE_MILLIS = 500L;
    private State state;
    private State observedState;
    private String trackId;
    private boolean paused;
    private long expiresAtMillis;
    private long actionUntilMillis;

    /** Returns true only when a new toast should be shown. */
    public boolean update(State newState, String newTrackId, boolean newPaused, long nowMillis) {
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(newTrackId, "newTrackId");
        boolean changed = newState != observedState
                || !newTrackId.equals(trackId)
                || newPaused != paused;
        observedState = newState;
        trackId = newTrackId;
        paused = newPaused;
        if (!changed) {
            return false;
        }
        if (nowMillis < actionUntilMillis) {
            state = newState;
            expiresAtMillis = nowMillis + DISPLAY_MILLIS;
            actionUntilMillis = expiresAtMillis;
            return true;
        }
        state = newState;
        expiresAtMillis = nowMillis + DISPLAY_MILLIS;
        return true;
    }

    /** Shows an action label and synchronizes passive state so rendering cannot replace it. */
    public void showAction(State action, State currentState, String currentTrackId,
                           boolean currentPaused, long nowMillis) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(currentTrackId, "currentTrackId");
        state = currentState == State.MUTED ? State.MUTED : action;
        observedState = currentState;
        trackId = currentTrackId;
        paused = currentPaused;
        expiresAtMillis = nowMillis + DISPLAY_MILLIS;
        actionUntilMillis = expiresAtMillis;
    }

    public void clear() {
        state = null;
        observedState = null;
        trackId = null;
        paused = false;
        expiresAtMillis = 0L;
        actionUntilMillis = 0L;
    }

    public boolean isVisible(long nowMillis) {
        return state != null && nowMillis < expiresAtMillis;
    }

    /** Returns the vanilla-style fade alpha for the final half-second of the toast. */
    public int alpha(long nowMillis) {
        if (!isVisible(nowMillis)) {
            return 0;
        }
        long remainingMillis = expiresAtMillis - nowMillis;
        if (remainingMillis >= FADE_MILLIS) {
            return 255;
        }
        return (int) (255L * remainingMillis / FADE_MILLIS);
    }

    public State state() {
        return state;
    }
}
