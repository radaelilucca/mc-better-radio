package com.radaeli.betterradio.music;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Coordinates loader-neutral track selection and selection history. */
public final class MusicController {
    private final MusicSelector selector;
    private final MusicHistory history;
    private String pausedTrackId;
    private boolean pausedRestoreRequested;
    private int pausedRestoreCooldown;

    public MusicController(MusicSelector selector, MusicHistory history) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.history = Objects.requireNonNull(history, "history");
    }

    /** Selects and records the next track; adapters remain responsible for audio playback. */
    public Optional<MusicTrack> next(List<MusicTrack> eligibleTracks) {
        return selectNext(eligibleTracks, Optional.empty());
    }

    private Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, Optional<String> currentBiomeId) {
        return selectNext(eligibleTracks, currentBiomeId, Optional.empty());
    }

    private Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, Optional<String> currentBiomeId,
                                            Optional<MusicTrack> currentlyPlaying) {
        Optional<MusicTrack> queued = nextEligibleQueuedTrack(eligibleTracks);
        if (queued.isPresent()) {
            return queued;
        }
        Optional<MusicTrack> selected = selector.selectNext(eligibleTracks, history, currentBiomeId, currentlyPlaying);
        selected.ifPresent(history::record);
        return selected;
    }

    private Optional<MusicTrack> nextEligibleQueuedTrack(List<MusicTrack> eligibleTracks) {
        Optional<MusicTrack> queued;
        do {
            queued = history.nextTrack();
        } while (queued.isPresent() && !eligibleTracks.contains(queued.get()));
        return queued;
    }

    /**
     * Selects the next track while treating a track started outside this
     * controller (for example by vanilla) as the previous track.
     */
    public Optional<MusicTrack> next(List<MusicTrack> eligibleTracks, Optional<MusicTrack> currentlyPlaying) {
        return next(eligibleTracks, currentlyPlaying, Optional.empty());
    }

    /** Plays the selected next track through a loader-specific platform adapter. */
    public Optional<MusicTrack> playNext(MusicPlatform platform) {
        Objects.requireNonNull(platform, "platform");
        if (pausedTrackId != null) return Optional.empty();
        Optional<MusicTrack> current = platform.currentTrack();
        Optional<MusicTrack> selected = next(platform.eligibleTracks(), current, platform.currentBiomeId());
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        current.ifPresent(ignored -> platform.stopCurrentTrack());
        platform.startTrack(selected.get());
        return selected;
    }

    private Optional<MusicTrack> next(List<MusicTrack> eligibleTracks, Optional<MusicTrack> currentlyPlaying,
                                      Optional<String> currentBiomeId) {
        Objects.requireNonNull(currentlyPlaying, "currentlyPlaying").ifPresent(history::record);
        return selectNext(eligibleTracks, currentBiomeId, currentlyPlaying);
    }

    /** Replays the previous visited track and moves the queue cursor back one position. */
    public Optional<MusicTrack> playPrevious(MusicPlatform platform) {
        Objects.requireNonNull(platform, "platform");
        if (pausedTrackId != null) return Optional.empty();
        Optional<MusicTrack> current = platform.currentTrack();
        current.ifPresent(history::record);
        List<MusicTrack> eligibleTracks = platform.eligibleTracks();
        Optional<MusicTrack> previous;
        do {
            previous = history.previousTrack();
        } while (previous.isPresent() && !eligibleTracks.contains(previous.get()));
        if (previous.isEmpty()) {
            return Optional.empty();
        }

        current.ifPresent(ignored -> platform.stopCurrentTrack());
        platform.startTrack(previous.get());
        return previous;
    }

    /** Toggles a single music-manager channel while preserving its playback position. */
    public boolean togglePause(MusicPlatform platform) {
        Objects.requireNonNull(platform, "platform");
        Optional<MusicTrack> current = platform.currentTrack();
        if (current.isEmpty()) {
            if (pausedTrackId == null) return false;
            MusicTrack resolved = platform.eligibleTracks().stream().filter(track -> track.id().equals(pausedTrackId))
                    .findFirst().orElse(null);
            if (resolved != null) platform.startTrack(resolved);
            pausedTrackId = null;
            pausedRestoreRequested = false;
            pausedRestoreCooldown = 0;
            return true;
        }

        String currentId = current.get().id();
        history.record(current.get());
        if (currentId.equals(pausedTrackId)) {
            if (!platform.resumeCurrentTrack()) {
                return false;
            }
            pausedTrackId = null;
            pausedRestoreRequested = false;
            pausedRestoreCooldown = 0;
            return true;
        }

        if (pausedTrackId != null) {
            platform.stopCurrentTrack();
            MusicTrack paused = platform.eligibleTracks().stream().filter(track -> track.id().equals(pausedTrackId))
                    .findFirst().orElse(null);
            if (paused != null) platform.startTrack(paused);
            pausedTrackId = null;
            pausedRestoreRequested = false;
            pausedRestoreCooldown = 0;
            return true;
        }

        if (!platform.pauseCurrentTrack()) {
            return false;
        }
        pausedTrackId = currentId;
        pausedRestoreRequested = true;
        pausedRestoreCooldown = 20;
        return true;
    }

    public boolean isPaused(Optional<MusicTrack> currentTrack) {
        return pausedTrackId != null;
    }

    public boolean isPlaybackPaused() {
        return pausedTrackId != null;
    }

    public Optional<MusicTrack> pausedTrack() {
        if (pausedTrackId == null) return Optional.empty();
        return history.tracks().stream().filter(track -> track.id().equals(pausedTrackId)).findFirst();
    }

    public void restorePausedTrack(MusicTrack track) {
        pausedTrackId = track == null ? null : track.id();
        pausedRestoreRequested = false;
        pausedRestoreCooldown = 0;
        if (track != null) history.record(track);
    }

    /** Reapplies the pause latch after an audio/world reset and rejects any unexpected replacement track. */
    public void maintainPausedPlayback(MusicPlatform platform) {
        if (pausedTrackId == null) return;
        Optional<MusicTrack> current = platform.currentTrack();
        if (current.isPresent() && !current.get().id().equals(pausedTrackId)) {
            platform.stopCurrentTrack();
            current = Optional.empty();
            pausedRestoreRequested = false;
            pausedRestoreCooldown = 0;
        }
        if (current.isPresent()) {
            pausedRestoreRequested = true;
            pausedRestoreCooldown = 20;
            platform.pauseCurrentTrack();
            return;
        }
        if (pausedRestoreRequested && --pausedRestoreCooldown > 0) return;
        platform.eligibleTracks().stream().filter(track -> track.id().equals(pausedTrackId)).findFirst()
                .ifPresent(track -> {
                    platform.startTrack(track);
                    pausedRestoreRequested = true;
                    pausedRestoreCooldown = 20;
                });
    }

    public MusicHistory history() {
        return history;
    }
}
