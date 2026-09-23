package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MusicControllerTest {
    private static final MusicTrack A = new MusicTrack("minecraft:music.a", "Track A");
    private static final MusicTrack B = new MusicTrack("minecraft:music.b", "Track B");

    @Test
    public void emptyListReturnsEmptyAndDoesNotChangeHistory() {
        MusicHistory history = new MusicHistory();
        MusicController controller = controller(history);

        assertTrue(controller.next(List.of()).isEmpty());
        assertTrue(history.lastTrack().isEmpty());
    }

    @Test
    public void singleTrackIsSelectedEvenWhenItWasLastTrack() {
        MusicHistory history = new MusicHistory();
        history.record(A);

        assertEquals(A, controller(history).next(List.of(A)).orElseThrow());
        assertEquals(A, history.lastTrack().orElseThrow());
    }

    @Test
    public void lastTrackIsExcludedWhenThereAreAlternatives() {
        MusicHistory history = new MusicHistory();
        history.record(A);

        assertEquals(B, controller(history).next(List.of(A, B)).orElseThrow());
        assertEquals(B, history.lastTrack().orElseThrow());
    }

    @Test
    public void currentlyPlayingVanillaTrackIsExcludedFromNextSelection() {
        MusicHistory history = new MusicHistory();

        assertEquals(B, controller(history).next(List.of(A, B), java.util.Optional.of(A)).orElseThrow());
        assertEquals(B, history.lastTrack().orElseThrow());
    }

    @Test
    public void sameSeedProducesSameSelectionSequence() {
        assertEquals(selectionSequence(1234), selectionSequence(1234));
    }

    @Test
    public void playNextStopsCurrentTrackAndStartsAnAlternativeOnOneCall() {
        TestPlatform platform = new TestPlatform(List.of(A, B), Optional.of(A));

        assertEquals(B, controller(new MusicHistory()).playNext(platform).orElseThrow());

        assertEquals(1, platform.stopCalls);
        assertEquals(List.of(B), platform.startedTracks);
    }

    @Test
    public void playNextWithNoEligibleTracksDoesNotStopOrStartAudio() {
        TestPlatform platform = new TestPlatform(List.of(), Optional.empty());

        assertTrue(controller(new MusicHistory()).playNext(platform).isEmpty());

        assertEquals(0, platform.stopCalls);
        assertTrue(platform.startedTracks.isEmpty());
    }

    private static List<MusicTrack> selectionSequence(long seed) {
        MusicHistory history = new MusicHistory();
        MusicController controller = new MusicController(new MusicSelector(new Random(seed)), history);
        return java.util.stream.IntStream.range(0, 8)
                .mapToObj(ignored -> controller.next(List.of(A, B)).orElseThrow())
                .toList();
    }

    private static MusicController controller(MusicHistory history) {
        return new MusicController(new MusicSelector(new Random(1234)), history);
    }

    private static final class TestPlatform implements MusicPlatform {
        private final List<MusicTrack> tracks;
        private final Optional<MusicTrack> current;
        private int stopCalls;
        private final java.util.ArrayList<MusicTrack> startedTracks = new java.util.ArrayList<>();

        private TestPlatform(List<MusicTrack> tracks, Optional<MusicTrack> current) {
            this.tracks = tracks;
            this.current = current;
        }

        @Override
        public List<MusicTrack> eligibleTracks() {
            return tracks;
        }

        @Override
        public Optional<MusicTrack> currentTrack() {
            return current;
        }

        @Override
        public void stopCurrentTrack() {
            stopCalls++;
        }

        @Override
        public void startTrack(MusicTrack track) {
            startedTracks.add(track);
        }
    }
}
