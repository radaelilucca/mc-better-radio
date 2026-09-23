package com.radaeli.betterradio.music;

import java.util.Objects;
import java.util.Set;

/** Loader-neutral identity for one concrete audio file and its catalog sources. */
public record MusicTrack(String id, Set<String> biomeIds, Set<String> sourceIds) {
    public MusicTrack(String id) {
        this(id, Set.of(), Set.of());
    }

    public MusicTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(biomeIds, "biomeIds");
        Objects.requireNonNull(sourceIds, "sourceIds");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        biomeIds = Set.copyOf(biomeIds);
        sourceIds = Set.copyOf(sourceIds);
    }

    public MusicTrack mergeSources(MusicTrack other) {
        if (!id.equals(other.id)) {
            throw new IllegalArgumentException("Cannot merge different audio files");
        }
        java.util.HashSet<String> biomes = new java.util.HashSet<>(biomeIds);
        biomes.addAll(other.biomeIds);
        java.util.HashSet<String> sources = new java.util.HashSet<>(sourceIds);
        sources.addAll(other.sourceIds);
        return new MusicTrack(id, biomes, sources);
    }

    /** Playback identity is the concrete audio path, independent of its origins. */
    @Override
    public boolean equals(Object object) {
        return object instanceof MusicTrack other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
