package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MusicHistoryTest {
    private static final MusicTrack A = new MusicTrack("minecraft:music.a");
    private static final MusicTrack B = new MusicTrack("minecraft:music.b");

    @Test
    public void tracksExposeTheCompleteQueueAndCursorNavigatesBothWays() {
        MusicHistory history = new MusicHistory();
        history.record(A);
        history.record(B);

        assertEquals(List.of(A, B), history.tracks());
        assertEquals(1, history.position());
        assertEquals(A, history.previousTrack().orElseThrow());
        assertEquals(A, history.lastTrack().orElseThrow());
        assertEquals(B, history.nextTrack().orElseThrow());
        assertTrue(history.nextTrack().isEmpty());
    }

    @Test
    public void recordingTheCurrentTrackDoesNotCreateDuplicateEntries() {
        MusicHistory history = new MusicHistory();
        history.record(A);
        history.record(A);

        assertEquals(List.of(A), history.tracks());
        assertEquals(0, history.position());
        assertFalse(history.previousTrack().isPresent());
    }
}
