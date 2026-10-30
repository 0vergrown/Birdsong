package dev.overgrown.birdsong.client.speech;

import java.nio.file.Path;

public final class SpeechEngines {
    private SpeechEngines() {}

    public static SpeechEngine open(Path model, float sampleRate) throws Exception {
        return VoskRuntime.open(model.toString(), sampleRate);
    }
}
