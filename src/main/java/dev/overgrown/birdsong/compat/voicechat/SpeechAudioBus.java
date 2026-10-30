package dev.overgrown.birdsong.compat.voicechat;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.LockSupport;

public final class SpeechAudioBus {
    public static final int SAMPLE_RATE = 48000;

    private static final int CAPACITY = 1 << 17;
    private static final int MASK = CAPACITY - 1;
    private static final short[] RING = new short[CAPACITY];

    private static volatile long written;
    private static volatile long consumed;
    private static volatile long dropped;
    private static volatile @Nullable Thread reader;

    private SpeechAudioBus() {}

    public static boolean isListening() {
        return reader != null;
    }

    public static void open(Thread consumer) {
        consumed = written;
        reader = consumer;
    }

    public static void close(Thread consumer) {
        if (reader == consumer) reader = null;
    }

    public static long dropped() {
        return dropped;
    }

    public static void write(short[] pcm, int length) {
        Thread consumer = reader;
        if (consumer == null || length <= 0) return;
        long head = written;
        if (length > CAPACITY - (head - consumed)) {
            dropped += length;
            return;
        }
        int start = (int) (head & MASK);
        int first = Math.min(length, CAPACITY - start);
        System.arraycopy(pcm, 0, RING, start, first);
        if (first < length) System.arraycopy(pcm, first, RING, 0, length - first);
        written = head + length;
        LockSupport.unpark(consumer);
    }

    public static int read(short[] out, long waitNanos) {
        long tail = consumed;
        long available = written - tail;
        if (available <= 0) {
            LockSupport.parkNanos(waitNanos);
            available = written - tail;
            if (available <= 0) return 0;
        }
        int count = (int) Math.min(available, out.length);
        int start = (int) (tail & MASK);
        int first = Math.min(count, CAPACITY - start);
        System.arraycopy(RING, start, out, 0, first);
        if (first < count) System.arraycopy(RING, 0, out, first, count - first);
        consumed = tail + count;
        return count;
    }
}
