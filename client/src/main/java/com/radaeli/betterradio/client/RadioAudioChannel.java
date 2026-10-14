package com.radaeli.betterradio.client;

/** Only Better Radio's own channels opt in to streaming offset bookkeeping. */
public interface RadioAudioChannel {
    void betterRadio$trackProgress(AudioPlaybackProgress progress);
}
