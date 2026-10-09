package com.radaeli.betterradio.neoforge.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.radaeli.betterradio.music.MusicFrequency;
import com.radaeli.betterradio.music.MusicSelector;
import com.radaeli.betterradio.music.StartupMode;
import com.radaeli.betterradio.music.PlayerSession;
import com.radaeli.betterradio.music.MusicTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Stores personal references rather than world-specific registries or playback positions. */
final class PlayerSettingsStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_radio");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private boolean preserveUnreadableFile;

    private record TrackReference(String id, Set<String> biomeIds, Set<String> sourceIds) { }
    private record Document(int version, List<TrackReference> playlist, boolean shuffle, String mode,
                            String musicFrequency, Integer biomeBiasPercent, String startupMode,
                            String playlistFrequency) { }

    PlayerSettingsStore(Path file) { this.file = file; }

    PlayerSession.SavedState load() {
        if (!Files.exists(file)) return PlayerSession.defaults();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Document data = GSON.fromJson(reader, Document.class);
            if (data == null || data.version() != 1 || data.playlist() == null) {
                throw new IllegalArgumentException("Unsupported or incomplete playlist document");
            }
            List<MusicTrack> tracks = new ArrayList<>();
            for (TrackReference track : data.playlist()) {
                tracks.add(new MusicTrack(track.id(), track.biomeIds() == null ? Set.of() : track.biomeIds(),
                        track.sourceIds() == null ? Set.of() : track.sourceIds()));
            }
            int bias = data.biomeBiasPercent() == null || data.biomeBiasPercent() < 0 || data.biomeBiasPercent() > 100
                    ? MusicSelector.DEFAULT_BIOME_BIAS_PERCENT : data.biomeBiasPercent();
            return new PlayerSession.SavedState(tracks, data.shuffle(), PlayerSession.Mode.valueOf(data.mode()),
                    MusicFrequency.fromSaved(data.musicFrequency()), bias, StartupMode.fromSaved(data.startupMode()),
                    MusicFrequency.fromSaved(data.playlistFrequency(), MusicFrequency.NON_STOP));
        } catch (IOException | RuntimeException exception) {
            preserveUnreadableFile = true;
            LOGGER.warn("Could not load Better Radio's personal playlist; using Free flow", exception);
            return PlayerSession.defaults();
        }
    }

    boolean save(PlayerSession.SavedState saved) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            // Keep a recoverable copy before replacing malformed or newer-version data.
            if (preserveUnreadableFile && Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".backup-" + System.currentTimeMillis()));
                preserveUnreadableFile = false;
            }
            Document data = new Document(1, saved.playlist().stream()
                    .map(track -> new TrackReference(track.id(), track.biomeIds(), track.sourceIds())).toList(),
                    saved.shuffle(), saved.mode().name(), saved.musicFrequency().name(),
                    saved.biomeBiasPercent(), saved.startupMode().name(), saved.playlistFrequency().name());
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not save Better Radio's personal playlist", exception);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            return false;
        }
    }
}
