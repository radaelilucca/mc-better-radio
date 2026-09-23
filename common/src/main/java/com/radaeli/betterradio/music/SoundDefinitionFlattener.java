package com.radaeli.betterradio.music;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Flattens loaded sound-event definitions into distinct concrete file ids. */
public final class SoundDefinitionFlattener {
    private SoundDefinitionFlattener() {
    }

    /**
     * Traverses event references in encounter order. Cycles and shared nested events are visited once;
     * missing definitions contribute no entries.
     */
    public static List<String> flatten(String rootEventId,
                                       Function<String, ? extends Collection<SoundDefinitionEntry>> lookup) {
        Objects.requireNonNull(rootEventId, "rootEventId");
        Objects.requireNonNull(lookup, "lookup");
        if (rootEventId.isBlank()) {
            throw new IllegalArgumentException("rootEventId must not be blank");
        }

        Deque<String> pending = new ArrayDeque<>();
        LinkedHashSet<String> visitedEvents = new LinkedHashSet<>();
        LinkedHashSet<String> files = new LinkedHashSet<>();
        pending.addLast(rootEventId);
        while (!pending.isEmpty()) {
            String eventId = pending.removeFirst();
            if (!visitedEvents.add(eventId)) {
                continue;
            }
            Collection<SoundDefinitionEntry> entries = lookup.apply(eventId);
            if (entries == null) {
                continue;
            }
            for (SoundDefinitionEntry entry : entries) {
                if (entry.eventReference()) {
                    pending.addLast(entry.id());
                } else {
                    files.add(entry.id());
                }
            }
        }
        return List.copyOf(files);
    }
}
