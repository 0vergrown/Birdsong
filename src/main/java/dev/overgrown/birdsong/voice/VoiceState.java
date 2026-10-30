package dev.overgrown.birdsong.voice;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class VoiceState {
    private VoiceState() {}

    private static final long SPEAK_TIMEOUT_NANOS = 400_000_000L;
    private static final int SILENCE_FRAME_BYTES = 16;
    private static final int LOUD_FRAME_BYTES = 160;
    private static final int LEVEL_SPAN = LOUD_FRAME_BYTES - SILENCE_FRAME_BYTES;
    private static final Function<UUID, Speaker> CREATE = ignored -> new Speaker();

    private static final class Speaker {
        volatile long lastPacket = System.nanoTime() - (SPEAK_TIMEOUT_NANOS << 1);
        volatile int loudness;
        volatile boolean whispering;
        volatile boolean disabled;
        volatile boolean disconnected;
        volatile boolean speaking;
        long lastSpokeTick = -1L;
    }

    private static final Map<UUID, Speaker> SPEAKERS = new ConcurrentHashMap<>();

    private static volatile @Nullable Predicate<UUID> disabledCheck;
    private static long currentTick;

    public static void setDisabledCheck(@Nullable Predicate<UUID> check) {
        disabledCheck = check;
    }

    public static void micPacket(UUID uuid, int encodedBytes, boolean whispering) {
        Speaker speaker = SPEAKERS.computeIfAbsent(uuid, CREATE);
        int sample = level(encodedBytes);
        int previous = speaker.loudness;
        speaker.loudness = sample >= previous ? sample : (previous * 3 + sample) >> 2;
        speaker.whispering = whispering;
        speaker.lastPacket = System.nanoTime();
    }

    public static void stateChanged(UUID uuid, boolean disabled, boolean disconnected) {
        Speaker speaker = SPEAKERS.computeIfAbsent(uuid, CREATE);
        speaker.disabled = disabled;
        speaker.disconnected = disconnected;
        if (disconnected) speaker.loudness = 0;
    }

    private static int level(int encodedBytes) {
        int scaled = (encodedBytes - SILENCE_FRAME_BYTES) * 100 / LEVEL_SPAN;
        if (scaled < 0) return 0;
        return Math.min(scaled, 100);
    }

    public static void tick(MinecraftServer server) {
        currentTick++;
        if (SPEAKERS.isEmpty()) return;
        long now = System.nanoTime();
        for (Map.Entry<UUID, Speaker> entry : SPEAKERS.entrySet()) {
            Speaker speaker = entry.getValue();
            boolean active = !speaker.disconnected && now - speaker.lastPacket < SPEAK_TIMEOUT_NANOS;
            if (active) {
                speaker.lastSpokeTick = currentTick;
                if (!speaker.speaking) {
                    speaker.speaking = true;
                    VoicePowerHandler.onSpeakStart(server, entry.getKey());
                }
            } else if (speaker.speaking) {
                speaker.speaking = false;
                speaker.whispering = false;
                speaker.loudness = 0;
                VoicePowerHandler.onSpeakStop(server, entry.getKey());
            }
        }
    }

    public static boolean isSpeaking(UUID uuid) {
        Speaker speaker = SPEAKERS.get(uuid);
        return speaker != null && speaker.speaking;
    }

    public static boolean anyoneSpeaking() {
        for (Speaker speaker : SPEAKERS.values()) {
            if (speaker.speaking) return true;
        }
        return false;
    }

    public static boolean isWhispering(UUID uuid) {
        Speaker speaker = SPEAKERS.get(uuid);
        return speaker != null && speaker.speaking && speaker.whispering;
    }

    public static boolean isDisabled(UUID uuid) {
        Predicate<UUID> check = disabledCheck;
        if (check != null) return check.test(uuid);
        Speaker speaker = SPEAKERS.get(uuid);
        return speaker != null && speaker.disabled;
    }

    public static boolean isDisconnected(UUID uuid) {
        Speaker speaker = SPEAKERS.get(uuid);
        return speaker != null && speaker.disconnected;
    }

    public static int loudness(UUID uuid) {
        Speaker speaker = SPEAKERS.get(uuid);
        return speaker == null || !speaker.speaking ? 0 : speaker.loudness;
    }

    public static long ticksSinceSpoke(UUID uuid) {
        Speaker speaker = SPEAKERS.get(uuid);
        if (speaker == null || speaker.lastSpokeTick < 0L) return Long.MAX_VALUE;
        return currentTick - speaker.lastSpokeTick;
    }

    public static void forget(UUID uuid) {
        SPEAKERS.remove(uuid);
    }

    public static void clear() {
        SPEAKERS.clear();
        disabledCheck = null;
        currentTick = 0L;
    }
}
