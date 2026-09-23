package com.radaeli.betterradio.music;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A gameplay background or disc definition that roots a sound-event subtree. */
public record MusicSource(String soundEventId, Set<String> sourceIds, Optional<String> biomeId) {
    public MusicSource {
        Objects.requireNonNull(soundEventId, "soundEventId");
        Objects.requireNonNull(sourceIds, "sourceIds");
        Objects.requireNonNull(biomeId, "biomeId");
        if (soundEventId.isBlank() || sourceIds.isEmpty() || sourceIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Sound event id and source ids must not be blank");
        }
        sourceIds = Set.copyOf(sourceIds);
        biomeId = biomeId.filter(value -> !value.isBlank());
    }
}
