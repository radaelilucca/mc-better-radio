package com.radaeli.betterradio.neoforge;

import com.radaeli.betterradio.BetterRadio;
import com.radaeli.betterradio.neoforge.client.MusicClientNeoForge;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NeoForge 1.21.1 entrypoint. Feature code will be added after the scaffold spike. */
@Mod(BetterRadio.MOD_ID)
public final class BetterRadioNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRadio.MOD_ID);

    public BetterRadioNeoForge(IEventBus modBus) {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, MusicClientNeoForge.CONFIG);
        LOGGER.info("{} common bootstrap initialized on NeoForge", BetterRadio.MOD_ID);
    }
}
