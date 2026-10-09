package com.radaeli.betterradio.neoforge.client;

/** Small handoff between the client and audio threads; no audio calls on the rendering thread. */
public final class AudioPlaybackProgress {
    private volatile double positionSeconds;
    private volatile long requestedUntilNanos;

    public void requestUpdates() {
        requestedUntilNanos = System.nanoTime() + 500_000_000L;
    }

    public boolean updatesRequested(long now) { return now < requestedUntilNanos; }

    public void update(double seconds) {
        // OpenAL resets its offset when a source ends. Keep the final observation until track disposal.
        if (Double.isFinite(seconds)) positionSeconds = Math.max(positionSeconds, seconds);
    }

    public double positionSeconds() { return positionSeconds; }
}
