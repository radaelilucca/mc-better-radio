package com.radaeli.betterradio.music;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

/** Pauses the SoundEngine channel belonging to MusicManager's current sound instance. */
public final class MusicChannelPauseBridge {
    private MusicChannelPauseBridge() {}

    public static boolean setPaused(Object musicManager, Object soundManager, boolean paused) {
        try {
            Object soundEngine = field(soundManager.getClass(), "soundEngine").get(soundManager);
            Object soundInstance = field(musicManager.getClass(), "currentMusic").get(musicManager);
            if (soundEngine == null || soundInstance == null) {
                return false;
            }
            Field channelsField = field(soundEngine.getClass(), "instanceToChannel");
            Object raw = channelsField.get(soundEngine);
            if (!(raw instanceof Map<?, ?> channels)) {
                return false;
            }
            Object handle = channels.get(soundInstance);
            if (handle == null) {
                return false;
            }
            Object channel = field(handle.getClass(), "channel").get(handle);
            if (channel == null) {
                return false;
            }
            Method channelAction = method(channel.getClass(), paused ? "pause" : "unpause");
            Method execute = method(handle.getClass(), "execute", Consumer.class);
            execute.invoke(handle, (Consumer<Object>) activeChannel -> {
                try {
                    channelAction.invoke(activeChannel);
                } catch (ReflectiveOperationException exception) {
                    throw new PauseBridgeException(exception);
                }
            });
            return true;
        } catch (ReflectiveOperationException | PauseBridgeException exception) {
            return false;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Search inherited implementation classes too.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Search inherited implementation classes too.
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static final class PauseBridgeException extends RuntimeException {
        private PauseBridgeException(ReflectiveOperationException cause) { super(cause); }
    }
}
