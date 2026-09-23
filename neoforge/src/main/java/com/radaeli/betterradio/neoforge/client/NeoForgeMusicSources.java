package com.radaeli.betterradio.neoforge.client;

import com.radaeli.betterradio.music.MusicSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Discovers registered roots that can naturally provide gameplay background music. */
final class NeoForgeMusicSources {
    private static final String EVENT_SOURCE_PREFIX = "event:";
    private static final String BIOME_SOURCE_PREFIX = "biome:";
    private static final Set<ResourceLocation> observedSituationalEvents = ConcurrentHashMap.newKeySet();

    private NeoForgeMusicSources() {
    }

    static void observeSituationalMusic(Music music) {
        observedSituationalEvents.add(music.getEvent().value().getLocation());
    }

    static List<MusicSource> collect(Minecraft minecraft) {
        if (minecraft.level == null) {
            return List.of();
        }

        List<MusicSource> sources = new ArrayList<>();
        Registry<Biome> biomes = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);
        for (var entry : biomes.entrySet()) {
            entry.getValue().getBackgroundMusic().ifPresent(music -> addMusic(sources, music,
                    Optional.of(entry.getKey().location())));
        }

        addMusic(sources, Musics.CREATIVE, Optional.empty());
        addMusic(sources, Musics.END, Optional.empty());
        addMusic(sources, Musics.END_BOSS, Optional.empty());
        addMusic(sources, Musics.UNDER_WATER, Optional.empty());
        addMusic(sources, Musics.GAME, Optional.empty());
        for (ResourceLocation eventId : observedSituationalEvents) {
            sources.add(new MusicSource(eventId.toString(), Set.of(EVENT_SOURCE_PREFIX + eventId), Optional.empty()));
        }

        Registry<JukeboxSong> songs = minecraft.level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG);
        for (var entry : songs.entrySet()) {
            ResourceLocation eventId = entry.getValue().soundEvent().value().getLocation();
            sources.add(new MusicSource(eventId.toString(),
                    Set.of(EVENT_SOURCE_PREFIX + eventId, "jukebox:" + entry.getKey().location()), Optional.empty()));
        }
        return List.copyOf(sources);
    }

    private static void addMusic(List<MusicSource> sources, Music music, Optional<ResourceLocation> biomeId) {
        ResourceLocation eventId = music.getEvent().value().getLocation();
        Optional<String> biome = biomeId.map(ResourceLocation::toString);
        Set<String> sourceIds = biome.map(id -> Set.of(EVENT_SOURCE_PREFIX + eventId, BIOME_SOURCE_PREFIX + id))
                .orElseGet(() -> Set.of(EVENT_SOURCE_PREFIX + eventId));
        sources.add(new MusicSource(eventId.toString(), sourceIds, biome));
    }
}
