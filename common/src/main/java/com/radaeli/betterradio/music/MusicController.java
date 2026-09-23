package com.radaeli.betterradio.music;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Coordinates loader-neutral track selection and selection history. */
public final class MusicController {
    private final MusicSelector selector;
    private final MusicHistory history;

    public MusicController(MusicSelector selector, MusicHistory history) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.history = Objects.requireNonNull(history, "history");
    }

    /** Selects and records the next track; adapters remain responsible for audio playback. */
    public Optional<MusicTrack> next(List<MusicTrack> eligibleTracks) {
        Optional<MusicTrack> selected = selector.selectNext(eligibleTracks, history);
        selected.ifPresent(history::record);
        return selected;
    }

    /**
     * Selects the next track while treating a track started outside this
     * controller (for example by vanilla) as the previous track.
     */
    public Optional<MusicTrack> next(List<MusicTrack> eligibleTracks, Optional<MusicTrack> currentlyPlaying) {
        Objects.requireNonNull(currentlyPlaying, "currentlyPlaying").ifPresent(history::record);
        return next(eligibleTracks);
    }

    /** Plays the selected next track through a loader-specific platform adapter. */
    public Optional<MusicTrack> playNext(MusicPlatform platform) {
        Objects.requireNonNull(platform, "platform");
        Optional<MusicTrack> current = platform.currentTrack();
        Optional<MusicTrack> selected = next(platform.eligibleTracks(), current);
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        current.ifPresent(ignored -> platform.stopCurrentTrack());
        platform.startTrack(selected.get());
        return selected;
    }

    public MusicHistory history() {
        return history;
    }
}
