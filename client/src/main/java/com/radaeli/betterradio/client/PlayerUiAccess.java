package com.radaeli.betterradio.client;

import com.radaeli.betterradio.music.MusicFrequency;
import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.PlayerSession;
import com.radaeli.betterradio.music.StartupMode;
import com.radaeli.ruilib.ui.UiTheme;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.OptionalDouble;

/** Loader boundary consumed by the shared player screen. */
public interface PlayerUiAccess {
    record Progress(double positionSeconds, OptionalDouble durationSeconds) { }

    boolean inWorld();
    UiTheme theme();
    boolean handleScreenKey(int keyCode, int scanCode);
    PlayerSession.Snapshot snapshot();
    List<MusicTrack> catalog();
    long revision();
    String idleStateKey();
    Component trackName(MusicTrack track);
    Component toastMessage(Component message);
    Progress playbackProgress();
    void playPrevious();
    void playNext();
    void togglePause();
    void freeFlow();
    void activatePlaylist();
    void setShuffle(boolean shuffle);
    void skipTo(long key);
    void setMusicFrequency(MusicFrequency frequency);
    void setPlaylistFrequency(MusicFrequency frequency);
    void setBiomeBiasPercent(int percent);
    void setStartupMode(StartupMode startupMode);
    void enqueue(MusicTrack track);
    void playNow(MusicTrack track);
    void addToPlaylist(MusicTrack track);
    void removeFromPlaylist(MusicTrack track);
}
