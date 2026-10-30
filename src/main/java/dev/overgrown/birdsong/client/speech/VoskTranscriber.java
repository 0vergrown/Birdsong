package dev.overgrown.birdsong.client.speech;

import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.overgrown.birdsong.Birdsong;
import dev.overgrown.birdsong.compat.voicechat.SpeechAudioBus;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.nio.file.Path;
import java.util.Locale;

public final class VoskTranscriber {
    public enum Source { MICROPHONE, VOICE_CHAT }

    public interface Listener {
        void text(String text, boolean complete);

        void utteranceEnded();
    }

    private static final float MICROPHONE_RATE = 16000.0F;
    private static final AudioFormat FORMAT = new AudioFormat(MICROPHONE_RATE, 16, 1, true, false);
    private static final long UTTERANCE_GAP_NANOS = 350_000_000L;
    private static final long PARTIAL_INTERVAL_NANOS = 60_000_000L;
    private static final long BUS_WAIT_NANOS = 100_000_000L;
    private static final int CHUNK_BYTES = 1024;

    private final Path model;
    private final String deviceHint;
    private final Source source;
    private final boolean pushToTalk;
    private final boolean verbose;
    private final Listener listener;

    private @Nullable Thread thread;
    private volatile boolean running;
    private volatile boolean listening;
    private volatile boolean failed;
    private volatile boolean silentCapture;
    private volatile @Nullable String failure;
    private String lineName = "system default";
    private String lastPartial = "";
    private long nextPartial;

    public VoskTranscriber(Path model, String deviceHint, Source source, boolean pushToTalk, boolean verbose,
                           Listener listener) {
        this.model = model;
        this.deviceHint = deviceHint == null ? "" : deviceHint;
        this.source = source;
        this.pushToTalk = pushToTalk;
        this.verbose = verbose;
        this.listener = listener;
    }

    public Source source() {
        return source;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread worker = new Thread(this::run, "Birdsong-Speech");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        thread = worker;
        worker.start();
    }

    public void stop() {
        running = false;
        listening = false;
        Thread worker = thread;
        if (worker != null) worker.interrupt();
        thread = null;
    }

    public void setListening(boolean value) {
        listening = value;
    }

    public boolean hasFailed() {
        return failed;
    }

    public String failure() {
        String message = failure;
        return message == null ? "unknown error" : message;
    }

    public boolean consumeSilentCapture() {
        if (!silentCapture) return false;
        silentCapture = false;
        return true;
    }

    private void run() {
        SpeechEngine engine = null;
        try {
            engine = SpeechEngines.open(model, source == Source.VOICE_CHAT ? SpeechAudioBus.SAMPLE_RATE : MICROPHONE_RATE);
            Birdsong.LOGGER.info("[Birdsong] Speech recognition started ({}, {}).", model.getFileName(),
                source == Source.VOICE_CHAT ? "Simple Voice Chat" : pushToTalk ? "microphone, push to talk" : "microphone");
            if (source == Source.VOICE_CHAT) {
                runVoiceChat(engine);
            } else if (pushToTalk) {
                runPushToTalk(engine);
            } else {
                runOpenMicrophone(engine);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Throwable root = t;
            while (root.getCause() != null && root.getMessage() == null) root = root.getCause();
            failure = root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
            failed = true;
            Birdsong.LOGGER.error("[Birdsong] Speech recognition stopped", t);
        } finally {
            if (engine != null) engine.close();
        }
    }

    private void runVoiceChat(SpeechEngine engine) {
        Thread self = Thread.currentThread();
        SpeechAudioBus.open(self);
        try {
            short[] buffer = new short[4800];
            boolean speaking = false;
            long lastAudio = 0L;
            while (running) {
                int read = SpeechAudioBus.read(buffer, BUS_WAIT_NANOS);
                long now = System.nanoTime();
                if (read == 0) {
                    if (speaking && now - lastAudio >= UTTERANCE_GAP_NANOS) {
                        speaking = false;
                        finish(engine);
                    }
                    continue;
                }
                if (!speaking) {
                    speaking = true;
                    engine.reset();
                    resetPartial();
                }
                lastAudio = now;
                feed(engine, buffer, read, now);
            }
        } finally {
            SpeechAudioBus.close(self);
        }
    }

    private void runPushToTalk(SpeechEngine engine) throws LineUnavailableException, InterruptedException {
        logDevices();
        byte[] buffer = new byte[CHUNK_BYTES];
        TargetDataLine line = null;
        long captured = 0L;
        try {
            while (running) {
                if (!listening) {
                    if (line != null) {
                        captured += drain(line, engine, buffer);
                        closeLine(line);
                        line = null;
                        if (captured == 0L) {
                            silentCapture = true;
                            Birdsong.LOGGER.warn("[Birdsong] The microphone delivered no audio; another program may hold it.");
                        }
                        finish(engine);
                        captured = 0L;
                    }
                    Thread.sleep(20L);
                    continue;
                }
                if (line == null) {
                    line = openLine();
                    engine.reset();
                    resetPartial();
                    captured = 0L;
                    line.start();
                }
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                captured += read;
                if (engine.accept(buffer, read)) {
                    emit(engine.result());
                    listener.utteranceEnded();
                    resetPartial();
                } else {
                    pollPartial(engine, System.nanoTime());
                }
            }
        } finally {
            if (line != null) closeLine(line);
        }
    }

    private void runOpenMicrophone(SpeechEngine engine) throws LineUnavailableException, InterruptedException {
        logDevices();
        EnergyGate gate = new EnergyGate();
        byte[] bytes = new byte[EnergyGate.FRAME * 2];
        short[] frame = new short[EnergyGate.FRAME];
        EnergyGate.Sink sink = new EnergyGate.Sink() {
            @Override
            public void speechStarted() {
                engine.reset();
                resetPartial();
            }

            @Override
            public void audio(short[] samples, int length) {
                feed(engine, samples, length, System.nanoTime());
            }

            @Override
            public void speechEnded() {
                finish(engine);
            }
        };
        TargetDataLine line = null;
        try {
            while (running) {
                if (!listening) {
                    if (line != null) {
                        closeLine(line);
                        line = null;
                        if (gate.speaking()) finish(engine);
                        gate.reset();
                    }
                    Thread.sleep(50L);
                    continue;
                }
                if (line == null) {
                    line = openLine();
                    line.start();
                    gate.reset();
                }
                int read = 0;
                while (read < bytes.length && running && listening) {
                    int got = line.read(bytes, read, bytes.length - read);
                    if (got <= 0) break;
                    read += got;
                }
                if (read < bytes.length) continue;
                for (int i = 0; i < EnergyGate.FRAME; i++) {
                    frame[i] = (short) ((bytes[2 * i] & 0xFF) | (bytes[2 * i + 1] << 8));
                }
                gate.push(frame, sink);
            }
        } finally {
            if (line != null) closeLine(line);
        }
    }

    private void feed(SpeechEngine engine, short[] samples, int length, long now) {
        if (engine.accept(samples, length)) {
            emit(engine.result());
            listener.utteranceEnded();
            resetPartial();
        } else {
            pollPartial(engine, now);
        }
    }

    private TargetDataLine openLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!deviceHint.isBlank()) {
            String want = deviceHint.toLowerCase(Locale.ROOT);
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (!mixerInfo.getName().toLowerCase(Locale.ROOT).contains(want)) continue;
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (!mixer.isLineSupported(info)) continue;
                TargetDataLine picked = (TargetDataLine) mixer.getLine(info);
                picked.open(FORMAT);
                lineName = mixerInfo.getName();
                return picked;
            }
            Birdsong.LOGGER.warn("[Birdsong] No recording device matched \"{}\"; using the system default.", deviceHint);
        }
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("no recording device supports 16 kHz mono 16-bit capture");
        }
        TargetDataLine picked = (TargetDataLine) AudioSystem.getLine(info);
        picked.open(FORMAT);
        lineName = "system default";
        return picked;
    }

    private static void closeLine(TargetDataLine line) {
        line.stop();
        line.close();
    }

    private void logDevices() {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        StringBuilder found = new StringBuilder();
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            if (!AudioSystem.getMixer(mixerInfo).isLineSupported(info)) continue;
            if (!found.isEmpty()) found.append(" | ");
            found.append(mixerInfo.getName());
        }
        Birdsong.LOGGER.info("[Birdsong] Recording devices: {}", found.isEmpty() ? "(none)" : found);
    }

    private long drain(TargetDataLine line, SpeechEngine engine, byte[] buffer) {
        long total = 0L;
        for (int guard = 0; guard < 128; guard++) {
            int available = Math.min(line.available(), buffer.length);
            int chunk = available - (available & 1);
            if (chunk <= 0) return total;
            int read = line.read(buffer, 0, chunk);
            if (read <= 0) return total;
            total += read;
            engine.accept(buffer, read);
        }
        return total;
    }

    private void pollPartial(SpeechEngine engine, long now) {
        if (now < nextPartial) return;
        nextPartial = now + PARTIAL_INTERVAL_NANOS;
        String text = extract(engine.partial(), "partial");
        if (text == null || text.isBlank() || text.equals(lastPartial)) return;
        lastPartial = text;
        listener.text(text, false);
    }

    private void resetPartial() {
        lastPartial = "";
        nextPartial = 0L;
    }

    private void finish(SpeechEngine engine) {
        emit(engine.finalResult());
        listener.utteranceEnded();
        resetPartial();
    }

    private void emit(String json) {
        String text = extract(json, "text");
        if (text == null || text.isBlank()) return;
        if (verbose) Birdsong.LOGGER.info("[Birdsong] Heard \"{}\" ({})", text, source == Source.VOICE_CHAT ? "voice chat" : lineName);
        listener.text(text, true);
    }

    private static @Nullable String extract(String json, String field) {
        try {
            return new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(json)).get(field).asString().result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
