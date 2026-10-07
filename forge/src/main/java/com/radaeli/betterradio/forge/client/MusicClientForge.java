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
import com.radaeli.betterradio.music.MusicTrackDiagnostics;
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
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

/** Forge client keybind and adapter for vanilla background music. */
@Mod.EventBusSubscriber(modid = BetterRadio.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MusicClientForge {
    private static final int VANILLA_INITIAL_MUSIC_DELAY_TICKS = 100;
    private static final int AUTOPLAY_RETRY_TICKS = 100;
    private static final Random AUTOPLAY_RANDOM = new Random();
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
    private static ForgeMusicCatalog.Snapshot cachedCatalog;
    private static long cachedMusicAtMillis;
    private static MusicController controller = createController();
    private static final PlaybackToast PLAYBACK_TOAST = new PlaybackToast();
    private static boolean tickHandlerVerified;
    private static ForgeTrackSound ownedSound;
    private static MusicTrack ownedTrack;
    private static boolean ownedPaused;
    private static boolean suspendedForVanillaScreen;
    private static int vanillaMusicMinDelay = 12_000;
    private static int vanillaMusicMaxDelay = 24_000;
    private static int autoplayDelayTicks = -1;
    private static boolean autoplayObservedPlayback;

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
            if (PLAY_NEXT_KEY.consumeClick()) {
                LOGGER.info("Play/Next keybind pressed on Forge client");
                playNext();
            }
            if (TOGGLE_PAUSE_KEY.consumeClick()) {
                togglePause();
            }
            tickAutoplay();
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
            long now = net.minecraft.Util.getMillis();
            if (current.isPresent()) {
                boolean paused = controller.isPaused(current);
                PLAYBACK_TOAST.update(currentState(paused), current.get().id(), paused, now);
            }
            if (!PLAYBACK_TOAST.isVisible(now)) {
                return;
            }
            Optional<MusicTrack> labelTrack = current.or(controller.history()::lastTrack);
            Component text = playbackMessage(PLAYBACK_TOAST.state(), labelTrack);
            event.getGuiGraphics().drawCenteredString(minecraft.font, text,
                    event.getWindow().getGuiScaledWidth() / 2, event.getWindow().getGuiScaledHeight() - 48,
                    0xFFFFFF);
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
            LOGGER.warn("Playback action=PAUSE_TOGGLE result=unavailable level={} currentTrack={} resolvedFile={} historyBefore={}",
                    describeLevel(minecraft), describeTrack(currentBefore), resolvedAudioBefore, historyBefore);
            return;
        }
        Optional<MusicTrack> track = platform.currentTrack().or(controller.history()::lastTrack);
        boolean paused = controller.isPaused(track);
        LOGGER.info("Playback action=PAUSE_TOGGLE result={} level={} currentTrackBefore={} resolvedFileBefore={} currentTrackAfter={} resolvedFileAfter={} historyBefore={} historyAfter={}",
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

    private static void playNext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            LOGGER.info("Play/Next ignored: no active player or level");
            stopOwnedPlayback(minecraft);
            activeLevel.clear();
            controller = createController();
            PLAYBACK_TOAST.clear();
            return;
        }

        if (activeLevel.get() != minecraft.level) {
            stopOwnedPlayback(minecraft);
            activeLevel = new WeakReference<>(minecraft.level);
            controller = createController();
            PLAYBACK_TOAST.clear();
        }

        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
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

    private static PlaybackToast.State currentState(boolean paused) {
        return paused ? PlaybackToast.State.PAUSED : PlaybackToast.State.PLAYING;
    }

    private static void showAction(Minecraft minecraft, PlaybackToast.State action,
                                   MusicTrack track, boolean paused) {
        PLAYBACK_TOAST.showAction(action, currentState(paused), track.id(), paused,
                net.minecraft.Util.getMillis());
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
        return Component.translatable("better_radio.track.background");
    }

    private static void ensureWorldContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (activeLevel.get() != null) {
                stopOwnedPlayback(minecraft);
                LOGGER.info("Playback context reset reason=left_world previousLevel={} history={}",
                        describeLevel(activeLevel.get()), describeHistory(controller.history()));
                activeLevel.clear();
                controller = createController();
                PLAYBACK_TOAST.clear();
                autoplayDelayTicks = -1;
                autoplayObservedPlayback = false;
            }
            return;
        }
        if (activeLevel.get() != minecraft.level) {
            LOGGER.info("Playback context reset reason=level_changed previousLevel={} newLevel={} history={}",
                    describeLevel(activeLevel.get()), describeLevel(minecraft), describeHistory(controller.history()));
            stopOwnedPlayback(minecraft);
            activeLevel = new WeakReference<>(minecraft.level);
            controller = createController();
            PLAYBACK_TOAST.clear();
            autoplayDelayTicks = VANILLA_INITIAL_MUSIC_DELAY_TICKS;
            autoplayObservedPlayback = false;
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

    private static String describeTrack(Optional<MusicTrack> track) {
        return track.map(MusicTrackDiagnostics::describe).orElse("none");
    }

    private static String describeHistory(MusicHistory history) {
        return "cursor=" + history.position() + ", tracks="
                + history.tracks().stream().map(MusicTrackDiagnostics::describe).toList();
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

    private static MusicController createController() {
        return new MusicController(new MusicSelector(new Random()), new MusicHistory());
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
            if (ownedSound == null || ownedPaused
                    || !MusicChannelPauseBridge.setSoundInstancePaused(minecraft.getSoundManager(), ownedSound, true)) {
                return false;
            }
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
        if (minecraft.player == null || minecraft.level == null
                || (minecraft.screen != null && minecraft.screen.getBackgroundMusic() != null)) {
            return;
        }

        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        Optional<MusicTrack> current = platform.currentTrack();
        if (current.isPresent()) {
            autoplayObservedPlayback = true;
            autoplayDelayTicks = -1;
            return;
        }

        if (autoplayObservedPlayback) {
            autoplayObservedPlayback = false;
            autoplayDelayTicks = nextVanillaMusicDelayTicks();
        } else if (autoplayDelayTicks < 0) {
            autoplayDelayTicks = VANILLA_INITIAL_MUSIC_DELAY_TICKS;
        }

        if (autoplayDelayTicks > 0) {
            autoplayDelayTicks--;
            return;
        }

        Optional<MusicTrack> started = controller.playNext(platform);
        if (started.isPresent()) {
            autoplayObservedPlayback = true;
            autoplayDelayTicks = -1;
            LOGGER.info("Playback action=AUTO_START selectedTrack={} biome={}",
                    MusicTrackDiagnostics.describe(started.get()), platform.currentBiomeId().orElse("unknown"));
        } else {
            autoplayDelayTicks = AUTOPLAY_RETRY_TICKS;
            LOGGER.debug("Automatic music playback is waiting for eligible tracks");
        }
    }

    /** Retains vanilla's current situational music interval while Better Radio owns gameplay playback. */
    public static void observeVanillaMusic(Music music) {
        if (music == null) return;
        vanillaMusicMinDelay = Math.max(0, music.getMinDelay());
        vanillaMusicMaxDelay = Math.max(vanillaMusicMinDelay, music.getMaxDelay());
    }

    private static int nextVanillaMusicDelayTicks() {
        return AUTOPLAY_RANDOM.nextInt(vanillaMusicMinDelay, vanillaMusicMaxDelay + 1);
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
