package com.radaeli.betterradio.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.neoforge.MusicConfigNeoForge;
import com.radaeli.betterradio.music.MusicController;
import com.radaeli.betterradio.music.MusicChannelPauseBridge;
import com.radaeli.betterradio.music.MusicHistory;
import com.radaeli.betterradio.music.MusicPlatform;
import com.radaeli.betterradio.music.PlaybackToast;
import com.radaeli.betterradio.music.MusicSelector;
import com.radaeli.betterradio.music.MusicTrack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.lang.ref.WeakReference;
import java.util.Optional;

/** NeoForge client keybind and adapter for vanilla background music. */
@EventBusSubscriber(modid = BetterRadio.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MusicClientNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRadio.MOD_ID);
    private static final KeyMapping PLAY_NEXT_KEY = new KeyMapping(
            "key.better_radio.play_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8,
            "key.categories.better_radio");
    private static final KeyMapping TOGGLE_PAUSE_KEY = new KeyMapping(
            "key.better_radio.toggle_pause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9,
            "key.categories.better_radio");
    private static WeakReference<ClientLevel> activeLevel = new WeakReference<>(null);
    private static WeakReference<ClientLevel> registryLevel = new WeakReference<>(null);
    private static Map<String, Music> cachedMusic = Map.of();
    private static long cachedMusicAtMillis;
    private static MusicController controller = createController();
    private static final PlaybackToast PLAYBACK_TOAST = new PlaybackToast();
    private static boolean tickHandlerVerified;

    private MusicClientNeoForge() {
    }

    @SubscribeEvent
    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(PLAY_NEXT_KEY);
        event.register(TOGGLE_PAUSE_KEY);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("Registered Play/Next keybind (default F8) on NeoForge");
    }

    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            if (!tickHandlerVerified) {
                tickHandlerVerified = true;
                LOGGER.info("NeoForge Play/Next client tick handler is active");
            }
            ensureWorldContext();
            playNextIfPressed();
            if (TOGGLE_PAUSE_KEY.consumeClick()) {
                togglePause();
            }
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiEvent.Post event) {
            if (!MusicConfigNeoForge.SHOW_NOW_PLAYING.get()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }
            Optional<MusicTrack> current = new NeoForgeMusicPlatform(minecraft).currentTrack();
            if (current.isEmpty()) {
                PLAYBACK_TOAST.clear();
                return;
            }
            PlaybackToast.State state;
            if (minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F) {
                state = PlaybackToast.State.MUTED;
            } else if (controller.isPaused(current)) {
                state = PlaybackToast.State.PAUSED;
            } else {
                state = PlaybackToast.State.PLAYING;
            }
            long now = System.currentTimeMillis();
            boolean paused = controller.isPaused(current);
            PLAYBACK_TOAST.update(state, current.get().id(), paused, now);
            if (!PLAYBACK_TOAST.isVisible(now)) {
                return;
            }
            String text = Component.translatable(switch (PLAYBACK_TOAST.state()) {
                case PLAYING -> "better_radio.status.playing";
                case PAUSED -> "better_radio.status.paused";
                case MUTED -> "better_radio.status.muted";
            }).getString();
            event.getGuiGraphics().drawCenteredString(minecraft.font, text,
                    minecraft.getWindow().getGuiScaledWidth() / 2,
                    minecraft.getWindow().getGuiScaledHeight() - 48, 0xFFFFFF);
        }
    }

    private static void togglePause() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        MusicPlatform platform = new NeoForgeMusicPlatform(minecraft);
        if (!controller.togglePause(platform)) {
            LOGGER.warn("Pause/resume unavailable: there is no active background music channel");
        }
    }

    private static void ensureWorldContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (activeLevel.get() != null) {
                activeLevel.clear();
                controller = createController();
                PLAYBACK_TOAST.clear();
            }
            return;
        }
        if (activeLevel.get() != minecraft.level) {
            activeLevel = new WeakReference<>(minecraft.level);
            controller = createController();
            PLAYBACK_TOAST.clear();
        }
    }

    private static void playNextIfPressed() {
        if (!PLAY_NEXT_KEY.consumeClick()) {
            return;
        }
        LOGGER.info("Play/Next keybind pressed on NeoForge client");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            LOGGER.info("Play/Next ignored: no active player or level");
            activeLevel.clear();
            controller = createController();
            PLAYBACK_TOAST.clear();
            return;
        }

        if (activeLevel.get() != minecraft.level) {
            activeLevel = new WeakReference<>(minecraft.level);
            controller = createController();
            PLAYBACK_TOAST.clear();
        }

        MusicPlatform platform = new NeoForgeMusicPlatform(minecraft);
        Optional<MusicTrack> started = controller.playNext(platform);
        LOGGER.info("Play/Next requested: {} eligible tracks, selected={}",
                platform.eligibleTracks().size(), started.map(MusicTrack::id).orElse("none"));
    }

    private static MusicController createController() {
        return new MusicController(new MusicSelector(new Random()), new MusicHistory());
    }

    private static final class NeoForgeMusicPlatform implements MusicPlatform {
        private final Minecraft minecraft;
        private final Map<String, Music> musicById;

        private NeoForgeMusicPlatform(Minecraft minecraft) {
            this.minecraft = minecraft;
            this.musicById = collectMusic(minecraft);
        }

        @Override
        public List<MusicTrack> eligibleTracks() {
            return musicById.keySet().stream()
                    .map(MusicTrack::new)
                    .toList();
        }

        @Override
        public Optional<MusicTrack> currentTrack() {
            return musicById.entrySet().stream()
                    .filter(entry -> minecraft.getMusicManager().isPlayingMusic(entry.getValue()))
                    .map(entry -> new MusicTrack(entry.getKey()))
                    .findFirst();
        }

        @Override
        public void stopCurrentTrack() {
            minecraft.getMusicManager().stopPlaying();
        }

        @Override
        public void startTrack(MusicTrack track) {
            Music music = musicById.get(track.id());
            if (music != null) {
                minecraft.getMusicManager().startPlaying(music);
                LOGGER.info("Started background music {}", track.id());
            } else {
                LOGGER.warn("Could not start selected music {}; it is absent from the current level's biome registry",
                        track.id());
            }
        }

        @Override
        public boolean pauseCurrentTrack() {
            return MusicChannelPauseBridge.setPaused(minecraft.getMusicManager(), minecraft.getSoundManager(), true);
        }

        @Override
        public boolean resumeCurrentTrack() {
            return MusicChannelPauseBridge.setPaused(minecraft.getMusicManager(), minecraft.getSoundManager(), false);
        }

        private static Map<String, Music> collectMusic(Minecraft minecraft) {
            long now = System.currentTimeMillis();
            if (registryLevel.get() == minecraft.level && now - cachedMusicAtMillis < 5_000L) {
                return cachedMusic;
            }
            Map<String, Music> result = new LinkedHashMap<>();
            minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).stream()
                    .map(biome -> biome.getBackgroundMusic().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .forEach(music -> result.putIfAbsent(
                            music.getEvent().value().getLocation().toString(), music));
            cachedMusic = Map.copyOf(result);
            registryLevel = new WeakReference<>(minecraft.level);
            cachedMusicAtMillis = now;
            return cachedMusic;
        }
    }
}
