package com.radaeli.betterradio.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.music.MusicController;
import com.radaeli.betterradio.music.MusicHistory;
import com.radaeli.betterradio.music.MusicPlatform;
import com.radaeli.betterradio.music.MusicSelector;
import com.radaeli.betterradio.music.MusicTrack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeConfigSpec;
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
    private static final ForgeConfigSpec.Builder CONFIG_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue SHOW_NOW_PLAYING = CONFIG_BUILDER
            .comment("Show Better Radio playback status above the experience bar")
            .define("showNowPlaying", true);
    public static final ForgeConfigSpec CONFIG = CONFIG_BUILDER.build();
    private static final KeyMapping PLAY_NEXT_KEY = new KeyMapping(
            "key.better_radio.play_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8,
            "key.categories.better_radio");
    private static WeakReference<ClientLevel> activeLevel = new WeakReference<>(null);
    private static WeakReference<ClientLevel> registryLevel = new WeakReference<>(null);
    private static Map<String, Music> cachedMusic = Map.of();
    private static MusicController controller = createController();
    private static boolean tickHandlerVerified;

    private MusicClientForge() {
    }

    @SubscribeEvent
    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(PLAY_NEXT_KEY);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("Registered Play/Next keybind (default F8) and Forge client tick handler");
    }

    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (!tickHandlerVerified) {
                tickHandlerVerified = true;
                LOGGER.info("Forge Play/Next client tick handler is active");
            }
            if (!PLAY_NEXT_KEY.consumeClick()) {
                return;
            }
            LOGGER.info("Play/Next keybind pressed on Forge client");
            playNext();
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() != VanillaGuiOverlay.EXPERIENCE_BAR.type() || !SHOW_NOW_PLAYING.get()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }
            Optional<MusicTrack> current = new ForgeMusicPlatform(minecraft).currentTrack();
            if (current.isEmpty()) {
                return;
            }
            String text;
            if (minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F) {
                text = Component.translatable("better_radio.status.muted").getString();
            } else if (minecraft.isPaused()) {
                text = Component.translatable("better_radio.status.paused").getString();
            } else {
                text = Component.translatable("better_radio.status.now_playing", current.get().displayName()).getString();
            }
            event.getGuiGraphics().drawCenteredString(minecraft.font, text,
                    event.getWindow().getGuiScaledWidth() / 2, event.getWindow().getGuiScaledHeight() - 48, 0xFFFFFF);
        }
    }

    private static void playNext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            LOGGER.info("Play/Next ignored: no active player or level");
            activeLevel.clear();
            controller = createController();
            return;
        }

        if (activeLevel.get() != minecraft.level) {
            activeLevel = new WeakReference<>(minecraft.level);
            controller = createController();
        }

        MusicPlatform platform = new ForgeMusicPlatform(minecraft);
        Optional<MusicTrack> started = controller.playNext(platform);
        LOGGER.info("Play/Next requested: {} eligible tracks, selected={}",
                platform.eligibleTracks().size(), started.map(MusicTrack::id).orElse("none"));
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
                    .map(id -> new MusicTrack(id, displayName(id)))
                    .toList();
        }

        @Override
        public Optional<MusicTrack> currentTrack() {
            return musicById.entrySet().stream()
                    .filter(entry -> minecraft.getMusicManager().isPlayingMusic(entry.getValue()))
                    .map(entry -> new MusicTrack(entry.getKey(), displayName(entry.getKey())))
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

        private static Map<String, Music> collectMusic(Minecraft minecraft) {
            if (registryLevel.get() == minecraft.level) {
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
            return cachedMusic;
        }

        private static String displayName(String id) {
            String path = ResourceLocation.tryParse(id).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
            return name.isEmpty() ? id : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }
}
