package com.radaeli.betterradio.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.forge.MusicConfigForge;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.lang.ref.WeakReference;
import java.util.Optional;

/** Forge client keybind and adapter for vanilla background music. */
@Mod.EventBusSubscriber(modid = BetterRadio.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MusicClientForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRadio.MOD_ID);
    private static final KeyMapping PLAY_NEXT_KEY = new KeyMapping(
            "key.better_radio.play_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8,
            "key.categories.better_radio");
    private static final KeyMapping PLAY_PREVIOUS_KEY = new KeyMapping(
            "key.better_radio.play_previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7,
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

    private MusicClientForge() {
    }

    @SubscribeEvent
    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(PLAY_NEXT_KEY);
        event.register(PLAY_PREVIOUS_KEY);
        event.register(TOGGLE_PAUSE_KEY);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("Registered Previous (F7), Play/Next (F8), and Pause/Resume (F9) keybinds and Forge client tick handler");
    }

    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            ensureWorldContext();
            if (!tickHandlerVerified) {
                tickHandlerVerified = true;
                LOGGER.info("Forge music hotkey client tick handler is active");
            }
            if (PLAY_PREVIOUS_KEY.consumeClick()) {
                playPrevious();
            }
            if (PLAY_NEXT_KEY.consumeClick()) {
                LOGGER.info("Play/Next keybind pressed on Forge client");
                playNext();
            }
            if (TOGGLE_PAUSE_KEY.consumeClick()) {
                togglePause();
            }
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() != VanillaGuiOverlay.EXPERIENCE_BAR.type() || !MusicConfigForge.SHOW_NOW_PLAYING.get()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }
            Optional<MusicTrack> current = new ForgeMusicPlatform(minecraft).currentTrack();
            long now = System.currentTimeMillis();
            if (current.isPresent()) {
                boolean paused = controller.isPaused(current);
                PLAYBACK_TOAST.update(currentState(minecraft, paused), current.get().id(), paused, now);
            }
            if (!PLAYBACK_TOAST.isVisible(now)) {
                return;
            }
            String text = Component.translatable(switch (PLAYBACK_TOAST.state()) {
                case PREVIOUS -> "better_radio.status.previous";
                case NEXT -> "better_radio.status.next";
                case PLAYING -> "better_radio.status.playing";
                case PAUSED -> "better_radio.status.paused";
                case MUTED -> "better_radio.status.muted";
            }).getString();
            event.getGuiGraphics().drawCenteredString(minecraft.font, text,
                    event.getWindow().getGuiScaledWidth() / 2, event.getWindow().getGuiScaledHeight() - 48,
                    (PLAYBACK_TOAST.alpha(now) << 24) | 0xFFFFFF);
        }
    }

    private static void togglePause() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(controller.history());
        if (!controller.togglePause(platform)) {
            LOGGER.warn("Playback action=PAUSE_TOGGLE result=unavailable level={} currentEvent={} resolvedAudio={} historyBefore={}",
                    describeLevel(minecraft), describeTrack(currentBefore), resolvedAudioBefore, historyBefore);
            return;
        }
        Optional<MusicTrack> track = platform.currentTrack().or(controller.history()::lastTrack);
        boolean paused = controller.isPaused(track);
        LOGGER.info("Playback action=PAUSE_TOGGLE result={} level={} currentEventBefore={} resolvedAudioBefore={} currentEventAfter={} resolvedAudioAfter={} historyBefore={} historyAfter={}",
                paused ? "paused" : "playing", describeLevel(minecraft), describeTrack(currentBefore),
                resolvedAudioBefore, describeTrack(platform.currentTrack()), describeResolvedSound(minecraft), historyBefore,
                describeHistory(controller.history()));
        track.ifPresent(value -> showAction(minecraft,
                paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING, value, paused));
    }

    private static void playPrevious() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(controller.history());
        Optional<MusicTrack> previous = controller.playPrevious(platform);
        LOGGER.info("Playback action=PREVIOUS level={} currentEventBefore={} resolvedAudioBefore={} selectedEvent={} currentEventAfter={} resolvedAudioAfter={} historyBefore={} historyAfter={}",
                describeLevel(minecraft), describeTrack(currentBefore), resolvedAudioBefore,
                describeTrack(previous), describeTrack(platform.currentTrack()), describeResolvedSound(minecraft),
                historyBefore, describeHistory(controller.history()));
        if (previous.isEmpty()) {
            LOGGER.info("Previous keybind ignored: playback history has no earlier track");
        } else {
            LOGGER.info("Playing previous background music {}", previous.get().id());
            showAction(minecraft, PlaybackToast.State.PREVIOUS, previous.get(), false);
        }
    }

    private static void playNext() {
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

        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(controller.history());
        Optional<MusicTrack> started = controller.playNext(platform);
        LOGGER.info("Playback action=NEXT level={} eligibleEvents={} currentEventBefore={} resolvedAudioBefore={} selectedEvent={} currentEventAfter={} resolvedAudioAfter={} historyBefore={} historyAfter={}",
                describeLevel(minecraft), platform.eligibleTracks().size(), describeTrack(currentBefore),
                resolvedAudioBefore, describeTrack(started), describeTrack(platform.currentTrack()),
                describeResolvedSound(minecraft), historyBefore, describeHistory(controller.history()));
        started.ifPresent(track -> showAction(minecraft, PlaybackToast.State.NEXT, track, false));
    }

    private static PlaybackToast.State currentState(Minecraft minecraft, boolean paused) {
        if (minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F) {
            return PlaybackToast.State.MUTED;
        }
        return paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING;
    }

    private static void showAction(Minecraft minecraft, PlaybackToast.State action,
                                   MusicTrack track, boolean paused) {
        PLAYBACK_TOAST.showAction(action, currentState(minecraft, paused), track.id(), paused,
                System.currentTimeMillis());
    }

    private static void ensureWorldContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (activeLevel.get() != null) {
                LOGGER.info("Playback context reset reason=left_world previousLevel={} history={}",
                        describeLevel(activeLevel.get()), describeHistory(controller.history()));
                activeLevel.clear();
                controller = createController();
                PLAYBACK_TOAST.clear();
            }
            return;
        }
        if (activeLevel.get() != minecraft.level) {
            LOGGER.info("Playback context reset reason=level_changed previousLevel={} newLevel={} history={}",
                    describeLevel(activeLevel.get()), describeLevel(minecraft), describeHistory(controller.history()));
            activeLevel = new WeakReference<>(minecraft.level);
            controller = createController();
            PLAYBACK_TOAST.clear();
        }
    }

    private static String describeTrack(Optional<MusicTrack> track) {
        return track.map(MusicTrack::id).orElse("none");
    }

    private static String describeHistory(MusicHistory history) {
        return "cursor=" + history.position() + ", events="
                + history.tracks().stream().map(MusicTrack::id).toList();
    }

    private static String describeResolvedSound(Minecraft minecraft) {
        return MusicChannelPauseBridge.currentSoundPath(minecraft.getMusicManager()).orElse("none");
    }

    private static String describeLevel(Minecraft minecraft) {
        return minecraft.level == null ? "none" : describeLevel(minecraft.level);
    }

    private static String describeLevel(ClientLevel level) {
        return level == null ? "none" : level.dimension().location() + "@"
                + Integer.toHexString(System.identityHashCode(level));
    }

    private static MusicController createController() {
        return new MusicController(new MusicSelector(new Random()), new MusicHistory());
    }

    private static final class ForgeMusicPlatform implements MusicPlatform {
        private final Minecraft minecraft;
        private final Map<String, Music> musicById;

        private ForgeMusicPlatform(Minecraft minecraft) {
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
