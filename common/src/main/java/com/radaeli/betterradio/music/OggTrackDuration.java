package com.radaeli.betterradio.music;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.OptionalDouble;

/** Reads Vorbis timing metadata without decoding or retaining audio samples. */
public final class OggTrackDuration {
    private OggTrackDuration() { }

    public static OptionalDouble read(InputStream input) throws IOException {
        byte[] header = new byte[27];
        byte[] segments = new byte[255];
        byte[] identification = new byte[16];
        int streamSerial = 0;
        long sampleRate = 0;
        long lastGranule = -1;
        while (true) {
            int read = input.readNBytes(header, 0, header.length);
            if (read == 0) break;
            if (read != header.length || header[0] != 'O' || header[1] != 'g'
                    || header[2] != 'g' || header[3] != 'S' || header[4] != 0) {
                return OptionalDouble.empty();
            }
            int count = Byte.toUnsignedInt(header[26]);
            if (input.readNBytes(segments, 0, count) != count) return OptionalDouble.empty();
            int bodySize = 0;
            for (int i = 0; i < count; i++) bodySize += Byte.toUnsignedInt(segments[i]);
            int serial = (int) littleEndian(header, 14, 4);
            if (sampleRate == 0) {
                if ((header[5] & 2) == 0 || bodySize < identification.length) return OptionalDouble.empty();
                if (input.readNBytes(identification, 0, identification.length) != identification.length) {
                    return OptionalDouble.empty();
                }
                if (!Arrays.equals(Arrays.copyOf(identification, 7), new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'})) {
                    return OptionalDouble.empty();
                }
                sampleRate = littleEndian(identification, 12, 4);
                if (sampleRate == 0) return OptionalDouble.empty();
                streamSerial = serial;
                bodySize -= identification.length;
            } else if (serial != streamSerial) {
                // Minecraft's music resources are single Vorbis streams; never invent a multiplexed duration.
                return OptionalDouble.empty();
            }
            input.skipNBytes(bodySize);
            long granule = littleEndian(header, 6, 8);
            if (granule >= 0) lastGranule = Math.max(lastGranule, granule);
            if ((header[5] & 4) != 0) {
                return lastGranule > 0 ? OptionalDouble.of((double) lastGranule / sampleRate) : OptionalDouble.empty();
            }
        }
        return OptionalDouble.empty();
    }

    private static long littleEndian(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) value |= (long) Byte.toUnsignedInt(bytes[offset + i]) << (8 * i);
        return value;
    }
}
