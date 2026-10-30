package dev.overgrown.birdsong.client.speech;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.nio.file.Path;

public final class SpeechEngines {
    private static volatile boolean quiet;

    private SpeechEngines() {}

    public static SpeechEngine open(Path model, float sampleRate) throws IOException {
        if (!quiet) {
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            quiet = true;
        }
        Model loaded = new Model(model.toString());
        try {
            return new Vosk(loaded, new Recognizer(loaded, sampleRate));
        } catch (IOException | RuntimeException e) {
            loaded.close();
            throw e;
        }
    }

    private record Vosk(Model model, Recognizer recognizer) implements SpeechEngine {
        @Override
        public boolean accept(short[] samples, int length) {
            return recognizer.acceptWaveForm(samples, length);
        }

        @Override
        public boolean accept(byte[] data, int length) {
            return recognizer.acceptWaveForm(data, length);
        }

        @Override
        public String result() {
            return recognizer.getResult();
        }

        @Override
        public String partial() {
            return recognizer.getPartialResult();
        }

        @Override
        public String finalResult() {
            return recognizer.getFinalResult();
        }

        @Override
        public void reset() {
            recognizer.reset();
        }

        @Override
        public void close() {
            recognizer.close();
            model.close();
        }
    }
}
