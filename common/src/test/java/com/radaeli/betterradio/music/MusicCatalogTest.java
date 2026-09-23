package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MusicCatalogTest {
    @Test
    public void mergesSourcesForTheSameConcreteAudioFile() {
        MusicCatalog catalog = new MusicCatalog();
        catalog.add("minecraft:music/game/a", "minecraft:music.overworld.a", Optional.of("minecraft:plains"));
        catalog.add("minecraft:music/game/a", "example:music.forest", Optional.of("example:crystal_forest"));
        catalog.add("example:music/game/b", "example:music.forest", Optional.of("example:crystal_forest"));

        assertEquals(2, catalog.tracks().size());
        MusicTrack shared = catalog.tracks().stream()
                .filter(track -> track.id().equals("minecraft:music/game/a"))
                .findFirst().orElseThrow();
        assertEquals(Set.of("minecraft:plains", "example:crystal_forest"), shared.biomeIds());
        assertEquals(Set.of("minecraft:music.overworld.a", "example:music.forest"), shared.sourceIds());
    }

    @Test
    public void trackEqualityUsesTheConcreteAudioPathOnly() {
        MusicTrack first = new MusicTrack("example:music/shared", Set.of("minecraft:plains"), Set.of("minecraft:a"));
        MusicTrack second = new MusicTrack("example:music/shared", Set.of("minecraft:forest"), Set.of("example:b"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new MusicTrack("example:music/other"));
    }

    @Test
    public void sourceWithoutBiomeRemainsInTheGlobalPool() {
        MusicCatalog catalog = new MusicCatalog();
        catalog.add("minecraft:music/disc/otherside", "minecraft:otherside", Optional.empty());

        assertEquals(List.of(new MusicTrack("minecraft:music/disc/otherside", Set.of(), Set.of("minecraft:otherside"))),
                List.copyOf(catalog.tracks()));
    }
}
