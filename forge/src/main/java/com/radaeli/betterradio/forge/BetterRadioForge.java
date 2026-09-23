package com.radaeli.betterradio.forge;

import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.forge.MusicConfigForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Forge 1.20.1 entrypoint. Feature code will be added after the scaffold spike. */
@Mod(BetterRadio.MOD_ID)
public final class BetterRadioForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRadio.MOD_ID);

    public BetterRadioForge() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MusicConfigForge.CONFIG);
        LOGGER.info("{} common bootstrap initialized on Forge", BetterRadio.MOD_ID);
    }
}
