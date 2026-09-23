package com.radaeli.betterradio.music;

import java.util.Optional;
import java.util.Objects;

/** Minimal MVP history, retaining only the last selected track. */
public final class MusicHistory {
    private MusicTrack lastTrack;

    public Optional<MusicTrack> lastTrack() {
        return Optional.ofNullable(lastTrack);
    }

    public void record(MusicTrack track) {
        lastTrack = Objects.requireNonNull(track, "track");
    }
}
