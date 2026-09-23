package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MusicChannelPauseBridgeTest {
    @Test
    public void pausesAndResumesOnlyTheMusicManagerChannel() {
        FakeMusic music = new FakeMusic();
        FakeChannel channel = new FakeChannel();
        FakeChannel jukebox = new FakeChannel();
        FakeSoundEngine engine = new FakeSoundEngine();
        engine.instanceToChannel.put(music.currentMusic, new FakeHandle(channel));
        engine.instanceToChannel.put(new Object(), new FakeHandle(jukebox));
        FakeSoundManager soundManager = new FakeSoundManager(engine);

        assertTrue(MusicChannelPauseBridge.setPaused(music, soundManager, true));
        assertTrue(channel.paused);
        assertFalse(jukebox.paused);
        assertTrue(MusicChannelPauseBridge.setPaused(music, soundManager, false));
        assertFalse(channel.paused);
    }

    @Test
    public void missingAudioChannelFailsSafely() {
        FakeSoundManager soundManager = new FakeSoundManager(new FakeSoundEngine());
        assertFalse(MusicChannelPauseBridge.setPaused(new FakeMusic(), soundManager, true));
    }

    private static final class FakeMusic { private final Object currentMusic = new Object(); }
    private static final class FakeSoundManager { private final FakeSoundEngine soundEngine; private FakeSoundManager(FakeSoundEngine e) { soundEngine = e; } }
    private static final class FakeSoundEngine { private final Map<Object, Object> instanceToChannel = new IdentityHashMap<>(); }
    private static final class FakeHandle {
        private final FakeChannel channel;
        private FakeHandle(FakeChannel channel) { this.channel = channel; }
        private void execute(Consumer<FakeChannel> action) { action.accept(channel); }
    }
    private static final class FakeChannel {
        private boolean paused;
        private void pause() { paused = true; }
        private void unpause() { paused = false; }
    }
}
