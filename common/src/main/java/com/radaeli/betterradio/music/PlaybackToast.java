package com.radaeli.betterradio.music;

import java.util.Objects;

/** Tracks state changes for a short, non-refreshing playback toast. */
public final class PlaybackToast {
    public enum State { PLAYING, PAUSED, MUTED }

    private static final long DISPLAY_MILLIS = 2_000L;
    private State state;
    private String trackId;
    private boolean paused;
    private long expiresAtMillis;

    /** Returns true only when a new toast should be shown. */
    public boolean update(State newState, String newTrackId, boolean newPaused, long nowMillis) {
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(newTrackId, "newTrackId");
        if (newState == state && newTrackId.equals(trackId) && newPaused == paused) {
            return false;
        }
        state = newState;
        trackId = newTrackId;
        paused = newPaused;
        expiresAtMillis = nowMillis + DISPLAY_MILLIS;
        return true;
    }

    public void clear() {
        state = null;
        trackId = null;
        paused = false;
        expiresAtMillis = 0L;
    }

    public boolean isVisible(long nowMillis) {
        return state != null && nowMillis < expiresAtMillis;
    }

    public State state() {
        return state;
    }
}
