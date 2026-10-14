package com.radaeli.betterradio.client;



import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.PlayerSession;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

public class PlayerSettingsStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path file() { return temporary.getRoot().toPath().resolve("better_radio-player.json"); }

    @Test
    public void missingFileUsesDefaultsWithoutCreatingIt() {
        assertEquals(PlayerSession.defaults(), new PlayerSettingsStore(file()).load());
        assertFalse(Files.exists(file()));
    }

    @Test
    public void roundTripKeepsModeShuffleAndTrackMetadataIncludingUnavailableReferences() {
        MusicTrack track = new MusicTrack("example:music/disc", Set.of("minecraft:plains"),
                Set.of("jukebox:example:song", "event:example:music_disc.song"));
        PlayerSession.SavedState saved = new PlayerSession.SavedState(List.of(track), true,
                PlayerSession.Mode.PLAYLIST);
        assertTrue(new PlayerSettingsStore(file()).save(saved));
        PlayerSession.SavedState loaded = new PlayerSettingsStore(file()).load();
        assertEquals(saved, loaded);
        assertEquals(track.sourceIds(), loaded.playlist().get(0).sourceIds());
        assertEquals(track.biomeIds(), loaded.playlist().get(0).biomeIds());
        assertFalse(Files.exists(file().resolveSibling(file().getFileName() + ".tmp")));
    }

    @Test
    public void malformedFileIsLeftIntactAndBackedUpBeforeANewSave() throws Exception {
        String original = "{broken playlist data";
        Files.writeString(file(), original);
        PlayerSettingsStore store = new PlayerSettingsStore(file());
        assertEquals(PlayerSession.defaults(), store.load());
        assertEquals(original, Files.readString(file()));
        assertTrue(store.save(PlayerSession.defaults()));
        try (var files = Files.list(file().getParent())) {
            Path backup = files.filter(path -> path.getFileName().toString().contains(".backup-")).findFirst().orElseThrow();
            assertEquals(original, Files.readString(backup));
        }
        assertEquals(PlayerSession.defaults(), new PlayerSettingsStore(file()).load());
    }

    @Test
    public void unknownVersionDoesNotOverwriteTheOriginalWhileLoading() throws Exception {
        String original = "{\"version\":99,\"playlist\":[],\"shuffle\":true,\"mode\":\"PLAYLIST\"}";
        Files.writeString(file(), original);
        assertEquals(PlayerSession.defaults(), new PlayerSettingsStore(file()).load());
        assertEquals(original, Files.readString(file()));
    }

    @Test
    public void saveFailureReportsFailureAndPreservesTheObstructingFile() throws Exception {
        Path blocked = temporary.getRoot().toPath().resolve("blocked");
        Files.writeString(blocked, "keep this file");
        assertFalse(new PlayerSettingsStore(blocked.resolve("playlist.json")).save(PlayerSession.defaults()));
        assertEquals("keep this file", Files.readString(blocked));
    }

    @Test
    public void legacyPauseIsImportedOnlyWhenJsonDoesNotExist() throws Exception {
        MusicTrack paused = new MusicTrack("minecraft:music/game/calm1");
        PlayerSettingsStore store = new PlayerSettingsStore(file());
        assertEquals(paused, store.loadWithLegacyPause(Optional.of(paused)).pausedTrack());
        assertTrue(store.save(PlayerSession.defaults()));
        assertNull(store.loadWithLegacyPause(Optional.of(paused)).pausedTrack());
    }

    @Test
    public void settingsRoundTripPreservesIndependentIntervalsBiasStartupAndPausedTrack() {
        MusicTrack paused = new MusicTrack("minecraft:music/game/calm1");
        PlayerSession.SavedState state = new PlayerSession.SavedState(List.of(), false,
                PlayerSession.Mode.FREE_FLOW, com.radaeli.betterradio.music.MusicFrequency.OCCASIONAL,
                35, com.radaeli.betterradio.music.StartupMode.PLAYLIST,
                com.radaeli.betterradio.music.MusicFrequency.NON_STOP, paused);
        PlayerSettingsStore store = new PlayerSettingsStore(file());
        assertTrue(store.save(state));
        assertEquals(state, store.load());
    }
}
