package com.radaeli.betterradio.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.neoforge.MusicConfigNeoForge;
import com.radaeli.betterradio.music.MusicController;
import com.radaeli.betterradio.music.MusicHistory;
import com.radaeli.betterradio.music.MusicPlatform;
import com.radaeli.betterradio.music.PlaybackToast;
import com.radaeli.betterradio.music.MusicSelector;
import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.MusicTrackDiagnostics;
import com.radaeli.ruilib.ui.Bounds;
import com.radaeli.ruilib.ui.Toast;
import com.radaeli.ruilib.ui.ToastHost;
import com.radaeli.ruilib.ui.UiContext;
import com.radaeli.ruilib.ui.UiRadii;
import com.radaeli.ruilib.ui.UiTheme;
import com.radaeli.ruilib.ui.UiTokens;
import com.radaeli.ruilib.ui.UiThemes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Optional;

/** NeoForge client keybind and adapter for vanilla background music. */
@EventBusSubscriber(modid = BetterRadio.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MusicClientNeoForge {
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
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> activeLevel =
            new java.lang.ref.WeakReference<>(null);
    private static MusicController controller = createController();
    private static final PlaybackToast PLAYBACK_TOAST = new PlaybackToast();
    private static final ToastHost TOAST_HOST = new ToastHost();
    private static UiTheme sourceToastTheme;
    private static UiTheme toastTheme;
    private static boolean tickHandlerVerified;

    private MusicClientNeoForge() {
    }

    @SubscribeEvent
    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(PLAY_NEXT_KEY);
        event.register(PLAY_PREVIOUS_KEY);
        event.register(TOGGLE_PAUSE_KEY);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        NeoForge.EVENT_BUS.register(NeoForgeMusicPlatform.VanillaMusicSelection.class);
        LOGGER.info("Registered Previous (F7), Play/Next (F8), and Pause/Resume (F9) keybinds on NeoForge");
    }

    @SubscribeEvent
    public static void registerResourceReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
                NeoForgeMusicCatalog.invalidate();
                LOGGER.debug("Invalidated Better Radio's loaded music catalog after resource reload");
            }
        });
    }

    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            if (!tickHandlerVerified) {
                tickHandlerVerified = true;
                LOGGER.info("NeoForge music hotkey client tick handler is active");
            }
            ensureWorldContext();
            updatePlaybackToast();
            TOAST_HOST.tick();
            if (PLAY_PREVIOUS_KEY.consumeClick()) {
                playPrevious();
            }
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
            if (minecraft.player == null || minecraft.font == null || TOAST_HOST.size() == 0) return;
            int width = minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft.getWindow().getGuiScaledHeight();
            var theme = toastTheme();
            TOAST_HOST.layout(minecraft.font, theme, new Bounds(0, 0, width, height));
            TOAST_HOST.render(new UiContext(event.getGuiGraphics(), minecraft.font, theme,
                    minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos(), width, height));
        }
    }

    private static UiTheme toastTheme() {
        UiTheme current = UiThemes.globalTheme();
        if (current != sourceToastTheme) {
            UiTokens tokens = current.tokens();
            UiTokens squareTokens = new UiTokens(tokens.panelPadding(), tokens.cardPadding(),
                    tokens.smallGap(), tokens.mediumGap(), tokens.largeGap(), tokens.buttonPadding(),
                    tokens.buttonMinimumWidth(), tokens.buttonMinimumHeight(), tokens.typography(),
                    new UiRadii(0, 0, 0, 0), tokens.motion());
            sourceToastTheme = current;
            toastTheme = new UiTheme(current.palette(), squareTokens);
        }
        return toastTheme;
    }

    private static void togglePause() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        MusicPlatform platform = new NeoForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(controller.history());
        if (!controller.togglePause(platform)) {
            LOGGER.warn("Playback action=PAUSE_TOGGLE result=unavailable level={} currentTrack={} resolvedFile={} historyBefore={}",
                    describeLevel(minecraft), describeTrack(currentBefore), resolvedAudioBefore, historyBefore);
            return;
        }
        Optional<MusicTrack> track = platform.currentTrack().or(controller.history()::lastTrack);
        boolean paused = controller.isPaused(track);
        LOGGER.info("Playback action=PAUSE_TOGGLE result={} level={} currentTrackBefore={} resolvedFileBefore={} currentTrackAfter={} resolvedFileAfter={} historyBefore={} historyAfter={}",
                paused ? "paused" : "playing", describeLevel(minecraft), describeTrack(currentBefore),
                resolvedAudioBefore, describeTrack(platform.currentTrack()), describeResolvedSound(minecraft),
                historyBefore, describeHistory(controller.history()));
        track.ifPresent(value -> showAction(minecraft,
                paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING, value, paused));
    }

    private static void playPrevious() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        MusicPlatform platform = new NeoForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(controller.history());
        Optional<MusicTrack> previous = controller.playPrevious(platform);
        LOGGER.info("Playback action=PREVIOUS level={} currentTrackBefore={} resolvedFileBefore={} selectedTrack={} currentTrackAfter={} resolvedFileAfter={} historyBefore={} historyAfter={}",
                describeLevel(minecraft), describeTrack(currentBefore), resolvedAudioBefore,
                describeTrack(previous), describeTrack(platform.currentTrack()), describeResolvedSound(minecraft),
                historyBefore, describeHistory(controller.history()));
        if (previous.isEmpty()) {
            LOGGER.info("Previous keybind ignored: playback history has no earlier track");
        } else {
            LOGGER.info("Playing previous background music {}", MusicTrackDiagnostics.describe(previous.get()));
            showAction(minecraft, PlaybackToast.State.PREVIOUS, previous.get(), false);
        }
    }

    private static void ensureWorldContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (activeLevel.get() != null) {
                LOGGER.info("Playback context reset reason=left_world previousLevel={} history={}",
                        describeLevel(activeLevel.get()), describeHistory(controller.history()));
                NeoForgeMusicPlatform.stopOwnedTrack(minecraft);
                activeLevel.clear();
                controller = createController();
                PLAYBACK_TOAST.clear();
            }
            return;
        }
        if (activeLevel.get() != minecraft.level) {
            LOGGER.info("Playback context reset reason=level_changed previousLevel={} newLevel={} history={}",
                    describeLevel(activeLevel.get()), describeLevel(minecraft), describeHistory(controller.history()));
            NeoForgeMusicPlatform.stopOwnedTrack(minecraft);
            activeLevel = new java.lang.ref.WeakReference<>(minecraft.level);
            controller = createController();
            PLAYBACK_TOAST.clear();
        }
        NeoForgeMusicPlatform.updateScreenMusicSuspension(minecraft);
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
            LOGGER.info("Playback context reset reason=level_changed previousLevel={} newLevel={} history={}",
                    describeLevel(activeLevel.get()), describeLevel(minecraft), describeHistory(controller.history()));
            NeoForgeMusicPlatform.stopOwnedTrack(minecraft);
            activeLevel = new java.lang.ref.WeakReference<>(minecraft.level);
            controller = createController();
            PLAYBACK_TOAST.clear();
        }

        MusicPlatform platform = new NeoForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(controller.history());
        Optional<MusicTrack> started = controller.playNext(platform);
        LOGGER.info("Playback action=NEXT level={} eligibleTracks={} currentTrackBefore={} resolvedFileBefore={} selectedTrack={} currentTrackAfter={} resolvedFileAfter={} historyBefore={} historyAfter={}",
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

    static void clearPlaybackToast() {
        PLAYBACK_TOAST.clear();
    }

    private static void showAction(Minecraft minecraft, PlaybackToast.State action,
                                   MusicTrack track, boolean paused) {
        PLAYBACK_TOAST.showAction(action, currentState(minecraft, paused), track.id(), paused,
                net.minecraft.Util.getMillis());
        TOAST_HOST.show(new Toast(playbackMessage(minecraft, action, Optional.of(track)),
                Toast.Level.INFO, 40));
    }

    private static void updatePlaybackToast() {
        if (!MusicConfigNeoForge.SHOW_NOW_PLAYING.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        Optional<MusicTrack> current = new NeoForgeMusicPlatform(minecraft).currentTrack();
        if (current.isEmpty()) return;
        boolean paused = controller.isPaused(current);
        if (PLAYBACK_TOAST.update(currentState(minecraft, paused), current.get().id(), paused,
                net.minecraft.Util.getMillis())) {
            TOAST_HOST.show(new Toast(playbackMessage(minecraft, PLAYBACK_TOAST.state(), current),
                    Toast.Level.INFO, 40));
        }
    }

    private static Component playbackMessage(Minecraft minecraft, PlaybackToast.State state,
                                             Optional<MusicTrack> track) {
        if (state == PlaybackToast.State.MUTED) {
            return Component.translatable(state.translationKey());
        }
        Component nowPlaying = Component.translatable("better_radio.now_playing",
                track.flatMap(value -> trackLabel(minecraft, value)).orElseGet(() ->
                        Component.translatable("better_radio.track.background")));
        if (state == PlaybackToast.State.PLAYING) {
            return nowPlaying;
        }
        return Component.translatable(state.translationKey())
                .append(Component.translatable("better_radio.status.separator"))
                .append(nowPlaying);
    }

    private static Optional<Component> trackLabel(Minecraft minecraft, MusicTrack track) {
        var songs = minecraft.level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG);
        return track.sourceIds().stream()
                .filter(source -> source.startsWith("jukebox:"))
                .sorted()
                .map(source -> ResourceLocation.tryParse(source.substring("jukebox:".length())))
                .filter(java.util.Objects::nonNull)
                .map(songs::get)
                .filter(java.util.Objects::nonNull)
                .map(JukeboxSong::description)
                .findFirst();
    }

    private static String describeTrack(Optional<MusicTrack> track) {
        return track.map(MusicTrackDiagnostics::describe).orElse("none");
    }

    private static String describeHistory(MusicHistory history) {
        return "cursor=" + history.position() + ", tracks="
                + history.tracks().stream().map(MusicTrackDiagnostics::describe).toList();
    }

    private static String describeResolvedSound(Minecraft minecraft) {
        return NeoForgeMusicPlatform.activeFilePath().orElse("none");
    }

    private static String describeLevel(Minecraft minecraft) {
        return minecraft.level == null ? "none" : describeLevel(minecraft.level);
    }

    private static String describeLevel(net.minecraft.client.multiplayer.ClientLevel level) {
        return level == null ? "none" : level.dimension().location() + "@"
                + Integer.toHexString(System.identityHashCode(level));
    }

    private static MusicController createController() {
        return new MusicController(new MusicSelector(new Random()), new MusicHistory());
    }

}
