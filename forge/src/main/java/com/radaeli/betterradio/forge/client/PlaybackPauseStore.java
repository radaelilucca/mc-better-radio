package com.radaeli.betterradio.forge.client;

import com.radaeli.betterradio.music.MusicTrack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Persists the user's pause latch across world changes and game restarts. */
final class PlaybackPauseStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_radio");
    private final Path file = FMLPaths.CONFIGDIR.get().resolve("better_radio-paused-track.txt");

    Optional<MusicTrack> load() {
        if (!Files.exists(file)) return Optional.empty();
        try {
            String id = Files.readString(file, StandardCharsets.UTF_8).trim();
            return id.isEmpty() ? Optional.empty() : Optional.of(new MusicTrack(id));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load Better Radio's paused track; playback will start normally", exception);
            return Optional.empty();
        }
    }

    boolean save(Optional<MusicTrack> pausedTrack) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            if (pausedTrack.isPresent()) {
                Files.writeString(temporary, pausedTrack.get().id(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.deleteIfExists(temporary);
                Files.deleteIfExists(file);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not save Better Radio's pause state", exception);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            return false;
        }
    }
}
