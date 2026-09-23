package com.radaeli.betterradio.music;

import java.util.Objects;

/** Loader-neutral identity for one selectable music track. */
public record MusicTrack(String id, String displayName) {
    public MusicTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
