package com.radaeli.betterradio.music;

/** Preferred mode for the next world session; never changes playback immediately. */
public enum StartupMode {
    LAST_USED,
    FREE_FLOW,
    PLAYLIST;

    public PlayerSession.Mode initialMode(PlayerSession.Mode lastUsed) {
        return switch (this) {
            case LAST_USED -> lastUsed;
            case FREE_FLOW -> PlayerSession.Mode.FREE_FLOW;
            case PLAYLIST -> PlayerSession.Mode.PLAYLIST;
        };
    }

    public static StartupMode fromSaved(String name) {
        if (name != null) {
            try { return valueOf(name); }
            catch (IllegalArgumentException ignored) { }
        }
        return LAST_USED;
    }
}
