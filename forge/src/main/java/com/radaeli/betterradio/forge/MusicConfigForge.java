package com.radaeli.betterradio.forge;

import net.minecraftforge.common.ForgeConfigSpec;

/** Loader config definition; intentionally contains no client-only Minecraft references. */
public final class MusicConfigForge {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue SHOW_NOW_PLAYING = BUILDER
            .comment("Show Better Radio's temporary playback toast above the experience bar")
            .define("showNowPlaying", true);
    public static final ForgeConfigSpec CONFIG = BUILDER.build();

    private MusicConfigForge() {}
}
