package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public class MusicTrackDiagnosticsTest {
    @Test
    public void formatsConcreteFileAndSortedSourceAffinitiesForLogs() {
        MusicTrack track = new MusicTrack("example:music/forest/night",
                Set.of("minecraft:forest", "example:crystal_forest"),
                Set.of("event:example:music.forest", "biome:minecraft:forest"));

        assertEquals("example:music/forest/night [sources=[biome:minecraft:forest, event:example:music.forest]"
                        + ", biomes=[example:crystal_forest, minecraft:forest]]",
                MusicTrackDiagnostics.describe(track));
    }
}
