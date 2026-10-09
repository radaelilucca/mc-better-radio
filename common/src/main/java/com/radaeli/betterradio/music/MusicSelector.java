package com.radaeli.betterradio.music;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Selects a track without consulting or changing any client/audio state. */
public final class MusicSelector {
    public static final int DEFAULT_BIOME_BIAS_PERCENT = 100;
    private final RandomGenerator random;

    public MusicSelector(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history) {
        return selectNext(eligibleTracks, history, Optional.empty());
    }

    /** Selects uniformly outside the current biome, with the default 3:1 local-biome bias. */
    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history,
                                           Optional<String> currentBiomeId) {
        return selectNext(eligibleTracks, history, currentBiomeId, Optional.empty());
    }

    /** Selects a different file than the active track when alternatives exist. */
    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history,
                                           Optional<String> currentBiomeId, Optional<MusicTrack> currentlyPlaying) {
        return selectNext(eligibleTracks, history, currentBiomeId, currentlyPlaying, DEFAULT_BIOME_BIAS_PERCENT);
    }

    /** Bias strength is 0–100%; the local weight ranges from 1:1 to 3:1, not a fixed probability. */
    public Optional<MusicTrack> selectNext(List<MusicTrack> eligibleTracks, MusicHistory history,
                                          Optional<String> currentBiomeId, Optional<MusicTrack> currentlyPlaying,
                                          int biomeBiasPercent) {
        if (biomeBiasPercent < 0 || biomeBiasPercent > 100) {
            throw new IllegalArgumentException("Biome bias must be between 0 and 100");
        }
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

        // Reduce the integer weights so the default retains its original 3:1 selection.
        int localWeight = 100 + 2 * biomeBiasPercent;
        int divisor = gcd(localWeight, 100);
        int matchingWeight = localWeight / divisor;
        int otherWeight = 100 / divisor;
        int totalWeight = choices.stream().mapToInt(track -> weight(track, currentBiomeId, matchingWeight, otherWeight)).sum();
        int selection = random.nextInt(totalWeight);
        for (MusicTrack track : choices) {
            selection -= weight(track, currentBiomeId, matchingWeight, otherWeight);
            if (selection < 0) {
                return Optional.of(track);
            }
        }
        throw new IllegalStateException("Weighted selection exhausted before choosing a track");
    }

    private static int weight(MusicTrack track, Optional<String> biomeId, int matchingWeight, int otherWeight) {
        return biomeId.filter(track.biomeIds()::contains).isPresent() ? matchingWeight : otherWeight;
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int remainder = a % b; a = b; b = remainder; }
        return a;
    }
}
