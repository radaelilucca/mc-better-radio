package com.radaeli.betterradio.music;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MusicChannelPauseBridgeTest {
    @Test
    public void pausesAndResumesOnlyTheRequestedSoundInstanceChannel() {
        Object ownedSound = new Object();
        Object jukeboxSound = new Object();
        SoundEngineFixture engine = new SoundEngineFixture();
        ChannelFixture ownedChannel = new ChannelFixture();
        ChannelFixture jukeboxChannel = new ChannelFixture();
        engine.instanceToChannel.put(ownedSound, new ChannelHandleFixture(ownedChannel));
        engine.instanceToChannel.put(jukeboxSound, new ChannelHandleFixture(jukeboxChannel));
        SoundManagerFixture soundManager = new SoundManagerFixture(engine);

        assertTrue(MusicChannelPauseBridge.setSoundInstancePaused(soundManager, ownedSound, true));
        assertTrue(ownedChannel.paused);
        assertFalse(jukeboxChannel.paused);

        assertTrue(MusicChannelPauseBridge.setSoundInstancePaused(soundManager, ownedSound, false));
        assertFalse(ownedChannel.paused);
        assertFalse(jukeboxChannel.paused);
    }

    @Test
    public void returnsFalseWhenTheInstanceHasNoActiveChannel() {
        assertFalse(MusicChannelPauseBridge.setSoundInstancePaused(
                new SoundManagerFixture(new SoundEngineFixture()), new Object(), true));
    }

    private static final class SoundManagerFixture {
        private final SoundEngineFixture soundEngine;

        private SoundManagerFixture(SoundEngineFixture soundEngine) {
            this.soundEngine = soundEngine;
        }
    }

    private static final class SoundEngineFixture {
        private final Map<Object, ChannelHandleFixture> instanceToChannel = new HashMap<>();
    }

    private static final class ChannelHandleFixture {
        private final ChannelFixture channel;

        private ChannelHandleFixture(ChannelFixture channel) {
            this.channel = channel;
        }

        private void execute(Consumer<ChannelFixture> action) {
            action.accept(channel);
        }
    }

    private static final class ChannelFixture {
        private boolean paused;

        private void pause() {
            paused = true;
        }

        private void unpause() {
            paused = false;
        }
    }
}
