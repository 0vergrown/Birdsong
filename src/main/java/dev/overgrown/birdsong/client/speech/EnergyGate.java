package dev.overgrown.birdsong.client.speech;

final class EnergyGate {
    static final int FRAME = 320;

    private static final int ONSET_FRAMES = 3;
    private static final int HANGOVER_FRAMES = 23;
    private static final int PREFILL_FRAMES = 22;
    private static final double MIN_LEVEL = 0.006;
    private static final double NOISE_MARGIN = 3.2;
    private static final double NOISE_RISE = 1.003;

    interface Sink {
        void speechStarted();

        void audio(short[] frame, int length);

        void speechEnded();
    }

    private final short[][] prefill = new short[PREFILL_FRAMES][FRAME];
    private int prefillStart;
    private int prefillCount;
    private double noise = -1.0;
    private int onset;
    private int hangover;
    private boolean speaking;

    boolean speaking() {
        return speaking;
    }

    void reset() {
        speaking = false;
        onset = 0;
        hangover = 0;
        prefillCount = 0;
    }

    void push(short[] frame, Sink sink) {
        double level = level(frame);
        if (noise < 0.0 || level < noise) {
            noise = level;
        } else {
            noise = Math.min(level, noise * NOISE_RISE + 1.0e-6);
        }
        boolean voiced = level > Math.max(MIN_LEVEL, noise * NOISE_MARGIN);
        if (speaking) {
            sink.audio(frame, FRAME);
            if (voiced) {
                hangover = HANGOVER_FRAMES;
            } else if (--hangover <= 0) {
                speaking = false;
                onset = 0;
                sink.speechEnded();
            }
            return;
        }
        if (voiced && ++onset >= ONSET_FRAMES) {
            speaking = true;
            hangover = HANGOVER_FRAMES;
            onset = 0;
            sink.speechStarted();
            for (int i = 0; i < prefillCount; i++) {
                sink.audio(prefill[(prefillStart + i) % PREFILL_FRAMES], FRAME);
            }
            prefillCount = 0;
            sink.audio(frame, FRAME);
            return;
        }
        if (!voiced) onset = 0;
        int slot = (prefillStart + prefillCount) % PREFILL_FRAMES;
        System.arraycopy(frame, 0, prefill[slot], 0, FRAME);
        if (prefillCount < PREFILL_FRAMES) {
            prefillCount++;
        } else {
            prefillStart = (prefillStart + 1) % PREFILL_FRAMES;
        }
    }

    private static double level(short[] frame) {
        long sum = 0L;
        for (int i = 0; i < FRAME; i++) {
            int sample = frame[i];
            sum += (long) sample * sample;
        }
        return Math.sqrt(sum / (double) FRAME) / 32768.0;
    }
}
