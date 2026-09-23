package com.radaeli.betterradio.music;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Selects a track without consulting or changing any client/audio state. */
public final class MusicSelector {
    private static final int LOCAL_BIOME_WEIGHT = 3;
    private final RandomGenerator random;

    public MusicSelector(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history) {
        return selectNext(eligibleTracks, history, Optional.empty());
    }

    /** Selects uniformly outside the current biome, with a gentle 3:1 local-biome bias. */
    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history,
                                           Optional<String> currentBiomeId) {
        return selectNext(eligibleTracks, history, currentBiomeId, Optional.empty());
    }

    /** Selects a different file than the active track when alternatives exist. */
    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history,
                                           Optional<String> currentBiomeId, Optional<MusicTrack> currentlyPlaying) {
        Objects.requireNonNull(eligibleTracks, "eligibleTracks");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(currentBiomeId, "currentBiomeId");
        Objects.requireNonNull(currentlyPlaying, "currentlyPlaying");
        if (eligibleTracks.isEmpty()) {
            return Optional.empty();
        }

        List<MusicTrack> choices = eligibleTracks;
        Optional<MusicTrack> lastTrack = history.lastTrack();
        if (eligibleTracks.size() > 1) {
            Optional<String> currentId = currentlyPlaying.map(MusicTrack::id);
            List<MusicTrack> alternatives = eligibleTracks.stream()
                    .filter(track -> currentId.map(id -> !id.equals(track.id())).orElse(true))
                    .toList();
            if (!alternatives.isEmpty()) {
                choices = alternatives;
            }

            if (lastTrack.isPresent() && currentId.filter(lastTrack.get().id()::equals).isEmpty()) {
                List<MusicTrack> excludingLast = choices.stream()
                        .filter(track -> !track.id().equals(lastTrack.get().id()))
                        .toList();
                if (!excludingLast.isEmpty()) {
                    choices = excludingLast;
                }
            }
        }

        int totalWeight = choices.stream().mapToInt(track -> weight(track, currentBiomeId)).sum();
        int selection = random.nextInt(totalWeight);
        for (MusicTrack track : choices) {
            selection -= weight(track, currentBiomeId);
            if (selection < 0) {
                return Optional.of(track);
            }
        }
        throw new IllegalStateException("Weighted selection exhausted before choosing a track");
    }

    private static int weight(MusicTrack track, Optional<String> biomeId) {
        return biomeId.filter(track.biomeIds()::contains).isPresent() ? LOCAL_BIOME_WEIGHT : 1;
    }
}
