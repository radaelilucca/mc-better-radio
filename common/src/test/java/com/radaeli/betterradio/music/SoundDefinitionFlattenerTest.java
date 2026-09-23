package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SoundDefinitionFlattenerTest {
    @Test
    public void flattensSingleFileAndWeightedAlternatives() {
        Map<String, List<SoundDefinitionEntry>> definitions = Map.of(
                "minecraft:music.a", List.of(
                        SoundDefinitionEntry.file("minecraft:music/game/a"),
                        SoundDefinitionEntry.file("minecraft:music/game/b"),
                        SoundDefinitionEntry.file("minecraft:music/game/a")));

        assertEquals(List.of("minecraft:music/game/a", "minecraft:music/game/b"),
                SoundDefinitionFlattener.flatten("minecraft:music.a", definitions::get));
    }

    @Test
    public void flattensNestedAndSharedEventReferencesWithoutDuplicates() {
        Map<String, List<SoundDefinitionEntry>> definitions = Map.of(
                "example:root", List.of(SoundDefinitionEntry.event("example:branch_a"),
                        SoundDefinitionEntry.event("example:branch_b")),
                "example:branch_a", List.of(SoundDefinitionEntry.event("example:shared")),
                "example:branch_b", List.of(SoundDefinitionEntry.event("example:shared"),
                        SoundDefinitionEntry.file("example:music/direct")),
                "example:shared", List.of(SoundDefinitionEntry.file("example:music/shared")));

        assertEquals(List.of("example:music/direct", "example:music/shared"),
                SoundDefinitionFlattener.flatten("example:root", definitions::get));
    }

    @Test
    public void skipsCyclesAndMissingDefinitions() {
        Map<String, List<SoundDefinitionEntry>> definitions = Map.of(
                "example:a", List.of(SoundDefinitionEntry.event("example:b"),
                        SoundDefinitionEntry.file("example:music/a")),
                "example:b", List.of(SoundDefinitionEntry.event("example:a"),
                        SoundDefinitionEntry.event("example:missing"),
                        SoundDefinitionEntry.file("example:music/b")));

        assertEquals(List.of("example:music/a", "example:music/b"),
                SoundDefinitionFlattener.flatten("example:a", definitions::get));
    }
}
