package com.radaeli.betterradio.neoforge.client;

import com.radaeli.betterradio.music.OggTrackDuration;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

/** Requested only by the visible player. Keeps a bounded cache, including unknown durations. */
final class TrackDurationCache {
    private static final int MAX_ENTRIES = 64;
    private static final LinkedHashMap<ResourceLocation, CompletableFuture<OptionalDouble>> CACHE =
            new LinkedHashMap<>(16, 0.75F, true);

    private TrackDurationCache() { }

    static CompletableFuture<OptionalDouble> get(ResourceManager resources, ResourceLocation file) {
        var cached = CACHE.get(file);
        if (cached != null) return cached;
        var future = CompletableFuture.supplyAsync(() -> {
            try (var input = new BufferedInputStream(resources.open(file))) {
                return OggTrackDuration.read(input);
            } catch (IOException | RuntimeException ignored) {
                return OptionalDouble.empty();
            }
        }, Util.ioPool());
        CACHE.put(file, future);
        if (CACHE.size() > MAX_ENTRIES) CACHE.remove(CACHE.keySet().iterator().next());
        return future;
    }

    static void invalidate() { CACHE.clear(); }
}
