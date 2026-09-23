package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class MusicSelectorTest {
    private static final String BIOME = "minecraft:plains";
    private static final MusicTrack LOCAL = new MusicTrack("minecraft:music/local", Set.of(BIOME), Set.of());
    private static final MusicTrack GLOBAL = new MusicTrack("example:music/global", Set.of(), Set.of());

    @Test
    public void localBiomeGetsThreeOfFourWeightedSlots() {
        assertEquals(LOCAL, selectorAt(0).selectNext(List.of(LOCAL, GLOBAL), new MusicHistory(), java.util.Optional.of(BIOME)).orElseThrow());
        assertEquals(LOCAL, selectorAt(2).selectNext(List.of(LOCAL, GLOBAL), new MusicHistory(), java.util.Optional.of(BIOME)).orElseThrow());
        assertEquals(GLOBAL, selectorAt(3).selectNext(List.of(LOCAL, GLOBAL), new MusicHistory(), java.util.Optional.of(BIOME)).orElseThrow());
    }

    @Test
    public void globalPoolRemainsAvailableWhenNoTrackMatchesTheBiome() {
        assertEquals(GLOBAL, selectorAt(0).selectNext(List.of(GLOBAL), new MusicHistory(), java.util.Optional.of(BIOME)).orElseThrow());
    }

    @Test
    public void activeFileIsNotRepeatedWhenAnotherEligibleFileExists() {
        MusicHistory history = new MusicHistory();
        history.record(GLOBAL);

        assertEquals(GLOBAL, selectorAt(0).selectNext(List.of(LOCAL, GLOBAL), history,
                java.util.Optional.of(BIOME), java.util.Optional.of(LOCAL)).orElseThrow());
    }

    private static MusicSelector selectorAt(int result) {
        Random fixedRandom = new Random(0) {
            @Override
            public int nextInt(int bound) {
                if (result >= bound) {
                    throw new IllegalArgumentException("Test result outside weighted range");
                }
                return result;
            }
        };
        return new MusicSelector(fixedRandom);
    }
}
