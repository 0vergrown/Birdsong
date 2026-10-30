package dev.overgrown.birdsong.client.speech;

public interface SpeechEngine extends AutoCloseable {
    boolean accept(short[] samples, int length);

    boolean accept(byte[] data, int length);

    String result();

    String partial();

    String finalResult();

    void reset();

    @Override
    void close();
}
