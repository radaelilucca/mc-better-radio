package com.radaeli.betterradio.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/** Personal player state, independent of Minecraft screens and audio implementations. */
public final class PlayerSession {
    public enum Mode { FREE_FLOW, PLAYLIST }
    public enum Source { HISTORY, MANUAL, PLAYLIST }
    public record QueueEntry(long key, MusicTrack track, Source source) { }
    public record SavedState(List<MusicTrack> playlist, boolean shuffle, Mode mode, MusicFrequency musicFrequency,
                             int biomeBiasPercent, StartupMode startupMode, MusicFrequency playlistFrequency) {
        public SavedState(List<MusicTrack> playlist, boolean shuffle, Mode mode) {
            this(playlist, shuffle, mode, MusicFrequency.BALANCED);
        }

        public SavedState(List<MusicTrack> playlist, boolean shuffle, Mode mode, MusicFrequency musicFrequency) {
            this(playlist, shuffle, mode, musicFrequency, MusicSelector.DEFAULT_BIOME_BIAS_PERCENT, StartupMode.LAST_USED);
        }

        public SavedState(List<MusicTrack> playlist, boolean shuffle, Mode mode, MusicFrequency musicFrequency,
                          int biomeBiasPercent, StartupMode startupMode) {
            this(playlist, shuffle, mode, musicFrequency, biomeBiasPercent, startupMode, MusicFrequency.NON_STOP);
        }

        public SavedState {
            playlist = List.copyOf(playlist);
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(musicFrequency, "musicFrequency");
            Objects.requireNonNull(playlistFrequency, "playlistFrequency");
            Objects.requireNonNull(startupMode, "startupMode");
            if (biomeBiasPercent < 0 || biomeBiasPercent > 100) {
                throw new IllegalArgumentException("Biome bias must be between 0 and 100");
            }
        }
    }
    public record Snapshot(Optional<MusicTrack> current, boolean paused, Mode mode, boolean shuffle,
                           List<QueueEntry> upcoming, List<MusicTrack> history,
                           List<MusicTrack> playlist, boolean randomNext, boolean canPrevious,
                           MusicFrequency musicFrequency, int biomeBiasPercent, StartupMode startupMode,
                           MusicFrequency playlistFrequency) { }

    private final Random random;
    private final MusicSelector selector;
    private final MusicHistory navigation = new MusicHistory();
    private final List<MusicTrack> journal = new ArrayList<>();
    private final List<QueueEntry> manual = new ArrayList<>();
    private final List<QueueEntry> cycle = new ArrayList<>();
    private final List<MusicTrack> playlist = new ArrayList<>();
    private Mode mode = Mode.FREE_FLOW;
    private boolean shuffle;
    private MusicFrequency musicFrequency;
    private MusicFrequency playlistFrequency;
    private int biomeBiasPercent;
    private StartupMode startupMode;
    private String pausedTrackId;
    private long nextKey = 1;
    private long revision;
    private List<MusicTrack> lastCatalog = List.of();

    public PlayerSession(Random random, SavedState saved) {
        this.random = Objects.requireNonNull(random, "random");
        selector = new MusicSelector(random);
        for (MusicTrack track : saved.playlist()) {
            if (!playlist.contains(track)) playlist.add(track);
        }
        shuffle = saved.shuffle();
        startupMode = saved.startupMode();
        mode = startupMode.initialMode(saved.mode());
        musicFrequency = saved.musicFrequency();
        playlistFrequency = saved.playlistFrequency();
        biomeBiasPercent = saved.biomeBiasPercent();
    }

    public static SavedState defaults() {
        return new SavedState(List.of(), false, Mode.FREE_FLOW);
    }

    public SavedState savedState() {
        return new SavedState(playlist, shuffle, mode, musicFrequency, biomeBiasPercent, startupMode, playlistFrequency);
    }
    public long revision() { return revision; }

    /** Reconciles saved references with the catalog and builds only one upcoming playlist cycle. */
    private void prepare(List<MusicTrack> catalog) {
        if (!lastCatalog.equals(catalog)) {
            cycle.removeIf(entry -> resolve(entry.track(), catalog).isEmpty());
            lastCatalog = List.copyOf(catalog);
            revision++;
        }
        if (mode == Mode.PLAYLIST && cycle.isEmpty()) refill(catalog);
    }

    private void refill(List<MusicTrack> catalog) {
        List<MusicTrack> available = playlist.stream().map(track -> resolve(track, catalog))
                .flatMap(Optional::stream).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (available.isEmpty()) return;
        if (shuffle) {
            Collections.shuffle(available, random);
            // Avoid an immediate repeat at the cycle boundary when alternatives exist.
            Optional<MusicTrack> last = navigation.lastTrack();
            if (available.size() > 1 && last.filter(available.get(0)::equals).isPresent()) {
                Collections.swap(available, 0, 1);
            }
        }
        available.forEach(track -> cycle.add(entry(track, Source.PLAYLIST)));
        revision++;
    }

    private QueueEntry entry(MusicTrack track, Source source) {
        return new QueueEntry(nextKey++, track, source);
    }

    private static Optional<MusicTrack> resolve(MusicTrack track, List<MusicTrack> catalog) {
        return catalog.stream().filter(track::equals).findFirst();
    }

    public Snapshot snapshot(MusicPlatform platform) {
        List<MusicTrack> catalog = platform.eligibleTracks();
        prepare(catalog);
        List<QueueEntry> upcoming = new ArrayList<>();
        List<MusicTrack> visited = navigation.tracks();
        for (int i = navigation.position() + 1; i < visited.size(); i++) {
            final int index = i;
            resolve(visited.get(i), catalog).ifPresent(track ->
                    upcoming.add(new QueueEntry(-index - 1L, track, Source.HISTORY)));
        }
        manual.stream().filter(entry -> resolve(entry.track(), catalog).isPresent()).forEach(upcoming::add);
        if (mode == Mode.PLAYLIST) upcoming.addAll(cycle);
        Optional<MusicTrack> current = platform.currentTrack();
        boolean previous = visited.subList(0, Math.max(0, navigation.position())).stream()
                .anyMatch(track -> resolve(track, catalog).isPresent());
        Mode effectiveMode = mode == Mode.PLAYLIST && cycle.isEmpty() ? Mode.FREE_FLOW : mode;
        return new Snapshot(current, isPaused(current), effectiveMode, shuffle, List.copyOf(upcoming),
                List.copyOf(journal), List.copyOf(playlist),
                mode == Mode.FREE_FLOW || cycle.isEmpty(), previous, musicFrequency, biomeBiasPercent, startupMode,
                playlistFrequency);
    }

    public boolean hasScheduledTracks(MusicPlatform platform) {
        return !snapshot(platform).upcoming().isEmpty();
    }

    /** All automatic transitions follow the effective mode, including queued tracks and cycle boundaries. */
    public int nextAutoplayDelayTicks(MusicPlatform platform) {
        MusicFrequency frequency = snapshot(platform).mode() == Mode.PLAYLIST ? playlistFrequency : musicFrequency;
        return frequency.nextDelayTicks(random);
    }

    public void setMusicFrequency(MusicFrequency frequency) {
        Objects.requireNonNull(frequency, "frequency");
        if (musicFrequency == frequency) return;
        musicFrequency = frequency;
        revision++;
    }

    public void setPlaylistFrequency(MusicFrequency frequency) {
        Objects.requireNonNull(frequency, "frequency");
        if (playlistFrequency == frequency) return;
        playlistFrequency = frequency;
        revision++;
    }

    public void setBiomeBiasPercent(int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Biome bias must be between 0 and 100");
        if (biomeBiasPercent == percent) return;
        biomeBiasPercent = percent;
        revision++;
    }

    public void setStartupMode(StartupMode startupMode) {
        Objects.requireNonNull(startupMode, "startupMode");
        if (this.startupMode == startupMode) return;
        this.startupMode = startupMode;
        revision++;
    }

    public void enqueue(MusicTrack track) {
        manual.add(entry(track, Source.MANUAL));
        revision++;
    }

    public boolean addToPlaylist(MusicTrack track, List<MusicTrack> catalog) {
        if (playlist.contains(track)) return false;
        playlist.add(track);
        if (mode == Mode.PLAYLIST) {
            if (cycle.isEmpty()) refill(catalog);
            else {
                QueueEntry added = entry(track, Source.PLAYLIST);
                cycle.add(shuffle ? random.nextInt(cycle.size() + 1) : cycle.size(), added);
            }
        }
        revision++;
        return true;
    }

    public void removeFromPlaylist(MusicTrack track) {
        playlist.remove(track);
        cycle.removeIf(entry -> entry.track().equals(track));
        if (playlist.isEmpty()) freeFlow();
        revision++;
    }

    public void setShuffle(boolean value) {
        if (shuffle == value) return;
        shuffle = value;
        if (shuffle) Collections.shuffle(cycle, random);
        else cycle.sort(java.util.Comparator.comparingInt(entry -> playlist.indexOf(entry.track())));
        revision++;
    }

    public void freeFlow() {
        mode = Mode.FREE_FLOW;
        cycle.clear();
        revision++;
    }

    public Optional<MusicTrack> activatePlaylist(MusicPlatform platform) {
        mode = Mode.PLAYLIST;
        cycle.clear();
        prepare(platform.eligibleTracks());
        revision++;
        if (cycle.isEmpty()) {
            freeFlow();
            return Optional.empty();
        }
        MusicTrack first = cycle.remove(0).track();
        return start(platform, first, true);
    }

    public Optional<MusicTrack> playNext(MusicPlatform platform) {
        List<MusicTrack> catalog = platform.eligibleTracks();
        prepare(catalog);
        Optional<MusicTrack> next;
        do {
            next = navigation.nextTrack();
        } while (next.isPresent() && resolve(next.get(), catalog).isEmpty());
        if (next.isPresent()) return start(platform, resolve(next.get(), catalog).orElseThrow(), false);
        while (!manual.isEmpty()) {
            QueueEntry queued = manual.remove(0);
            next = resolve(queued.track(), catalog);
            revision++;
            if (next.isPresent()) return start(platform, next.get(), true);
        }
        if (mode == Mode.PLAYLIST && !cycle.isEmpty()) {
            return start(platform, cycle.remove(0).track(), true);
        }
        next = selector.selectNext(catalog, navigation, platform.currentBiomeId(), platform.currentTrack(), biomeBiasPercent);
        return next.flatMap(track -> start(platform, track, true));
    }

    public Optional<MusicTrack> playPrevious(MusicPlatform platform) {
        List<MusicTrack> catalog = platform.eligibleTracks();
        Optional<MusicTrack> previous;
        do {
            previous = navigation.previousTrack();
        } while (previous.isPresent() && resolve(previous.get(), catalog).isEmpty());
        return previous.flatMap(track -> resolve(track, catalog)).flatMap(track -> start(platform, track, false));
    }

    public Optional<MusicTrack> playNow(MusicPlatform platform, MusicTrack track) {
        return resolve(track, platform.eligibleTracks()).flatMap(found -> start(platform, found, true));
    }

    /** Consumes the selected entry and every earlier upcoming entry, preserving everything after it. */
    public Optional<MusicTrack> skipTo(MusicPlatform platform, long key) {
        Snapshot state = snapshot(platform);
        Optional<QueueEntry> selected = state.upcoming().stream().filter(entry -> entry.key() == key).findFirst();
        if (selected.isEmpty()) return Optional.empty();
        QueueEntry target = selected.get();
        if (target.source() == Source.HISTORY) {
            int index = (int) (-key - 1);
            while (navigation.position() < index) navigation.nextTrack();
            return start(platform, target.track(), false);
        }
        if (target.source() == Source.MANUAL) {
            while (!manual.isEmpty()) {
                if (manual.remove(0).key() == key) break;
            }
        } else {
            manual.clear();
            while (!cycle.isEmpty()) {
                if (cycle.remove(0).key() == key) break;
            }
        }
        return start(platform, target.track(), true);
    }

    private Optional<MusicTrack> start(MusicPlatform platform, MusicTrack track, boolean record) {
        platform.stopCurrentTrack();
        platform.startTrack(track);
        pausedTrackId = null;
        if (record) {
            navigation.discardForward();
            navigation.record(track);
        }
        journal.add(track);
        revision++;
        return Optional.of(track);
    }

    public boolean togglePause(MusicPlatform platform) {
        Optional<MusicTrack> current = platform.currentTrack();
        if (current.isEmpty()) return playNext(platform).isPresent();
        boolean wasPaused = isPaused(current);
        if (!(wasPaused ? platform.resumeCurrentTrack() : platform.pauseCurrentTrack())) return false;
        pausedTrackId = wasPaused ? null : current.get().id();
        revision++;
        return true;
    }

    public boolean isPaused(Optional<MusicTrack> current) {
        return current.map(MusicTrack::id).filter(id -> id.equals(pausedTrackId)).isPresent();
    }
}
