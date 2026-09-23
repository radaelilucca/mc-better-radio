package com.radaeli.betterradio.music;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Pauses one SoundEngine channel while leaving every other sound untouched. */
public final class MusicChannelPauseBridge {
    private MusicChannelPauseBridge() {}

    public static boolean setPaused(Object musicManager, Object soundManager, boolean paused) {
        try {
            Object soundInstance = field(musicManager.getClass(), "currentMusic").get(musicManager);
            return setSoundInstancePaused(soundManager, soundInstance, paused);
        } catch (ReflectiveOperationException | PauseBridgeException exception) {
            return false;
        }
    }

    /** Pauses/resumes the channel associated with an explicitly retained SoundInstance. */
    public static boolean setSoundInstancePaused(Object soundManager, Object soundInstance, boolean paused) {
        try {
            Object soundEngine = field(soundManager.getClass(), "soundEngine").get(soundManager);
            if (soundEngine == null || soundInstance == null) {
                return false;
            }
            Object raw = field(soundEngine.getClass(), "instanceToChannel").get(soundEngine);
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

    /** Checks whether an instance is the vanilla manager's own current background sound. */
    public static boolean isCurrentMusicInstance(Object musicManager, Object soundInstance) {
        try {
            return soundInstance != null
                    && field(musicManager.getClass(), "currentMusic").get(musicManager) == soundInstance;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    /** Returns the resolved audio asset for MusicManager's current event, when available. */
    public static Optional<String> currentSoundPath(Object musicManager) {
        try {
            Object soundInstance = field(musicManager.getClass(), "currentMusic").get(musicManager);
            if (soundInstance == null) {
                return Optional.empty();
            }
            Object sound = method(soundInstance.getClass(), "getSound").invoke(soundInstance);
            if (sound == null) {
                return Optional.empty();
            }
            Object path = method(sound.getClass(), "getPath").invoke(sound);
            return Optional.ofNullable(path).map(Object::toString);
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
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
