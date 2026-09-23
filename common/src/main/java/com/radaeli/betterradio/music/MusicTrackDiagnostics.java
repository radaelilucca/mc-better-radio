package com.radaeli.betterradio.music;

import java.util.Objects;

/** Stable, player-independent description of a concrete track for diagnostic logs. */
public final class MusicTrackDiagnostics {
    private MusicTrackDiagnostics() {
    }

    public static String describe(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        return track.id()
                + " [sources=" + track.sourceIds().stream().sorted().toList()
                + ", biomes=" + track.biomeIds().stream().sorted().toList() + "]";
    }
}
