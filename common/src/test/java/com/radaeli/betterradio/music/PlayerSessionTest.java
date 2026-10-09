package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;

public class PlayerSessionTest {
    private static final MusicTrack A = new MusicTrack("test:a");
    private static final MusicTrack B = new MusicTrack("test:b");
    private static final MusicTrack C = new MusicTrack("test:c");
    private static final MusicTrack D = new MusicTrack("test:d");

    private static PlayerSession session() {
        return new PlayerSession(new Random(42), PlayerSession.defaults());
    }

    private static PlayerSession playlist(boolean shuffle, MusicTrack... tracks) {
        return new PlayerSession(new Random(42), new PlayerSession.SavedState(
                List.of(tracks), shuffle, PlayerSession.Mode.PLAYLIST));
    }

    @Test
    public void activatingStartsThePlaylistImmediatelyButPreservesManualQueue() {
        PlayerSession player = playlist(false, A, B);
        Platform audio = new Platform();
        audio.current = D;
        player.enqueue(C);
        assertEquals(A, player.activatePlaylist(audio).orElseThrow());
        assertEquals(List.of(C, B), upcoming(player, audio));
        assertEquals(C, player.playNext(audio).orElseThrow());
        assertEquals(B, player.playNext(audio).orElseThrow());
        assertEquals(A, player.playNext(audio).orElseThrow());
    }

    @Test
    public void previousAndNextReplayHistoryBeforeReturningToManualQueue() {
        PlayerSession player = session();
        Platform audio = new Platform();
        player.playNow(audio, A);
        player.playNow(audio, B);
        player.enqueue(C);
        assertEquals(A, player.playPrevious(audio).orElseThrow());
        assertEquals(List.of(B, C), upcoming(player, audio));
        assertEquals(B, player.playNext(audio).orElseThrow());
        assertEquals(C, player.playNext(audio).orElseThrow());
    }

    @Test
    public void branchingPlaybackPreservesFullJournalAndManualQueue() {
        PlayerSession player = playlist(false, A, B);
        Platform audio = new Platform();
        player.activatePlaylist(audio);
        player.playNext(audio);
        player.playPrevious(audio);
        player.enqueue(C);
        player.playNow(audio, D);
        assertEquals(List.of(A, B, A, D), player.snapshot(audio).history());
        assertEquals(C, player.playNext(audio).orElseThrow());
        assertEquals(PlayerSession.Mode.PLAYLIST, player.snapshot(audio).mode());
    }

    @Test
    public void skipToManualDiscardsForwardHistoryAndOnlyEarlierManualEntries() {
        PlayerSession player = session();
        Platform audio = new Platform();
        player.playNow(audio, A);
        player.playNow(audio, B);
        player.playPrevious(audio);
        player.enqueue(C);
        player.enqueue(A);
        player.enqueue(D);
        long key = player.snapshot(audio).upcoming().get(2).key();
        assertEquals(A, player.skipTo(audio, key).orElseThrow());
        assertEquals(List.of(D), upcoming(player, audio));
    }

    @Test
    public void skipToPlaylistDiscardsManualQueueAndEarlierPlaylistEntries() {
        PlayerSession player = playlist(false, A, B, C);
        Platform audio = new Platform();
        player.enqueue(D);
        long key = player.snapshot(audio).upcoming().get(2).key();
        assertEquals(B, player.skipTo(audio, key).orElseThrow());
        assertEquals(List.of(C), upcoming(player, audio));
    }

    @Test
    public void skippingToForwardHistoryPreservesEverythingAfterTheTarget() {
        PlayerSession player = session();
        Platform audio = new Platform();
        player.playNow(audio, A);
        player.playNow(audio, B);
        player.playNow(audio, C);
        player.playPrevious(audio);
        player.playPrevious(audio);
        player.enqueue(D);
        long key = player.snapshot(audio).upcoming().get(0).key();
        assertEquals(B, player.skipTo(audio, key).orElseThrow());
        assertEquals(List.of(C, D), upcoming(player, audio));
    }

    @Test
    public void shuffleVisitsEveryPlaylistTrackOncePerCycle() {
        PlayerSession player = playlist(true, A, B, C, D);
        Platform audio = new Platform();
        for (int cycle = 0; cycle < 3; cycle++) {
            List<MusicTrack> played = new ArrayList<>();
            for (int i = 0; i < 4; i++) played.add(player.playNext(audio).orElseThrow());
            assertEquals(new HashSet<>(List.of(A, B, C, D)), new HashSet<>(played));
            assertEquals(4, played.size());
        }
        for (int i = 4; i < audio.started.size(); i += 4) {
            assertNotEquals(audio.started.get(i - 1), audio.started.get(i));
        }
    }

    @Test
    public void shuffleChangesOnlyRemainingPlaylistTracks() {
        PlayerSession player = playlist(false, A, B, C);
        Platform audio = new Platform();
        player.activatePlaylist(audio);
        player.enqueue(D);
        player.setShuffle(true);
        assertEquals(A, audio.current);
        assertEquals(D, upcoming(player, audio).get(0));
        assertEquals(new HashSet<>(List.of(B, C)), new HashSet<>(upcoming(player, audio).subList(1, 3)));
        player.setShuffle(false);
        assertEquals(List.of(D, B, C), upcoming(player, audio));
    }

    @Test
    public void emptyOrUnavailablePlaylistFallsBackWithoutLosingSavedReferences() {
        PlayerSession player = playlist(false, D);
        Platform audio = new Platform();
        audio.catalog = List.of(A, B);
        assertEquals(PlayerSession.Mode.FREE_FLOW, player.snapshot(audio).mode());
        assertTrue(player.snapshot(audio).randomNext());
        assertEquals(List.of(D), player.savedState().playlist());
        assertTrue(player.playNext(audio).isPresent());
        audio.catalog = List.of(A, B, D);
        assertEquals(List.of(D), upcoming(player, audio));
        assertEquals(PlayerSession.Mode.PLAYLIST, player.snapshot(audio).mode());
    }

    @Test
    public void removingCurrentlyPlayingTrackDoesNotStopAudio() {
        PlayerSession player = playlist(false, A, B);
        Platform audio = new Platform();
        player.activatePlaylist(audio);
        player.removeFromPlaylist(A);
        assertEquals(A, audio.current);
        assertEquals(List.of(B), upcoming(player, audio));
        player.removeFromPlaylist(B);
        assertEquals(PlayerSession.Mode.FREE_FLOW, player.snapshot(audio).mode());
    }

    @Test
    public void playlistDeduplicatesButManualQueueAllowsRepeatedTracks() {
        PlayerSession player = session();
        Platform audio = new Platform();
        assertTrue(player.addToPlaylist(A, audio.catalog));
        assertFalse(player.addToPlaylist(A, audio.catalog));
        player.enqueue(A);
        player.enqueue(A);
        player.playNext(audio);
        player.playNext(audio);
        assertEquals(List.of(A, A), player.snapshot(audio).history());
    }

    @Test
    public void playPauseStartsWhenIdleAndResumeDoesNotAddHistory() {
        PlayerSession player = session();
        Platform audio = new Platform();
        player.enqueue(A);
        assertTrue(player.togglePause(audio));
        assertEquals(A, audio.current);
        assertTrue(player.togglePause(audio));
        assertTrue(player.snapshot(audio).paused());
        assertTrue(player.togglePause(audio));
        assertFalse(player.snapshot(audio).paused());
        assertEquals(List.of(A), player.snapshot(audio).history());
    }

    @Test
    public void reloadKeepsPersonalPreferencesButResetsSessionPlayback() {
        PlayerSession player = playlist(true, A, B);
        Platform audio = new Platform();
        player.playNext(audio);
        player.enqueue(C);
        PlayerSession restored = new PlayerSession(new Random(42), player.savedState());
        audio.current = null;
        assertTrue(restored.snapshot(audio).history().isEmpty());
        assertEquals(2, restored.snapshot(audio).upcoming().size());
        assertTrue(restored.snapshot(audio).shuffle());
        assertEquals(PlayerSession.Mode.PLAYLIST, restored.snapshot(audio).mode());
    }

    @Test
    public void freeFlowKeepsCurrentTrackAndManualQueue() {
        PlayerSession player = playlist(false, A, B);
        Platform audio = new Platform();
        player.activatePlaylist(audio);
        player.enqueue(C);
        player.freeFlow();
        assertEquals(A, audio.current);
        assertEquals(List.of(C), upcoming(player, audio));
        assertTrue(player.snapshot(audio).randomNext());
        assertTrue(player.hasScheduledTracks(audio));
        player.playNext(audio);
        assertFalse(player.hasScheduledTracks(audio));
    }

    @Test
    public void unavailableQueuedTrackAndStaleQueueKeyAreSafe() {
        PlayerSession player = session();
        Platform audio = new Platform();
        player.enqueue(new MusicTrack("test:missing"));
        player.enqueue(A);
        assertEquals(List.of(A), upcoming(player, audio));
        assertTrue(player.skipTo(audio, Long.MAX_VALUE).isEmpty());
        assertEquals(A, player.playNext(audio).orElseThrow());
    }

    private static List<MusicTrack> upcoming(PlayerSession player, Platform platform) {
        return player.snapshot(platform).upcoming().stream().map(PlayerSession.QueueEntry::track).toList();
    }

    private static final class Platform implements MusicPlatform {
        private List<MusicTrack> catalog = List.of(A, B, C, D);
        private MusicTrack current;
        private boolean paused;
        private final List<MusicTrack> started = new ArrayList<>();
        public List<MusicTrack> eligibleTracks() { return catalog; }
        public Optional<MusicTrack> currentTrack() { return Optional.ofNullable(current); }
        public void stopCurrentTrack() { current = null; paused = false; }
        public void startTrack(MusicTrack track) { current = track; started.add(track); }
        public boolean pauseCurrentTrack() { paused = true; return true; }
        public boolean resumeCurrentTrack() { paused = false; return true; }
    }
}
