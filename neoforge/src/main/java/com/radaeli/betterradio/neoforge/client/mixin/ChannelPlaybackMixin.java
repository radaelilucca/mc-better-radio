package com.radaeli.betterradio.neoforge.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.radaeli.betterradio.neoforge.client.AudioPlaybackProgress;
import com.radaeli.betterradio.neoforge.client.RadioAudioChannel;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs on Minecraft's audio thread. Non-radio channels never query extra OpenAL timing data. */
@Mixin(value = Channel.class, remap = false)
public abstract class ChannelPlaybackMixin implements RadioAudioChannel {
    @Shadow @Final private int source;
    @Unique private AudioPlaybackProgress betterRadio$progress;
    @Unique private double betterRadio$processedSeconds;
    @Unique private double betterRadio$bytesPerSecond;
    @Unique private long betterRadio$nextSampleNanos;

    @Override
    public void betterRadio$trackProgress(AudioPlaybackProgress progress) {
        betterRadio$progress = progress;
    }

    @Inject(method = "attachBufferStream", at = @At("HEAD"))
    private void betterRadio$streamFormat(AudioStream stream, CallbackInfo ci) {
        var format = stream.getFormat();
        betterRadio$bytesPerSecond = (double) format.getFrameRate() * format.getFrameSize();
        betterRadio$processedSeconds = 0;
    }

    @Redirect(method = "removeProcessedBuffers", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/AL10;alDeleteBuffers([I)V", remap = false))
    private void betterRadio$rememberProcessedBuffers(int[] buffers) {
        if (betterRadio$progress != null && betterRadio$bytesPerSecond > 0) {
            for (int buffer : buffers) {
                betterRadio$processedSeconds += AL10.alGetBufferi(buffer, AL10.AL_SIZE) / betterRadio$bytesPerSecond;
            }
        }
        AL10.alDeleteBuffers(buffers);
    }

    @Inject(method = "updateStream", at = @At("TAIL"))
    private void betterRadio$samplePosition(CallbackInfo ci) {
        if (betterRadio$progress == null) return;
        long now = System.nanoTime();
        if (now < betterRadio$nextSampleNanos || !betterRadio$progress.updatesRequested(now)) return;
        betterRadio$nextSampleNanos = now + 250_000_000L;
        betterRadio$progress.update(betterRadio$processedSeconds + AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET));
    }

    @Inject(method = "destroy", at = @At("TAIL"))
    private void betterRadio$clearProgress(CallbackInfo ci) {
        betterRadio$progress = null;
    }
}
