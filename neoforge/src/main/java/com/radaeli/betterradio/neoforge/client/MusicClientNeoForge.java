package com.radaeli.betterradio.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.music.MusicFrequency;
import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.MusicTrackDiagnostics;
import com.radaeli.betterradio.music.MusicTrackNames;
import com.radaeli.betterradio.music.PlaybackToast;
import com.radaeli.betterradio.music.PlayerSession;
import com.radaeli.betterradio.music.StartupMode;
import com.radaeli.betterradio.neoforge.MusicConfigNeoForge;
import com.radaeli.ruilib.ui.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Shared commands for NeoForge hotkeys, the personal player dialog and automatic playback. */
@EventBusSubscriber(modid = BetterRadio.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MusicClientNeoForge {
    private static final int MIN_INITIAL_DELAY_TICKS = 200;
    private static final int MAX_INITIAL_DELAY_TICKS = 300;
    private static final int AUTOPLAY_RETRY_TICKS = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRadio.MOD_ID);
    private static final KeyMapping OPEN_PLAYER_KEY = key("open_player", GLFW.GLFW_KEY_F6);
    private static final KeyMapping PLAY_PREVIOUS_KEY = key("play_previous", GLFW.GLFW_KEY_F7);
    private static final KeyMapping PLAY_NEXT_KEY = key("play_next", GLFW.GLFW_KEY_F8);
    private static final KeyMapping TOGGLE_PAUSE_KEY = key("toggle_pause", GLFW.GLFW_KEY_F9);
    private static final PlayerSettingsStore SETTINGS =
            new PlayerSettingsStore(FMLPaths.CONFIGDIR.get().resolve("better_radio-player.json"));
    private static PlayerSession session = new PlayerSession(new Random(), SETTINGS.load());
    private static WeakReference<ClientPacketListener> activeConnection = new WeakReference<>(null);
    private static WeakReference<ClientLevel> activeLevel = new WeakReference<>(null);
    private static final PlaybackToast PLAYBACK_TOAST = new PlaybackToast();
    private static final ToastHost TOAST_HOST = new ToastHost();
    private static UiTheme sourceToastTheme;
    private static UiTheme toastTheme;
    private static int autoplayDelayTicks = -1;
    private static boolean autoplayObservedPlayback;
    private static String idleStateKey = "nothing_playing";

    private MusicClientNeoForge() { }

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.better_radio." + name, InputConstants.Type.KEYSYM, key,
                "key.categories.better_radio");
    }

    @SubscribeEvent
    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PLAYER_KEY);
        event.register(PLAY_PREVIOUS_KEY);
        event.register(PLAY_NEXT_KEY);
        event.register(TOGGLE_PAUSE_KEY);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        NeoForge.EVENT_BUS.register(NeoForgeMusicPlatform.VanillaMusicSelection.class);
        LOGGER.info("Registered player (F6), previous (F7), next (F8) and pause/resume (F9)");
    }

    @SubscribeEvent
    public static void registerResourceReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
            @Override
            protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
                NeoForgeMusicCatalog.invalidate();
                TrackDurationCache.invalidate();
            }
        });
    }

    public static final class ClientEvents {
        private ClientEvents() { }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            ensureWorldContext();
            Minecraft minecraft = Minecraft.getInstance();
            if (OPEN_PLAYER_KEY.consumeClick() && inWorld()) {
                if (minecraft.screen instanceof PlayerScreen) minecraft.setScreen(null);
                else if (minecraft.screen == null) minecraft.setScreen(new PlayerScreen());
            }
            if (PLAY_PREVIOUS_KEY.consumeClick()) playPrevious();
            if (PLAY_NEXT_KEY.consumeClick()) playNext();
            if (TOGGLE_PAUSE_KEY.consumeClick()) togglePause();
            tickAutoplay();
            updatePlaybackToast();
            TOAST_HOST.tick();
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiEvent.Post event) {
            if (!MusicConfigNeoForge.SHOW_NOW_PLAYING.get()) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.font == null || TOAST_HOST.size() == 0) return;
            int width = minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft.getWindow().getGuiScaledHeight();
            UiTheme theme = playerTheme();
            TOAST_HOST.layout(minecraft.font, theme, new Bounds(0, 0, width, height));
            TOAST_HOST.render(new UiContext(event.getGuiGraphics(), minecraft.font, theme,
                    0, 0, width, height));
        }
    }

    static boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null;
    }

    static UiTheme playerTheme() {
        UiTheme current = UiThemes.globalTheme();
        if (current != sourceToastTheme) {
            UiTokens tokens = current.tokens();
            UiTokens square = new UiTokens(tokens.panelPadding(), tokens.cardPadding(),
                    tokens.smallGap(), tokens.mediumGap(), tokens.largeGap(), tokens.buttonPadding(),
                    tokens.buttonMinimumWidth(), tokens.buttonMinimumHeight(), tokens.typography(),
                    new UiRadii(0, 0, 0, 0), tokens.motion());
            sourceToastTheme = current;
            toastTheme = new UiTheme(current.palette(), square);
        }
        return toastTheme;
    }

    private static NeoForgeMusicPlatform platform() {
        return new NeoForgeMusicPlatform(Minecraft.getInstance());
    }

    static PlayerSession.Snapshot snapshot() { return session.snapshot(platform()); }
    static long revision() { return session.revision(); }
    static List<MusicTrack> catalog() { return platform().eligibleTracks(); }
    static String idleStateKey() { return idleStateKey; }

    static void playPrevious() {
        if (inWorld()) announce("PREVIOUS", session.playPrevious(platform()));
    }

    static void playNext() {
        if (inWorld()) announce("NEXT", session.playNext(platform()));
    }

    static void togglePause() {
        if (inWorld() && session.togglePause(platform())) {
            Optional<MusicTrack> current = platform().currentTrack();
            if (current.isPresent()) showPlayback(current.get(), session.isPaused(current));
        }
    }

    static void enqueue(MusicTrack track) {
        if (inWorld()) {
            session.enqueue(track);
            refreshAutoplayInterval();
        }
    }

    static void playNow(MusicTrack track) {
        if (inWorld()) announce("PLAY_NOW", session.playNow(platform(), track));
    }

    static void skipTo(long key) {
        if (inWorld()) announce("SKIP_TO", session.skipTo(platform(), key));
    }

    static void setShuffle(boolean shuffle) {
        session.setShuffle(shuffle);
        saveSettings();
    }

    static void setMusicFrequency(MusicFrequency frequency) {
        if (session.savedState().musicFrequency() == frequency) return;
        session.setMusicFrequency(frequency);
        if (snapshot().mode() == PlayerSession.Mode.FREE_FLOW) refreshAutoplayInterval();
        saveSettings();
    }

    static void setPlaylistFrequency(MusicFrequency frequency) {
        if (session.savedState().playlistFrequency() == frequency) return;
        session.setPlaylistFrequency(frequency);
        if (snapshot().mode() == PlayerSession.Mode.PLAYLIST) refreshAutoplayInterval();
        saveSettings();
    }

    static void setBiomeBiasPercent(int percent) {
        if (session.savedState().biomeBiasPercent() == percent) return;
        session.setBiomeBiasPercent(percent);
        saveSettings();
    }

    static void setStartupMode(StartupMode startupMode) {
        if (session.savedState().startupMode() == startupMode) return;
        session.setStartupMode(startupMode);
        saveSettings();
    }

    /** Apply a new preference during silence without replacing the initial world-entry delay. */
    private static void refreshAutoplayInterval() {
        PlayerSession.Snapshot state = snapshot();
        if (state.current().isEmpty() && !state.history().isEmpty()) {
            autoplayDelayTicks = session.nextAutoplayDelayTicks(platform());
            autoplayObservedPlayback = false;
            idleStateKey = "interval";
        }
    }

    static void addToPlaylist(MusicTrack track) {
        if (session.addToPlaylist(track, catalog())) saveSettings();
    }

    static void removeFromPlaylist(MusicTrack track) {
        session.removeFromPlaylist(track);
        refreshAutoplayInterval();
        saveSettings();
    }

    static void activatePlaylist() {
        if (!inWorld()) return;
        announce("PLAYLIST", session.activatePlaylist(platform()));
        saveSettings();
    }

    static void freeFlow() {
        session.freeFlow();
        refreshAutoplayInterval();
        saveSettings();
    }

    private static void saveSettings() {
        if (!SETTINGS.save(session.savedState()) && Minecraft.getInstance().screen instanceof PlayerScreen screen) {
            screen.showSaveError();
        }
    }

    private static void announce(String action, Optional<MusicTrack> selected) {
        selected.ifPresent(track -> {
            autoplayObservedPlayback = true;
            autoplayDelayTicks = -1;
            idleStateKey = "nothing_playing";
            LOGGER.info("Playback action={} selectedTrack={}", action, MusicTrackDiagnostics.describe(track));
            showPlayback(track, false);
        });
    }

    private static void ensureWorldContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!inWorld()) {
            if (minecraft.getConnection() == null && activeConnection.get() != null) {
                NeoForgeMusicPlatform.stopOwnedTrack(minecraft);
                activeConnection.clear();
                activeLevel.clear();
                session = new PlayerSession(new Random(), session.savedState());
                PLAYBACK_TOAST.clear();
                autoplayObservedPlayback = false;
                autoplayDelayTicks = -1;
                idleStateKey = "nothing_playing";
            }
            return;
        }
        boolean enteredWorld = activeConnection.get() != minecraft.getConnection();
        if (enteredWorld) {
            NeoForgeMusicPlatform.stopOwnedTrack(minecraft);
            activeConnection = new WeakReference<>(minecraft.getConnection());
            session = new PlayerSession(new Random(), session.savedState());
            PLAYBACK_TOAST.clear();
            autoplayDelayTicks = randomInitialDelayTicks();
            autoplayObservedPlayback = false;
            idleStateKey = "starting";
        }
        if (activeLevel.get() != minecraft.level) {
            activeLevel = new WeakReference<>(minecraft.level);
            NeoForgeMusicCatalog.invalidate();
        }
        NeoForgeMusicPlatform.updateScreenMusicSuspension(minecraft);
        if (enteredWorld) showStartupToast();
    }

    private static void tickAutoplay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!inWorld() || minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F
                || (minecraft.screen != null && minecraft.screen.getBackgroundMusic() != null)) return;
        NeoForgeMusicPlatform platform = platform();
        if (platform.currentTrack().isPresent()) {
            autoplayObservedPlayback = true;
            autoplayDelayTicks = -1;
            return;
        }
        if (autoplayObservedPlayback) {
            autoplayDelayTicks = session.nextAutoplayDelayTicks(platform);
            idleStateKey = "interval";
        } else if (autoplayDelayTicks < 0) {
            autoplayDelayTicks = randomInitialDelayTicks();
            idleStateKey = "starting";
        }
        autoplayObservedPlayback = false;
        if (autoplayDelayTicks > 0) {
            autoplayDelayTicks--;
            return;
        }
        Optional<MusicTrack> started = session.playNext(platform);
        if (started.isPresent()) announce("AUTO_START", started);
        else {
            autoplayDelayTicks = AUTOPLAY_RETRY_TICKS;
            idleStateKey = "nothing_playing";
        }
    }

    private static int randomInitialDelayTicks() {
        return net.minecraft.util.RandomSource.create().nextInt(
                MIN_INITIAL_DELAY_TICKS, MAX_INITIAL_DELAY_TICKS + 1);
    }

    static boolean handleScreenKey(int keyCode, int scanCode) {
        if (OPEN_PLAYER_KEY.matches(keyCode, scanCode)) {
            Minecraft.getInstance().setScreen(null);
        } else if (PLAY_PREVIOUS_KEY.matches(keyCode, scanCode)) {
            playPrevious();
        } else if (PLAY_NEXT_KEY.matches(keyCode, scanCode)) {
            playNext();
        } else if (TOGGLE_PAUSE_KEY.matches(keyCode, scanCode)) {
            togglePause();
        } else return false;
        return true;
    }

    static void clearPlaybackToast() { PLAYBACK_TOAST.clear(); }

    private static void showStartupToast() {
        if (!MusicConfigNeoForge.SHOW_NOW_PLAYING.get()) return;
        boolean playlist = snapshot().mode() == PlayerSession.Mode.PLAYLIST;
        Component mode = Component.translatable("better_radio.player." + (playlist ? "playlist" : "free_flow"));
        TOAST_HOST.show(new Toast(toastMessage(Component.translatable("better_radio.started", mode)),
                Toast.Level.INFO, 100));
    }

    private static void showPlayback(MusicTrack track, boolean paused) {
        PlaybackToast.State state = paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING;
        PLAYBACK_TOAST.showAction(state, state, track.id(), paused, net.minecraft.Util.getMillis());
        if (MusicConfigNeoForge.SHOW_NOW_PLAYING.get()) {
            TOAST_HOST.show(new Toast(toastMessage(playbackMessage(track, paused)), Toast.Level.INFO, 40));
        }
    }

    private static void updatePlaybackToast() {
        if (!MusicConfigNeoForge.SHOW_NOW_PLAYING.get() || !inWorld()) return;
        Optional<MusicTrack> current = platform().currentTrack();
        if (current.isEmpty()) return;
        boolean paused = session.isPaused(current);
        PlaybackToast.State state = paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING;
        if (PLAYBACK_TOAST.update(state, current.get().id(), paused, net.minecraft.Util.getMillis())) {
            TOAST_HOST.show(new Toast(toastMessage(playbackMessage(current.get(), paused)), Toast.Level.INFO, 40));
        }
    }

    static Component toastMessage(Component message) {
        return Component.literal("Better Radio\n").append(message);
    }

    private static Component playbackMessage(MusicTrack track, boolean paused) {
        return paused ? Component.translatable("better_radio.status.paused")
                : Component.translatable("better_radio.now_playing", trackName(track));
    }

    static Component trackName(MusicTrack track) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            var songs = minecraft.level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG);
            Optional<Component> name = track.sourceIds().stream().filter(source -> source.startsWith("jukebox:"))
                    .sorted().map(source -> ResourceLocation.tryParse(source.substring("jukebox:".length())))
                    .filter(java.util.Objects::nonNull).map(songs::get).filter(java.util.Objects::nonNull)
                    .map(JukeboxSong::description).findFirst();
            if (name.isPresent()) return name.get();
        }
        if (track.sourceIds().stream().anyMatch(source -> source.startsWith("jukebox:"))) {
            return Component.translatable("better_radio.player.music_disc");
        }
        return Component.translatable("better_radio.track.background_named", MusicTrackNames.backgroundTitle(track));
    }
}
