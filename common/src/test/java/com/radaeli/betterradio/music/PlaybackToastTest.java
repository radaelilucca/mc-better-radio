package com.radaeli.betterradio.music;

import org.junit.Test;

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
    public void observedStateChangesDuringAnActionDoNotCauseASecondToastAfterward() {
        PlaybackToast toast = new PlaybackToast();
        toast.showAction(PlaybackToast.State.PREVIOUS, PlaybackToast.State.PLAYING,
                "track-a", false, 0L);

        assertFalse(toast.update(PlaybackToast.State.PAUSED, "track-a", true, 1000L));
        assertFalse(toast.update(PlaybackToast.State.PAUSED, "track-a", true, 2000L));
        assertFalse(toast.isVisible(2000L));
    }

    @Test
    public void mutedStateOverridesActionLabelsWhenMusicVolumeIsZero() {
        PlaybackToast toast = new PlaybackToast();
        toast.showAction(PlaybackToast.State.NEXT, PlaybackToast.State.MUTED,
                "track-b", false, 0L);

        assertEquals(PlaybackToast.State.MUTED, toast.state());
    }
}
