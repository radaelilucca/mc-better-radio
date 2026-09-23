package com.radaeli.betterradio.music;

import java.util.Objects;

/** One leaf file or nested event reference from a loaded sound definition. */
public record SoundDefinitionEntry(String id, boolean eventReference) {
    public SoundDefinitionEntry {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    public static SoundDefinitionEntry file(String audioFileId) {
        return new SoundDefinitionEntry(audioFileId, false);
    }

    public static SoundDefinitionEntry event(String eventId) {
        return new SoundDefinitionEntry(eventId, true);
    }
}
