package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MusicControllerTest {
    private static final MusicTrack A = new MusicTrack("minecraft:music/game/calm");
    private static final MusicTrack B = new MusicTrack("minecraft:music/end/dragon");
    private static final MusicTrack C = new MusicTrack("example:music/forest/dusk");

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
    public void currentlyPlayingTrackIsRecordedBeforeNextSelection() {
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

    @Test
    public void previousAndNextNavigateTheRecordedPlaybackQueue() {
        MusicHistory history = new MusicHistory();
        history.record(A);
        history.record(B);
        TestPlatform platform = new TestPlatform(List.of(A, B), Optional.of(B));
        MusicController controller = controller(history);

        assertEquals(A, controller.playPrevious(platform).orElseThrow());
        assertEquals(List.of(A), platform.startedTracks);
        assertEquals(0, history.position());
        assertEquals(B, controller.playNext(platform).orElseThrow());
        assertEquals(List.of(A, B), platform.startedTracks);
        assertEquals(List.of(A, B), history.tracks());
        assertEquals(1, history.position());
    }

    @Test
    public void repeatedPreviousAndNextPlayTheExactFilesAroundTheQueueCursor() {
        MusicHistory history = new MusicHistory();
        history.record(A);
        history.record(B);
        history.record(C);
        TestPlatform platform = new TestPlatform(List.of(A, B, C), Optional.of(C));
        MusicController controller = controller(history);

        assertEquals(B, controller.playPrevious(platform).orElseThrow());
        assertEquals(A, controller.playPrevious(platform).orElseThrow());
        assertEquals(B, controller.playNext(platform).orElseThrow());
        assertEquals(C, controller.playNext(platform).orElseThrow());

        assertEquals(List.of(B, A, B, C), platform.startedTracks);
        assertEquals(List.of(A, B, C), history.tracks());
        assertEquals(2, history.position());
    }

    @Test
    public void selectingANewTrackAfterGoingBackBranchesTheQueue() {
        MusicHistory history = new MusicHistory();
        history.record(A);
        history.record(B);
        history.previousTrack();

        history.record(new MusicTrack("minecraft:music.c"));

        assertEquals(List.of(A, new MusicTrack("minecraft:music.c")), history.tracks());
        assertEquals(1, history.position());
    }

    @Test
    public void pauseHotkeyPausesAndResumesTheSameTrack() {
        TestPlatform platform = new TestPlatform(List.of(A, B), Optional.of(A));
        MusicHistory history = new MusicHistory();
        MusicController controller = controller(history);

        assertTrue(controller.togglePause(platform));
        assertTrue(controller.isPaused(Optional.of(A)));
        assertEquals(A, history.lastTrack().orElseThrow());
        assertTrue(controller.togglePause(platform));
        assertTrue(!controller.isPaused(Optional.of(A)));
        assertEquals(1, platform.pauseCalls);
        assertEquals(1, platform.resumeCalls);
    }

    @Test
    public void playerSessionKeepsExactFilesAcrossNextPausePreviousAndResume() {
        TestPlatform platform = new TestPlatform(List.of(A, B, C), Optional.of(A));
        MusicHistory history = new MusicHistory();
        MusicSelector selector = new MusicSelector(new Random(0) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        MusicController controller = new MusicController(selector, history);

        assertEquals(B, controller.playNext(platform).orElseThrow());
        assertTrue(controller.togglePause(platform));
        assertTrue(controller.isPaused(Optional.of(B)));
        assertTrue(controller.togglePause(platform));
        assertTrue(!controller.isPaused(Optional.of(B)));
        assertEquals(A, controller.playNext(platform).orElseThrow());
        assertEquals(B, controller.playPrevious(platform).orElseThrow());
        assertEquals(A, controller.playNext(platform).orElseThrow());

        assertEquals(List.of(B, A, B, A), platform.startedTracks);
        assertEquals(1, platform.pauseCalls);
        assertEquals(1, platform.resumeCalls);
        assertEquals(List.of(A, B, A), history.tracks());
        assertEquals(2, history.position());
    }

    @Test
    public void pauseHotkeyDoesNothingWithoutAnActiveTrack() {
        TestPlatform platform = new TestPlatform(List.of(A), Optional.empty());

        assertTrue(!controller(new MusicHistory()).togglePause(platform));
        assertEquals(0, platform.pauseCalls);
        assertEquals(0, platform.resumeCalls);
    }

    @Test
    public void modAndDatapackNamespacedTracksRemainEligible() {
        MusicTrack modTrack = new MusicTrack("examplemod:music/custom");
        MusicTrack datapackTrack = new MusicTrack("custom_pack:music/forest");

        assertTrue(List.of(modTrack, datapackTrack)
                .contains(controller(new MusicHistory()).next(List.of(modTrack, datapackTrack)).orElseThrow()));
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
        private Optional<MusicTrack> current;
        private int stopCalls;
        private int pauseCalls;
        private int resumeCalls;
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
            current = Optional.empty();
        }

        @Override
        public void startTrack(MusicTrack track) {
            startedTracks.add(track);
            current = Optional.of(track);
        }

        @Override
        public boolean pauseCurrentTrack() {
            pauseCalls++;
            return true;
        }

        @Override
        public boolean resumeCurrentTrack() {
            resumeCalls++;
            return true;
        }
    }
}
