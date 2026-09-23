package com.radaeli.betterradio.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** In-memory playback queue containing the visited tracks and the current position. */
public final class MusicHistory {
    private final List<MusicTrack> tracks = new ArrayList<>();
    private int position = -1;

    /** The track at the current queue position, if playback has been recorded. */
    public Optional<MusicTrack> lastTrack() {
        return position < 0 ? Optional.empty() : Optional.of(tracks.get(position));
    }

    /** Immutable snapshot of the visited queue in playback order. */
    public List<MusicTrack> tracks() {
        return List.copyOf(tracks);
    }

    /** Zero-based current position, or -1 when the queue is empty. */
    public int position() {
        return position;
    }

    /** Moves the cursor to the next already-visited track. */
    public Optional<MusicTrack> nextTrack() {
        if (position + 1 >= tracks.size()) {
            return Optional.empty();
        }
        return Optional.of(tracks.get(++position));
    }

    /** Moves the cursor to the previous already-visited track. */
    public Optional<MusicTrack> previousTrack() {
        if (position <= 0) {
            return Optional.empty();
        }
        return Optional.of(tracks.get(--position));
    }

    /** Records a newly playing track, discarding any forward path after a branch. */
    public void record(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        if (lastTrack().filter(track::equals).isPresent()) {
            return;
        }
        while (tracks.size() > position + 1) {
            tracks.remove(tracks.size() - 1);
        }
        tracks.add(track);
        position = tracks.size() - 1;
    }
}
