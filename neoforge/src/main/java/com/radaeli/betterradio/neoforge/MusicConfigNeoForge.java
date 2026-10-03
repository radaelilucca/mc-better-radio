package com.radaeli.betterradio.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Loader config definition; intentionally contains no client-only Minecraft references. */
public final class MusicConfigNeoForge {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SHOW_NOW_PLAYING = BUILDER
            .comment("Show Better Radio playback notifications using R-UI Lib toasts")
            .define("showNowPlaying", true);
    public static final ModConfigSpec CONFIG = BUILDER.build();

    private MusicConfigNeoForge() {}
}
