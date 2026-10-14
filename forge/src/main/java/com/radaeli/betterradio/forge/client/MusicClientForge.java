package com.radaeli.betterradio.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.forge.MusicConfigForge;
import com.radaeli.betterradio.client.PlayerScreen;
import com.radaeli.betterradio.client.PlayerSettingsStore;
import com.radaeli.betterradio.client.PlayerUiAccess;
import com.radaeli.betterradio.client.AudioPlaybackProgress;
import com.radaeli.betterradio.client.RadioAudioChannel;
import com.radaeli.betterradio.client.TrackDurationCache;
import com.radaeli.betterradio.music.MusicFrequency;
import com.radaeli.betterradio.music.MusicAutoplayScheduler;
import com.radaeli.betterradio.music.MusicChannelPauseBridge;
import com.radaeli.betterradio.music.MusicPlatform;
import com.radaeli.betterradio.music.PlaybackToast;
import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.MusicTrackDiagnostics;
import com.radaeli.betterradio.music.MusicTrackNames;
import com.radaeli.betterradio.music.PlayerSession;
import com.radaeli.betterradio.music.StartupMode;
import com.radaeli.ruilib.ui.*;
import com.radaeli.betterradio.forge.client.ForgeMusicCatalog;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.Dist;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.OptionalDouble;

/** Forge client keybind and adapter for vanilla background music. */
@Mod.EventBusSubscriber(modid = BetterRadio.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MusicClientForge {
    private static final int MIN_INITIAL_MUSIC_DELAY_TICKS = 200;
    private static final int MAX_INITIAL_MUSIC_DELAY_TICKS = 300;
    private static final int AUTOPLAY_RETRY_TICKS = 100;
    private static final Random AUTOPLAY_RANDOM = new Random();
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRadio.MOD_ID);
    private static final PlaybackPauseStore PAUSE_STATE = new PlaybackPauseStore();
    private static final java.nio.file.Path SETTINGS_PATH = FMLPaths.CONFIGDIR.get().resolve("better_radio-player.json");
    private static final PlayerSettingsStore SETTINGS = new PlayerSettingsStore(SETTINGS_PATH);
    private static final String OPEN_PLAYER_KEY_NAME = "key.better_radio.open_player";
    private static final KeyMapping OPEN_PLAYER_KEY = new KeyMapping(
            OPEN_PLAYER_KEY_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.better_radio");
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
    private static WeakReference<net.minecraft.client.multiplayer.ClientPacketListener> activeConnection = new WeakReference<>(null);
    private static WeakReference<ClientLevel> registryLevel = new WeakReference<>(null);
    private static ForgeMusicCatalog.Snapshot cachedCatalog;
    private static long cachedMusicAtMillis;
    private static PlayerSession session = new PlayerSession(new Random(), importedSettings());
    private static final PlaybackToast PLAYBACK_TOAST = new PlaybackToast();
    private static boolean tickHandlerVerified;
    private static ForgeTrackSound ownedSound;
    private static MusicTrack ownedTrack;
    private static boolean ownedPaused;
    private static boolean suspendedForVanillaScreen;
    private static final MusicAutoplayScheduler AUTOPLAY = new MusicAutoplayScheduler();
    private static String idleStateKey = "nothing_playing";
    private static final ToastHost TOAST_HOST = new ToastHost();
    private static UiTheme sourceTheme;
    private static UiTheme squareTheme;
    private static final PlayerUiAccess UI_ACCESS = new PlayerUiAccess() {
        public boolean inWorld() { return MusicClientForge.inWorld(); }
        public UiTheme theme() { return MusicClientForge.playerTheme(); }
        public boolean handleScreenKey(int key, int scan) { return MusicClientForge.handleScreenKey(key, scan); }
        public PlayerSession.Snapshot snapshot() { return MusicClientForge.snapshot(); }
        public List<MusicTrack> catalog() { return MusicClientForge.catalog(); }
        public long revision() { return session.revision(); }
        public String idleStateKey() { return MusicClientForge.idleStateKey(); }
        public Component trackName(MusicTrack track) { return trackLabel(track); }
        public Component toastMessage(Component message) { return MusicClientForge.toastMessage(message); }
        public Progress playbackProgress() { return new ForgeMusicPlatform(Minecraft.getInstance()).playbackProgress(); }
        public void playPrevious() { MusicClientForge.playPrevious(); }
        public void playNext() { MusicClientForge.playNext(); }
        public void togglePause() { MusicClientForge.togglePause(); }
        public void freeFlow() { session.freeFlow(); refreshAutoplayInterval(); saveSettings(); }
        public void activatePlaylist() { MusicClientForge.activatePlaylist(); }
        public void setShuffle(boolean value) { session.setShuffle(value); saveSettings(); }
        public void skipTo(long key) { action(() -> session.skipTo(new ForgeMusicPlatform(Minecraft.getInstance()), key)); }
        public void setMusicFrequency(MusicFrequency value) {
            session.setMusicFrequency(value);
            if (session.snapshot(new ForgeMusicPlatform(Minecraft.getInstance())).mode() == PlayerSession.Mode.FREE_FLOW) {
                refreshAutoplayInterval();
            }
            saveSettings();
        }
        public void setPlaylistFrequency(MusicFrequency value) {
            session.setPlaylistFrequency(value);
            if (session.snapshot(new ForgeMusicPlatform(Minecraft.getInstance())).mode() == PlayerSession.Mode.PLAYLIST) {
                refreshAutoplayInterval();
            }
            saveSettings();
        }
        public void setBiomeBiasPercent(int value) { session.setBiomeBiasPercent(value); saveSettings(); }
        public void setStartupMode(StartupMode value) { session.setStartupMode(value); saveSettings(); }
        public void enqueue(MusicTrack track) { session.enqueue(track); saveSettings(); }
        public void playNow(MusicTrack track) { action(() -> session.playNow(new ForgeMusicPlatform(Minecraft.getInstance()), track)); }
        public void addToPlaylist(MusicTrack track) { session.addToPlaylist(track, catalog()); saveSettings(); }
        public void removeFromPlaylist(MusicTrack track) {
            session.removeFromPlaylist(track);
            refreshAutoplayInterval();
            saveSettings();
        }
    };

    private MusicClientForge() {
    }

    @SubscribeEvent
    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PLAYER_KEY);
        event.register(PLAY_NEXT_KEY);
        event.register(PLAY_PREVIOUS_KEY);
        event.register(TOGGLE_PAUSE_KEY);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("Registered Previous (F7), Play/Next (F8), and Pause/Resume (F9) keybinds and Forge client tick handler");
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
                cachedCatalog = null;
                registryLevel.clear();
                cachedMusicAtMillis = 0L;
                TrackDurationCache.invalidate();
                LOGGER.debug("Invalidated Better Radio's loaded music catalog after resource reload");
            }
        });
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
            if (OPEN_PLAYER_KEY.consumeClick() && inWorld()) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen instanceof PlayerScreen) minecraft.setScreen(null);
                else if (minecraft.screen == null) minecraft.setScreen(new PlayerScreen(UI_ACCESS));
            }
            if (PLAY_NEXT_KEY.consumeClick()) {
                LOGGER.info("Play/Next keybind pressed on Forge client");
                playNext();
            }
            if (TOGGLE_PAUSE_KEY.consumeClick()) {
                togglePause();
            }
            tickAutoplay();
            TOAST_HOST.tick();
        }

        @SubscribeEvent
        public static void soundSource(PlaySoundSourceEvent event) {
            if (event.getSound() instanceof ForgeTrackSound sound) {
                ((RadioAudioChannel) event.getChannel()).betterRadio$trackProgress(sound.progress);
            }
        }

        @SubscribeEvent
        public static void streamingSource(PlayStreamingSourceEvent event) {
            if (event.getSound() instanceof ForgeTrackSound sound) {
                ((RadioAudioChannel) event.getChannel()).betterRadio$trackProgress(sound.progress);
            }
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() != VanillaGuiOverlay.EXPERIENCE_BAR.type()
                    || !MusicConfigForge.SHOW_NOW_PLAYING.get()) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.font != null && TOAST_HOST.size() > 0) {
                int width = minecraft.getWindow().getGuiScaledWidth();
                int height = minecraft.getWindow().getGuiScaledHeight();
                UiTheme theme = playerTheme();
                TOAST_HOST.layout(minecraft.font, theme, new Bounds(0, 0, width, height));
                TOAST_HOST.render(new UiContext(event.getGuiGraphics(), minecraft.font, theme, 0, 0, width, height));
            }
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
        String historyBefore = describeHistory(session.snapshot(platform).history());
        if (!session.togglePause(platform)) {
            LOGGER.warn("Playback action=PAUSE_TOGGLE result=unavailable level={} currentTrack={} resolvedFile={} historyBefore={}",
                    describeLevel(minecraft), describeTrack(currentBefore), resolvedAudioBefore, historyBefore);
            return;
        }
        if (session.isPlaybackPaused()) AUTOPLAY.pause(); else AUTOPLAY.manualStart();
        Optional<MusicTrack> track = platform.currentTrack().or(() -> lastHistoryTrack(platform));
        boolean paused = session.isPaused(track);
        LOGGER.info("Playback action=PAUSE_TOGGLE result={} level={} currentTrackBefore={} resolvedFileBefore={} currentTrackAfter={} resolvedFileAfter={} historyBefore={} historyAfter={}",
                paused ? "paused" : "playing", describeLevel(minecraft), describeTrack(currentBefore),
                resolvedAudioBefore, describeTrack(platform.currentTrack()), describeResolvedSound(minecraft), historyBefore,
                describeHistory(session.snapshot(platform).history()));
        track.ifPresent(value -> showAction(minecraft,
                paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING, value, paused));
        saveSettings();
    }

    private static void playPrevious() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(session.snapshot(platform).history());
        Optional<MusicTrack> previous = session.playPrevious(platform);
        LOGGER.info("Playback action=PREVIOUS level={} currentTrackBefore={} resolvedFileBefore={} selectedTrack={} currentTrackAfter={} resolvedFileAfter={} historyBefore={} historyAfter={}",
                describeLevel(minecraft), describeTrack(currentBefore), resolvedAudioBefore,
                describeTrack(previous), describeTrack(platform.currentTrack()), describeResolvedSound(minecraft),
                historyBefore, describeHistory(session.snapshot(platform).history()));
        if (previous.isEmpty()) {
            LOGGER.info("Previous keybind ignored: playback history has no earlier track");
        } else {
            LOGGER.info("Playing previous background music {}", MusicTrackDiagnostics.describe(previous.get()));
            showAction(minecraft, PlaybackToast.State.PREVIOUS, previous.get(), false);
        }
    }

    private static void playNext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            LOGGER.info("Play/Next ignored: no active player or level");
            stopOwnedPlayback(minecraft);
            activeLevel.clear();
            PLAYBACK_TOAST.clear();
            return;
        }

        if (activeLevel.get() != minecraft.level) {
            stopOwnedPlayback(minecraft);
            activeLevel = new WeakReference<>(minecraft.level);
            PLAYBACK_TOAST.clear();
        }

        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        Optional<MusicTrack> currentBefore = platform.currentTrack();
        String resolvedAudioBefore = describeResolvedSound(minecraft);
        String historyBefore = describeHistory(session.snapshot(platform).history());
        Optional<MusicTrack> started = session.playNext(platform);
        LOGGER.info("Playback action=NEXT level={} eligibleTracks={} currentTrackBefore={} resolvedFileBefore={} selectedTrack={} currentTrackAfter={} resolvedFileAfter={} historyBefore={} historyAfter={}",
                describeLevel(minecraft), platform.eligibleTracks().size(), describeTrack(currentBefore),
                resolvedAudioBefore, describeTrack(started), describeTrack(platform.currentTrack()),
                describeResolvedSound(minecraft), historyBefore, describeHistory(session.snapshot(platform).history()));
        started.ifPresent(track -> showAction(minecraft, PlaybackToast.State.NEXT, track, false));
    }

    private static PlaybackToast.State currentState(boolean paused) {
        return paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING;
    }

    private static void showAction(Minecraft minecraft, PlaybackToast.State action,
                                   MusicTrack track, boolean paused) {
        PLAYBACK_TOAST.showAction(action, currentState(paused), track.id(), paused,
                net.minecraft.Util.getMillis());
        toastPlayback(track, paused);
    }

    private static Component playbackMessage(PlaybackToast.State state, Optional<MusicTrack> track) {
        if (state == PlaybackToast.State.PAUSED) {
            return Component.translatable(PlaybackToast.State.PAUSED.translationKey());
        }
        return Component.translatable("better_radio.now_playing",
                track.map(MusicClientForge::trackLabel).orElseGet(() ->
                        Component.translatable("better_radio.track.background")));
    }

    private static Component trackLabel(MusicTrack track) {
        for (String source : track.sourceIds().stream().filter(id -> id.startsWith("record:")).sorted().toList()) {
            ResourceLocation itemId = ResourceLocation.tryParse(source.substring("record:".length()));
            if (itemId == null) {
                continue;
            }
            String songDescriptionKey = "item." + itemId.getNamespace() + "." + itemId.getPath() + ".desc";
            if (Language.getInstance().has(songDescriptionKey)) {
                return Component.translatable(songDescriptionKey);
            }
            var item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null && Language.getInstance().has(item.getDescriptionId())) {
                return Component.translatable(item.getDescriptionId());
            }
        }
        return Component.translatable("better_radio.track.background_named", MusicTrackNames.backgroundTitle(track));
    }

    private static void ensureWorldContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (activeConnection.get() != null) {
                stopOwnedPlayback(minecraft);
                LOGGER.info("Playback context reset reason=left_world previousLevel={} history={}",
                        describeLevel(activeLevel.get()), describeHistory(List.of()));
                activeLevel.clear();
                activeConnection.clear();
                session = new PlayerSession(new Random(), session.savedState());
                PLAYBACK_TOAST.clear();
                AUTOPLAY.reset();
            }
            return;
        }
        if (activeConnection.get() != minecraft.getConnection()) {
            activeConnection = new WeakReference<>(minecraft.getConnection());
            session = new PlayerSession(new Random(), session.savedState());
            PLAYBACK_TOAST.clear();
            AUTOPLAY.startSession(session.isPlaybackPaused(), randomInitialDelayTicks());
            idleStateKey = session.isPlaybackPaused() ? "nothing_playing" : "starting";
            showStartupToast();
        }
        if (activeLevel.get() != minecraft.level) {
            LOGGER.info("Playback context reset reason=level_changed previousLevel={} newLevel={} history={}",
                    describeLevel(activeLevel.get()), describeLevel(minecraft), describeHistory(List.of()));
            stopOwnedPlayback(minecraft);
            activeLevel = new WeakReference<>(minecraft.level);
        }
        boolean screenOwnsBackground = minecraft.screen != null && minecraft.screen.getBackgroundMusic() != null;
        if (screenOwnsBackground && ownedSound != null && !ownedPaused && !suspendedForVanillaScreen) {
            suspendedForVanillaScreen = MusicChannelPauseBridge.setSoundInstancePaused(
                    minecraft.getSoundManager(), ownedSound, true);
        } else if (!screenOwnsBackground && ownedSound != null && suspendedForVanillaScreen) {
            if (!ownedPaused) {
                MusicChannelPauseBridge.setSoundInstancePaused(minecraft.getSoundManager(), ownedSound, false);
            }
            suspendedForVanillaScreen = false;
        }
    }

    private static PlayerSession.SavedState importedSettings() {
        boolean jsonMissing = !java.nio.file.Files.exists(SETTINGS_PATH);
        Optional<MusicTrack> legacyPause = PAUSE_STATE.load();
        PlayerSession.SavedState saved = SETTINGS.loadWithLegacyPause(legacyPause);
        if (jsonMissing && legacyPause.isPresent()) SETTINGS.save(saved);
        return saved;
    }

    static boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null;
    }

    static PlayerSession.Snapshot snapshot() { return session.snapshot(new ForgeMusicPlatform(Minecraft.getInstance())); }
    static List<MusicTrack> catalog() { return new ForgeMusicPlatform(Minecraft.getInstance()).eligibleTracks(); }
    static String idleStateKey() {
        return session.isPlaybackPaused() ? "nothing_playing" : idleStateKey;
    }

    static UiTheme playerTheme() {
        UiTheme current = UiThemes.globalTheme();
        if (current != sourceTheme) {
            UiTokens tokens = current.tokens();
            UiTokens square = new UiTokens(tokens.panelPadding(), tokens.cardPadding(), tokens.smallGap(),
                    tokens.mediumGap(), tokens.largeGap(), tokens.buttonPadding(), tokens.buttonMinimumWidth(),
                    tokens.buttonMinimumHeight(), tokens.typography(), new UiRadii(0, 0, 0, 0), tokens.motion());
            sourceTheme = current;
            squareTheme = new UiTheme(current.palette(), square);
        }
        return squareTheme;
    }

    static boolean handleScreenKey(int keyCode, int scanCode) {
        if (OPEN_PLAYER_KEY.matches(keyCode, scanCode)) Minecraft.getInstance().setScreen(null);
        else if (PLAY_PREVIOUS_KEY.matches(keyCode, scanCode)) playPrevious();
        else if (PLAY_NEXT_KEY.matches(keyCode, scanCode)) playNext();
        else if (TOGGLE_PAUSE_KEY.matches(keyCode, scanCode)) togglePause();
        else return false;
        return true;
    }

    static Component toastMessage(Component message) { return Component.literal("Better Radio\n").append(message); }

    private static void toastPlayback(MusicTrack track, boolean paused) {
        if (!MusicConfigForge.SHOW_NOW_PLAYING.get()) return;
        Component message = paused ? Component.translatable("better_radio.status.paused")
                : Component.translatable("better_radio.now_playing", trackLabel(track));
        TOAST_HOST.show(new Toast(toastMessage(message), Toast.Level.INFO, 40));
    }

    private static void showStartupToast() {
        if (!MusicConfigForge.SHOW_NOW_PLAYING.get()) return;
        boolean playlistMode = snapshot().mode() == PlayerSession.Mode.PLAYLIST;
        Component mode = Component.translatable("better_radio.player." + (playlistMode ? "playlist" : "free_flow"));
        TOAST_HOST.show(new Toast(toastMessage(Component.translatable("better_radio.started", mode)),
                Toast.Level.INFO, 100));
    }

    private static void saveSettings() {
        SETTINGS.save(session.savedState());
    }

    private static void action(java.util.function.Supplier<Optional<MusicTrack>> operation) {
        if (!inWorld() || session.isPlaybackPaused()) return;
        operation.get().ifPresent(track -> {
            AUTOPLAY.manualStart();
            showAction(Minecraft.getInstance(), PlaybackToast.State.NEXT, track, false);
        });
    }

    private static void activatePlaylist() {
        action(() -> session.activatePlaylist(new ForgeMusicPlatform(Minecraft.getInstance())));
        saveSettings();
    }

    private static void refreshAutoplayInterval() {
        if (session.isPlaybackPaused()) return;
        PlayerSession.Snapshot state = snapshot();
        if (state.current().isEmpty() && !state.history().isEmpty()) {
            AUTOPLAY.reschedule(session.nextAutoplayDelayTicks(new ForgeMusicPlatform(Minecraft.getInstance())));
            idleStateKey = "interval";
        }
    }

    private static String describeTrack(Optional<MusicTrack> track) {
        return track.map(MusicTrackDiagnostics::describe).orElse("none");
    }

    private static String describeHistory(List<MusicTrack> history) {
        return "tracks=" + history.stream().map(MusicTrackDiagnostics::describe).toList();
    }

    private static String describeResolvedSound(Minecraft minecraft) {
        if (ownedSound == null) return "none";
        Sound resolved = ownedSound.getSound();
        return resolved == null ? ownedSound.getLocation().toString() : resolved.getPath().toString();
    }

    private static String describeLevel(Minecraft minecraft) {
        return minecraft.level == null ? "none" : describeLevel(minecraft.level);
    }

    private static String describeLevel(ClientLevel level) {
        return level == null ? "none" : level.dimension().location() + "@"
                + Integer.toHexString(System.identityHashCode(level));
    }

    private static Optional<MusicTrack> lastHistoryTrack(MusicPlatform platform) {
        List<MusicTrack> history = session.snapshot(platform).history();
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1));
    }

    private static final class ForgeMusicPlatform implements MusicPlatform {
        private final Minecraft minecraft;
        private final ForgeMusicCatalog.Snapshot catalog;

        private ForgeMusicPlatform(Minecraft minecraft) {
            this.minecraft = minecraft;
            long now = System.currentTimeMillis();
            if (registryLevel.get() == minecraft.level && cachedCatalog != null
                    && now - cachedMusicAtMillis < 5_000L) {
                this.catalog = cachedCatalog;
            } else {
                this.catalog = ForgeMusicCatalog.discover(minecraft);
                cachedCatalog = this.catalog;
                registryLevel = new WeakReference<>(minecraft.level);
                cachedMusicAtMillis = now;
            }
        }

        @Override
        public List<MusicTrack> eligibleTracks() {
            return catalog.tracks();
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
            if (ownedSound != null && !minecraft.getSoundManager().isActive(ownedSound)) {
                ownedSound = null;
                ownedTrack = null;
                ownedPaused = false;
                suspendedForVanillaScreen = false;
                PLAYBACK_TOAST.clear();
            }
            if (ownedSound == null || ownedTrack == null) return Optional.empty();
            Sound sound = ownedSound.getSound();
            String fileId = sound == null ? ownedSound.getLocation().toString() : sound.getLocation().toString();
            return Optional.of(new MusicTrack(fileId, ownedTrack.biomeIds(), ownedTrack.sourceIds()));
        }

        private PlayerUiAccess.Progress playbackProgress() {
            ForgeTrackSound sound = ownedSound;
            if (sound == null) return new PlayerUiAccess.Progress(0, OptionalDouble.empty());
            sound.progress.requestUpdates();
            OptionalDouble duration = TrackDurationCache.get(minecraft.getResourceManager(), sound.fixedFile.getPath())
                    .getNow(OptionalDouble.empty());
            double position = sound.progress.positionSeconds();
            return new PlayerUiAccess.Progress(duration.isPresent()
                    ? Math.min(position, duration.getAsDouble()) : position, duration);
        }

        @Override
        public void stopCurrentTrack() {
            stopOwnedPlayback(minecraft);
        }

        @Override
        public void startTrack(MusicTrack track) {
            ResourceLocation fileId = ResourceLocation.tryParse(track.id());
            Sound sourceDefinition = catalog.soundDefinitions().get(track.id());
            if (fileId == null || sourceDefinition == null) {
                LOGGER.warn("Could not start selected audio file {}; it is absent from the current gameplay catalog", track.id());
                return;
            }
            stopOwnedPlayback(minecraft);
            ForgeTrackSound instance = new ForgeTrackSound(fileId, sourceDefinition);
            ownedSound = instance;
            ownedTrack = catalog.tracks().stream().filter(candidate -> candidate.id().equals(track.id()))
                    .findFirst().orElse(track);
            ownedPaused = false;
            minecraft.getSoundManager().play(instance);
            LOGGER.info("Started owned background audio file {}", MusicTrackDiagnostics.describe(ownedTrack));
        }

        @Override
        public boolean pauseCurrentTrack() {
            if (ownedSound == null
                    || !MusicChannelPauseBridge.setSoundInstancePaused(minecraft.getSoundManager(), ownedSound, true)) {
                return false;
            }
            // PlayerSession reasserts its pause latch every client tick. Keep the
            // actual channel paused if a screen transition resumed it underneath us.
            ownedPaused = true;
            return true;
        }

        @Override
        public boolean resumeCurrentTrack() {
            if (ownedSound == null || !ownedPaused
                    || !MusicChannelPauseBridge.setSoundInstancePaused(minecraft.getSoundManager(), ownedSound, false)) {
                return false;
            }
            ownedPaused = false;
            return true;
        }
    }

    private static void tickAutoplay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        if (session.isPlaybackPaused()) {
            AUTOPLAY.pause();
            session.maintainPausedPlayback(platform);
            return;
        }
        Optional<MusicTrack> current = platform.currentTrack();
        boolean mayAdvance = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0.0F
                && (minecraft.screen == null || minecraft.screen.getBackgroundMusic() == null);
        java.util.concurrent.atomic.AtomicReference<Optional<MusicTrack>> selected =
                new java.util.concurrent.atomic.AtomicReference<>(Optional.empty());
        MusicAutoplayScheduler.Tick result = AUTOPLAY.tick(false, current.isPresent(), mayAdvance,
                MusicClientForge::randomInitialDelayTicks, () -> session.nextAutoplayDelayTicks(platform),
                () -> {
                    selected.set(session.playNext(platform));
                    return selected.get().isPresent();
                }, AUTOPLAY_RETRY_TICKS);
        if (result == MusicAutoplayScheduler.Tick.STARTING) idleStateKey = "starting";
        if (result == MusicAutoplayScheduler.Tick.INTERVAL) idleStateKey = "interval";
        if (result == MusicAutoplayScheduler.Tick.STARTED && selected.get().isPresent()) {
            idleStateKey = "nothing_playing";
            LOGGER.info("Playback action=AUTO_START selectedTrack={} biome={}",
                    MusicTrackDiagnostics.describe(selected.get().get()), platform.currentBiomeId().orElse("unknown"));
            toastPlayback(selected.get().get(), false);
        } else if (result == MusicAutoplayScheduler.Tick.NO_TRACK) {
            idleStateKey = "nothing_playing";
            LOGGER.debug("Automatic music playback is waiting for eligible tracks");
        }
    }

    /** Compatibility hook retained for the vanilla music selection mixin. */
    public static void observeVanillaMusic(Music music) {
        // Better Radio's interval preferences control both modes now.
    }

    private static int randomInitialDelayTicks() {
        return AUTOPLAY_RANDOM.nextInt(MIN_INITIAL_MUSIC_DELAY_TICKS, MAX_INITIAL_MUSIC_DELAY_TICKS + 1);
    }

    private static void stopOwnedPlayback(Minecraft minecraft) {
        if (ownedSound != null) minecraft.getSoundManager().stop(ownedSound);
        ownedSound = null;
        ownedTrack = null;
        ownedPaused = false;
        suspendedForVanillaScreen = false;
    }

    private static final class ForgeTrackSound extends AbstractSoundInstance {
        private final Sound fixedFile;
        private final WeighedSoundEvents fixedGroup;
        private final AudioPlaybackProgress progress = new AudioPlaybackProgress();

        private ForgeTrackSound(ResourceLocation fileId, Sound sourceDefinition) {
            super(fileId, SoundSource.MUSIC, RandomSource.create());
            this.fixedFile = new Sound(fileId.toString(), sourceDefinition.getVolume(), sourceDefinition.getPitch(),
                    1, Sound.Type.FILE, sourceDefinition.shouldStream(), sourceDefinition.shouldPreload(),
                    sourceDefinition.getAttenuationDistance());
            this.fixedGroup = new WeighedSoundEvents(fileId, null);
            this.fixedGroup.addSound(fixedFile);
            this.sound = fixedFile;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.looping = false;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            this.sound = fixedFile;
            return fixedGroup;
        }
    }
}
