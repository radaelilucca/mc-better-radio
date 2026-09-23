package com.radaeli.betterradio.music;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaybackToastTest {
    @Test
    public void toastExpiresAfterTwoSecondsWithoutBeingRefreshedByRendering() {
        PlaybackToast toast = new PlaybackToast();
        assertTrue(toast.update(PlaybackToast.State.PLAYING, "track-a", false, 1000L));
        assertFalse(toast.update(PlaybackToast.State.PLAYING, "track-a", false, 2500L));
        assertTrue(toast.isVisible(2999L));
        assertFalse(toast.isVisible(3000L));
    }

    @Test
    public void trackOrPlaybackStateChangesStartANewToast() {
        PlaybackToast toast = new PlaybackToast();
        assertTrue(toast.update(PlaybackToast.State.PLAYING, "track-a", false, 0L));
        assertTrue(toast.update(PlaybackToast.State.PLAYING, "track-b", false, 2500L));
        assertTrue(toast.isVisible(4000L));
        assertTrue(toast.update(PlaybackToast.State.PAUSED, "track-b", true, 4000L));
        assertTrue(toast.isVisible(5999L));
        assertFalse(toast.isVisible(6000L));
    }

    @Test
    public void clearingWithoutMusicAllowsTheNextTrackToShow() {
        PlaybackToast toast = new PlaybackToast();
        toast.update(PlaybackToast.State.PLAYING, "track-a", false, 0L);
        toast.clear();

        assertTrue(toast.update(PlaybackToast.State.PLAYING, "track-a", false, 5000L));
    }

    @Test
    public void pauseTransitionStillRenewsMutedToast() {
        PlaybackToast toast = new PlaybackToast();
        toast.update(PlaybackToast.State.MUTED, "track-a", false, 0L);
        assertTrue(toast.update(PlaybackToast.State.MUTED, "track-a", true, 2500L));
    }

    @Test
    public void toastFadesLinearlyDuringItsFinalHalfSecond() {
        PlaybackToast toast = new PlaybackToast();
        toast.update(PlaybackToast.State.PLAYING, "track-a", false, 1000L);

        assertEquals(255, toast.alpha(2499L));
        assertEquals(255, toast.alpha(2500L));
        assertEquals(127, toast.alpha(2750L));
        assertEquals(0, toast.alpha(2999L));
        assertEquals(0, toast.alpha(3000L));
    }

    @Test
    public void actionMessageSurvivesPassiveRenderUpdatesAndDoesNotRetriggerAfterExpiry() {
        PlaybackToast toast = new PlaybackToast();
        toast.showAction(PlaybackToast.State.NEXT, PlaybackToast.State.PLAYING,
                "track-b", false, 0L);

        assertEquals(PlaybackToast.State.NEXT, toast.state());
        assertFalse(toast.update(PlaybackToast.State.PLAYING, "track-b", false, 100L));
        assertEquals(PlaybackToast.State.NEXT, toast.state());
        assertTrue(toast.isVisible(1999L));
        assertFalse(toast.update(PlaybackToast.State.PLAYING, "track-b", false, 2000L));
        assertFalse(toast.isVisible(2000L));
    }

    @Test
    public void playbackStateChangeDuringAnActionShowsTheNewStateAndStartsItsOwnTimeout() {
        PlaybackToast toast = new PlaybackToast();
        toast.showAction(PlaybackToast.State.PREVIOUS, PlaybackToast.State.PLAYING,
                "track-a", false, 0L);

        assertTrue(toast.update(PlaybackToast.State.PAUSED, "track-a", true, 1000L));
        assertEquals(PlaybackToast.State.PAUSED, toast.state());
        assertFalse(toast.update(PlaybackToast.State.PAUSED, "track-a", true, 2000L));
        assertTrue(toast.isVisible(2999L));
        assertFalse(toast.isVisible(3000L));
    }

    @Test
    public void mutedStateOverridesActionLabelsWhenMusicVolumeIsZero() {
        PlaybackToast toast = new PlaybackToast();
        toast.showAction(PlaybackToast.State.NEXT, PlaybackToast.State.MUTED,
                "track-b", false, 0L);

        assertEquals(PlaybackToast.State.MUTED, toast.state());
    }

    @Test
    public void stateLabelsUseTheSharedLocalizedTranslationKeys() throws IOException {
        assertEquals("better_radio.status.previous", PlaybackToast.State.PREVIOUS.translationKey());
        assertEquals("better_radio.status.next", PlaybackToast.State.NEXT.translationKey());
        assertEquals("better_radio.status.playing", PlaybackToast.State.PLAYING.translationKey());
        assertEquals("better_radio.status.paused", PlaybackToast.State.PAUSED.translationKey());
        assertEquals("better_radio.status.muted", PlaybackToast.State.MUTED.translationKey());

        try (var input = getClass().getClassLoader().getResourceAsStream("assets/better_radio/lang/en_us.json")) {
            assertTrue("English status translations are packaged", input != null);
            String translations = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(translations.contains("\"better_radio.status.previous\": \"Previous\""));
            assertTrue(translations.contains("\"better_radio.status.next\": \"Next\""));
            assertTrue(translations.contains("\"better_radio.status.playing\": \"Playing\""));
            assertTrue(translations.contains("\"better_radio.status.paused\": \"Paused\""));
            assertTrue(translations.contains("\"better_radio.status.muted\": \"Muted\""));
        }
    }
}
