package com.radaeli.betterradio.neoforge.client.mixin;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Exposes the resolved, weighted entries of the active sound definition. */
@Mixin(value = WeighedSoundEvents.class, remap = false)
public interface WeighedSoundEventsAccessor {
    @Accessor(value = "list", remap = false)
    List<Weighted<Sound>> betterRadio$getSounds();
}
