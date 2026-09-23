package com.radaeli.betterradio.forge.client;

import com.radaeli.betterradio.music.MusicCatalog;
import com.radaeli.betterradio.music.MusicSource;
import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.SoundDefinitionEntry;
import com.radaeli.betterradio.music.SoundDefinitionFlattener;
import com.radaeli.betterradio.forge.mixin.client.WeighedSoundEventsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves natural SoundEvent roots into distinct concrete audio file identities. */
final class ForgeMusicCatalog {
    private ForgeMusicCatalog() {
    }

    static Snapshot discover(Minecraft minecraft) {
        MusicCatalog catalog = new MusicCatalog();
        Map<String, Sound> soundDefinitions = new LinkedHashMap<>();
        SoundManager soundManager = minecraft.getSoundManager();

        for (MusicSource source : ForgeMusicSources.collect(minecraft)) {
            ResourceLocation rootId = ResourceLocation.tryParse(source.soundEventId());
            if (rootId == null || rootId.equals(SoundManager.INTENTIONALLY_EMPTY_SOUND_LOCATION)) continue;
            List<String> files = SoundDefinitionFlattener.flatten(source.soundEventId(), referencedEvent -> {
                ResourceLocation eventId = ResourceLocation.tryParse(referencedEvent);
                if (eventId == null || eventId.equals(SoundManager.INTENTIONALLY_EMPTY_SOUND_LOCATION)) return List.of();
                WeighedSoundEvents event = soundManager.getSoundEvent(eventId);
                if (event == null || event == SoundManager.INTENTIONALLY_EMPTY_SOUND_EVENT) return List.of();

                List<SoundDefinitionEntry> entries = new ArrayList<>();
                for (var weightedEntry : ((WeighedSoundEventsAccessor) event).betterRadio$getEntries()) {
                    Sound sound = weightedEntry.getSound(RandomSource.create());
                    if (sound == null || sound == SoundManager.EMPTY_SOUND
                            || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) continue;
                    String soundId = sound.getLocation().toString();
                    if (sound.getType() == Sound.Type.FILE) {
                        soundDefinitions.putIfAbsent(soundId, sound);
                        entries.add(SoundDefinitionEntry.file(soundId));
                    } else {
                        entries.add(SoundDefinitionEntry.event(soundId));
                    }
                }
                return entries;
            });
            for (String file : files) {
                for (String sourceId : source.sourceIds()) {
                    catalog.add(file, sourceId, source.biomeId());
                }
            }
        }

        return new Snapshot(catalog.tracks().stream().toList(), Map.copyOf(soundDefinitions));
    }

    record Snapshot(List<MusicTrack> tracks, Map<String, Sound> soundDefinitions) {
    }
}
