package com.radaeli.betterradio.forge.client;

import com.radaeli.betterradio.music.MusicSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Collects eligible gameplay SoundEvent roots and their original source context. */
final class ForgeMusicSources {
    private ForgeMusicSources() {
    }

    static List<MusicSource> collect(Minecraft minecraft) {
        List<MusicSource> sources = new ArrayList<>();
        minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).entrySet().forEach(entry ->
                entry.getValue().getBackgroundMusic().ifPresent(music -> add(
                        sources, music, "biome:" + entry.getKey(), Optional.of(entry.getKey().toString()))));

        add(sources, Musics.CREATIVE, "situation:creative", Optional.empty());
        add(sources, Musics.END_BOSS, "situation:end_boss", Optional.empty());
        add(sources, Musics.END, "situation:end", Optional.empty());
        add(sources, Musics.UNDER_WATER, "situation:underwater", Optional.empty());
        add(sources, Musics.GAME, "situation:game", Optional.empty());

        // Modded selection hooks can supply a situational Music without adding
        // it to biome data. Keep the currently selected gameplay source too.
        if (minecraft.player != null
                && (minecraft.screen == null || minecraft.screen.getBackgroundMusic() == null)) {
            Music current = minecraft.getSituationalMusic();
            add(sources, current, "situation:active:" + current.getEvent().value().getLocation(), Optional.empty());
        }

        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            var item = entry.getValue();
            if (item instanceof RecordItem record) {
                ResourceLocation itemId = entry.getKey().location();
                sources.add(new MusicSource(record.getSound().getLocation().toString(),
                        java.util.Set.of("record:" + itemId), Optional.empty()));
            }
        }
        return List.copyOf(sources);
    }

    private static void add(List<MusicSource> sources, Music music, String sourceId, Optional<String> biomeId) {
        sources.add(new MusicSource(music.getEvent().value().getLocation().toString(),
                java.util.Set.of(sourceId), biomeId));
    }
}
