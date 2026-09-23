package com.radaeli.betterradio.music;

import java.util.List;
import java.util.Optional;

/** Version-specific boundary implemented by the Forge and NeoForge client adapters. */
public interface MusicPlatform {
    List<MusicTrack> eligibleTracks();

    /** Namespaced biome id used only to bias newly randomized selections. */
    default Optional<String> currentBiomeId() {
        return Optional.empty();
    }

    Optional<MusicTrack> currentTrack();

    void stopCurrentTrack();

    void startTrack(MusicTrack track);

    boolean pauseCurrentTrack();

    boolean resumeCurrentTrack();
}
