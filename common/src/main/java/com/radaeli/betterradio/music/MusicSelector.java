package com.radaeli.betterradio.music;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Selects a track without consulting or changing any client/audio state. */
public final class MusicSelector {
    private final RandomGenerator random;

    public MusicSelector(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history) {
        Objects.requireNonNull(eligibleTracks, "eligibleTracks");
        Objects.requireNonNull(history, "history");
        if (eligibleTracks.isEmpty()) {
            return Optional.empty();
        }

        List<MusicTrack> choices = eligibleTracks;
        Optional<MusicTrack> lastTrack = history.lastTrack();
        if (eligibleTracks.size() > 1 && lastTrack.isPresent()) {
            List<MusicTrack> alternatives = eligibleTracks.stream()
                    .filter(track -> !track.id().equals(lastTrack.get().id()))
                    .toList();
            if (!alternatives.isEmpty()) {
                choices = alternatives;
            }
        }

        return Optional.of(choices.get(random.nextInt(choices.size())));
    }
}
