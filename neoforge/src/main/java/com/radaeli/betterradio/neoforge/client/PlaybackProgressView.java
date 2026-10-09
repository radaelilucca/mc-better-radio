package com.radaeli.betterradio.neoforge.client;

import com.radaeli.ruilib.ui.Bounds;
import com.radaeli.ruilib.ui.ProgressBar;
import com.radaeli.ruilib.ui.UiContainer;
import com.radaeli.ruilib.ui.UiContext;
import com.radaeli.ruilib.ui.UiPaint;
import com.radaeli.ruilib.ui.UiSize;
import com.radaeli.ruilib.ui.UiText;
import com.radaeli.ruilib.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** Updates independently of the screen's lists, selection and layout. Rendering only draws cached data. */
final class PlaybackProgressView extends UiContainer {
    private final ProgressBar bar = add(new ProgressBar(0, 1, 0).setBarHeight(3));
    private long nextUpdateNanos;
    private long displayedPosition = -1;
    private long displayedDuration = -2;
    private String time = "--:-- / --:--";

    void update() {
        long now = System.nanoTime();
        if (now < nextUpdateNanos) return;
        nextUpdateNanos = now + 250_000_000L;
        var progress = NeoForgeMusicPlatform.playbackProgress(Minecraft.getInstance());
        double duration = progress.durationSeconds().orElse(-1);
        bar.setRange(0, duration > 0 ? duration : 1).setValue(duration > 0 ? progress.positionSeconds() : 0);
        long positionSeconds = duration >= 0 ? (long) progress.positionSeconds() : -1;
        long durationSeconds = (long) duration;
        if (positionSeconds != displayedPosition || durationSeconds != displayedDuration) {
            displayedPosition = positionSeconds;
            displayedDuration = durationSeconds;
            time = durationSeconds >= 0 ? formatTime(positionSeconds) + " / " + formatTime(durationSeconds)
                    : "--:-- / --:--";
        }
    }

    private static String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    @Override
    protected UiSize measureIntrinsic(Font font, UiTheme theme, int maxWidth, int maxHeight) {
        return new UiSize(Math.min(220, maxWidth), Math.min(UiText.height(font, theme) + 7, maxHeight));
    }

    @Override
    protected void layoutChildren(Font font, UiTheme theme) {
        bar.layout(font, theme, new Bounds(bounds().x(), bounds().bottom() - 3, bounds().width(), 3));
    }

    @Override
    public void render(UiContext context) {
        UiPaint.textCentered(context, time, bounds().centerX(), bounds().y(), context.theme().palette().mutedText());
        bar.render(context);
    }
}
