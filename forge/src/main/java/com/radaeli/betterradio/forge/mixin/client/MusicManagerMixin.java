package com.radaeli.betterradio.forge.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import com.radaeli.betterradio.forge.client.MusicClientForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives gameplay background music ownership to Better Radio while retaining vanilla screen music. */
@Mixin(MusicManager.class)
abstract class MusicManagerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private SoundInstance currentMusic;
    @Shadow public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void betterRadio$ownGameplayMusic(CallbackInfo callback) {
        if (minecraft.player == null || minecraft.level == null
                || (minecraft.screen != null && minecraft.screen.getBackgroundMusic() != null)) {
            return;
        }

        MusicClientForge.observeVanillaMusic(minecraft.getSituationalMusic());

        // stopPlaying adds to nextSongDelay; do not call it once the field is clear.
        if (currentMusic != null) {
            stopPlaying();
        }
        callback.cancel();
    }
}
