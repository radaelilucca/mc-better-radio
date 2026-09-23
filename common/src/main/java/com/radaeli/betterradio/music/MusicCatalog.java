package com.radaeli.betterradio.music;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deduplicates concrete audio files while retaining every source and biome association. */
public final class MusicCatalog {
    private final Map<String, MusicTrack> tracks = new LinkedHashMap<>();

    public void add(String audioFile, MusicSource source) {
        Objects.requireNonNull(source, "source");
        for (String sourceId : source.sourceIds()) {
            add(audioFile, sourceId, source.biomeId());
        }
    }

    public void add(String audioFile, String sourceId, Optional<String> biomeId) {
        Objects.requireNonNull(audioFile, "audioFile");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(biomeId, "biomeId");
        if (audioFile.isBlank() || sourceId.isBlank()) {
            throw new IllegalArgumentException("Audio file and source id must not be blank");
        }
        MusicTrack source = new MusicTrack(audioFile,
                biomeId.<Set<String>>map(Set::of).orElseGet(Set::of), Set.of(sourceId));
        tracks.merge(audioFile, source, MusicTrack::mergeSources);
    }

    public Collection<MusicTrack> tracks() {
        return List.copyOf(tracks.values());
    }
}
