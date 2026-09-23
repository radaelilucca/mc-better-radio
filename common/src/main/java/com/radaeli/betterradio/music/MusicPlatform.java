package com.radaeli.betterradio.music;

import java.util.List;
import java.util.Optional;

/** Version-specific boundary implemented by the Forge and NeoForge client adapters. */
public interface MusicPlatform {
    List<MusicTrack> eligibleTracks();

    Optional<MusicTrack> currentTrack();

    void stopCurrentTrack();

    void startTrack(MusicTrack track);

    boolean pauseCurrentTrack();

    boolean resumeCurrentTrack();
}
