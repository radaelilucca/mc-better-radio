package com.radaeli.betterradio.music;

import java.util.Random;

/** Silence between naturally completed tracks in either listening mode. */
public enum MusicFrequency {
    FREQUENT(60, 120),
    BALANCED(120, 300),
    OCCASIONAL(300, 600),
    NON_STOP(2, 4);

    private final int minDelayTicks;
    private final int maxDelayTicks;

    MusicFrequency(int minSeconds, int maxSeconds) {
        minDelayTicks = minSeconds * 20;
        maxDelayTicks = maxSeconds * 20;
    }

    public int nextDelayTicks(Random random) {
        return minDelayTicks + random.nextInt(maxDelayTicks - minDelayTicks + 1);
    }

    /** Older settings and unknown preferences retain the rest of the saved player state. */
    public static MusicFrequency fromSaved(String name) {
        return fromSaved(name, BALANCED);
    }

    public static MusicFrequency fromSaved(String name, MusicFrequency fallback) {
        if (name != null) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) { }
        }
        return fallback;
    }
}
