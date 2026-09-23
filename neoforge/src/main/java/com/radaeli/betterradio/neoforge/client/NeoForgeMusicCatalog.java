package com.radaeli.betterradio.neoforge.client;

import com.radaeli.betterradio.music.MusicCatalog;
import com.radaeli.betterradio.music.MusicSource;
import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.SoundDefinitionEntry;
import com.radaeli.betterradio.music.SoundDefinitionFlattener;
import com.radaeli.betterradio.neoforge.client.mixin.WeighedSoundEventsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds the NeoForge-side inventory from registries and loaded sound definitions. */
final class NeoForgeMusicCatalog {
    private NeoForgeMusicCatalog() {
    }

    static void observeSituationalMusic(Music music) {
        NeoForgeMusicSources.observeSituationalMusic(music);
    }

    static Snapshot collect(Minecraft minecraft) {
        MusicCatalog catalog = new MusicCatalog();
        Map<String, Sound> fileEntries = new LinkedHashMap<>();
        SoundManager soundManager = minecraft.getSoundManager();
        for (MusicSource source : NeoForgeMusicSources.collect(minecraft)) {
            ResourceLocation eventId = ResourceLocation.tryParse(source.soundEventId());
            if (eventId == null) {
                continue;
            }
            Map<String, Sound> files = flattenEvent(soundManager, eventId);
            for (Map.Entry<String, Sound> file : files.entrySet()) {
                fileEntries.putIfAbsent(file.getKey(), file.getValue());
                for (String sourceId : source.sourceIds()) {
                    catalog.add(file.getKey(), sourceId, source.biomeId());
                }
            }
        }
        return new Snapshot(catalog.tracks().stream().toList(), Map.copyOf(fileEntries));
    }

    /** Walks weighted sound entries and nested event references, then delegates cycle/dedupe to common. */
    private static Map<String, Sound> flattenEvent(SoundManager soundManager, ResourceLocation eventId) {
        Set<ResourceLocation> visited = new HashSet<>();
        Map<String, List<SoundDefinitionEntry>> definitions = new HashMap<>();
        Map<String, Sound> fileEntries = new LinkedHashMap<>();
        Deque<ResourceLocation> pending = new ArrayDeque<>();
        pending.add(eventId);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            WeighedSoundEvents group = soundManager.getSoundEvent(current);
            if (group == null || group == SoundManager.INTENTIONALLY_EMPTY_SOUND_EVENT) {
                continue;
            }
            List<SoundDefinitionEntry> entries = new ArrayList<>();
            for (Sound sound : weightedSounds(group)) {
                if (sound.getType() == Sound.Type.SOUND_EVENT) {
                    entries.add(SoundDefinitionEntry.event(sound.getLocation().toString()));
                    pending.addLast(sound.getLocation());
                } else if (sound.getType() == Sound.Type.FILE
                        && sound != SoundManager.EMPTY_SOUND
                        && sound != SoundManager.INTENTIONALLY_EMPTY_SOUND
                        && isPlayableFile(sound)) {
                    String fileId = sound.getLocation().toString();
                    entries.add(SoundDefinitionEntry.file(fileId));
                    fileEntries.putIfAbsent(fileId, sound);
                }
            }
            definitions.put(current.toString(), entries);
        }

        Map<String, Sound> flattened = new LinkedHashMap<>();
        for (String fileId : SoundDefinitionFlattener.flatten(eventId.toString(), definitions::get)) {
            Sound sound = fileEntries.get(fileId);
            if (sound != null) {
                flattened.put(fileId, sound);
            }
        }
        return flattened;
    }

    private static List<Sound> weightedSounds(WeighedSoundEvents group) {
        List<Sound> sounds = new ArrayList<>();
        for (Weighted<Sound> entry : ((WeighedSoundEventsAccessor) group).betterRadio$getSounds()) {
            Sound sound = entry.getSound(RandomSource.create(0L));
            if (sound != null) {
                sounds.add(sound);
            }
        }
        return sounds;
    }

    private static boolean isPlayableFile(Sound sound) {
        return sound.getLocation() != null && !sound.getLocation().getPath().isBlank();
    }

    record Snapshot(List<MusicTrack> tracks, Map<String, Sound> fileEntries) {
        Optional<MusicTrack> findTrack(String id) {
            return tracks.stream().filter(track -> track.id().equals(id)).findFirst();
        }
    }
}
