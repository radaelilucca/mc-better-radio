package com.radaeli.betterradio.neoforge.client;

/** Only Better Radio's own channels opt in to streaming offset bookkeeping. */
public interface RadioAudioChannel {
    void betterRadio$trackProgress(AudioPlaybackProgress progress);
}
