package com.radaeli.betterradio.neoforge.client;

import com.radaeli.betterradio.music.MusicChannelPauseBridge;
import com.radaeli.betterradio.music.MusicPlatform;
import com.radaeli.betterradio.music.MusicTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

import java.util.Optional;

/** Adapter that plays a retained concrete Sound file as local gameplay background music. */
final class NeoForgeMusicPlatform implements MusicPlatform {
    private static final String EVENT_SOURCE_PREFIX = "event:";
    private static FixedFileSound activeSound;
    private static MusicTrack activeTrack;
    private static boolean pausedByUser;
    private static boolean suspendedForVanillaScreen;

    private final Minecraft minecraft;

    NeoForgeMusicPlatform(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public java.util.List<MusicTrack> eligibleTracks() {
        return NeoForgeMusicCatalog.collect(minecraft).tracks();
    }

    @Override
    public Optional<String> currentBiomeId() {
        if (minecraft.level == null || minecraft.player == null) {
            return Optional.empty();
        }
        return minecraft.level.getBiome(minecraft.player.blockPosition()).unwrapKey()
                .map(ResourceKey::location).map(ResourceLocation::toString);
    }

    @Override
    public Optional<MusicTrack> currentTrack() {
        if (activeSound == null || activeTrack == null || !minecraft.getSoundManager().isActive(activeSound)) {
            return Optional.empty();
        }
        return Optional.of(activeTrack);
    }

    @Override
    public void stopCurrentTrack() {
        stopOwnedTrack(minecraft);
    }

    @Override
    public void startTrack(MusicTrack track) {
        NeoForgeMusicCatalog.Snapshot catalog = NeoForgeMusicCatalog.collect(minecraft);
        MusicTrack selected = catalog.findTrack(track.id()).orElse(null);
        Sound resolvedFile = catalog.fileEntries().get(track.id());
        ResourceLocation sourceEvent = selected == null ? null : selected.sourceIds().stream()
                .filter(source -> source.startsWith(EVENT_SOURCE_PREFIX))
                .map(source -> ResourceLocation.tryParse(source.substring(EVENT_SOURCE_PREFIX.length())))
                .filter(java.util.Objects::nonNull)
                .filter(id -> minecraft.getSoundManager().getSoundEvent(id) != null)
                .findFirst().orElse(null);
        if (selected == null || resolvedFile == null || sourceEvent == null) {
            return;
        }

        stopOwnedTrack(minecraft);
        FixedFileSound sound = new FixedFileSound(resolvedFile, sourceEvent);
        activeSound = sound;
        activeTrack = selected;
        minecraft.getSoundManager().play(sound);
    }

    @Override
    public boolean pauseCurrentTrack() {
        if (activeSound == null || pausedByUser || !MusicChannelPauseBridge.setSoundInstancePaused(
                minecraft.getSoundManager(), activeSound, true)) {
            return false;
        }
        pausedByUser = true;
        return true;
    }

    @Override
    public boolean resumeCurrentTrack() {
        if (activeSound == null || !pausedByUser || !MusicChannelPauseBridge.setSoundInstancePaused(
                minecraft.getSoundManager(), activeSound, false)) {
            return false;
        }
        pausedByUser = false;
        return true;
    }

    static void updateScreenMusicSuspension(Minecraft minecraft) {
        boolean screenOwnsBackground = minecraft.screen != null && minecraft.screen.getBackgroundMusic() != null;
        if (screenOwnsBackground && activeSound != null && !pausedByUser && !suspendedForVanillaScreen) {
            suspendedForVanillaScreen = MusicChannelPauseBridge.setSoundInstancePaused(
                    minecraft.getSoundManager(), activeSound, true);
        } else if (!screenOwnsBackground && suspendedForVanillaScreen) {
            if (!pausedByUser) {
                MusicChannelPauseBridge.setSoundInstancePaused(minecraft.getSoundManager(), activeSound, false);
            }
            suspendedForVanillaScreen = false;
        }
    }

    static void stopOwnedTrack(Minecraft minecraft) {
        FixedFileSound previous = activeSound;
        activeSound = null;
        activeTrack = null;
        pausedByUser = false;
        suspendedForVanillaScreen = false;
        if (previous != null) {
            minecraft.getSoundManager().stop(previous);
        }
    }

    static Optional<String> activeFilePath() {
        if (activeSound == null || activeSound.getSound() == null) {
            return Optional.ofNullable(activeTrack).map(MusicTrack::id);
        }
        return Optional.of(activeSound.getSound().getPath().toString());
    }

    /** Null selection makes MusicManager stop its current sound and avoid retrying gameplay music. */
    static final class VanillaMusicSelection {
        private VanillaMusicSelection() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void selectGameplayMusic(SelectMusicEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }
            if (minecraft.screen != null && minecraft.screen.getBackgroundMusic() != null) {
                return;
            }
            if (event.getMusic() != null) {
                NeoForgeMusicCatalog.observeSituationalMusic(event.getMusic());
            }
            event.setMusic(null);
        }
    }

    /**
     * Resolves to the exact selected file, so SoundEngine keeps its sampled volume/pitch and streaming flag.
     * Background playback remains local, non-positional MUSIC and non-looping like vanilla music.
     */
    private static final class FixedFileSound extends AbstractSoundInstance {
        private final ResourceLocation sourceEvent;
        private final Sound fixedFile;

        private FixedFileSound(Sound fixedFile, ResourceLocation sourceEvent) {
            super(sourceEvent, SoundSource.MUSIC, RandomSource.create());
            this.sourceEvent = sourceEvent;
            this.fixedFile = fixedFile;
            this.looping = false;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            this.sound = fixedFile;
            return soundManager.getSoundEvent(sourceEvent);
        }
    }
}
