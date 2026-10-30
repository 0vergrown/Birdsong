package dev.overgrown.birdsong.client.speech;

import dev.overgrown.apoli.client.ApoliKeyMappings;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.birdsong.BirdsongIds;
import dev.overgrown.birdsong.Platform;
import dev.overgrown.birdsong.client.BirdsongClientConfig;
import dev.overgrown.birdsong.client.ClientPlatform;
import dev.overgrown.birdsong.speech.ActionOnSpeechPower;
import dev.overgrown.birdsong.speech.PhraseMatcher;
import dev.overgrown.birdsong.speech.SpeechLanguage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SpeechClient {
    private static final int RETRY_TICKS = 100;
    private static final int FAILURE_RETRY_TICKS = 1200;
    private static final int IDLE_STOP_TICKS = 600;
    private static final String MODELS_PAGE = "https://alphacephei.com/vosk/models";

    private static final Set<ResourceLocation> FIRED = new HashSet<>();

    private static final VoskTranscriber.Listener LISTENER = new VoskTranscriber.Listener() {
        @Override
        public void text(String text, boolean complete) {
            if (!complete && !BirdsongClientConfig.get().instant()) return;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> matchAndSend(mc, text, complete));
        }

        @Override
        public void utteranceEnded() {
            Minecraft.getInstance().execute(FIRED::clear);
        }
    };

    private static volatile @Nullable VoskTranscriber transcriber;
    private static @Nullable KeyMapping pushToTalk;
    private static @Nullable String language;
    private static @Nullable String lastReport;
    private static @Nullable String modelName;
    private static boolean prompted;
    private static int retry;
    private static int idle;
    private static SpeechModels.Phase reportedPhase = SpeechModels.Phase.IDLE;
    private static int reportedPercent;

    private SpeechClient() {}

    public static void setPushToTalkKey(KeyMapping key) {
        pushToTalk = key;
    }

    public static void onJoin() {
        prompted = false;
        lastReport = null;
        retry = 0;
        idle = 0;
        FIRED.clear();
    }

    public static void onLeave() {
        stop();
    }

    public static void restart() {
        stop();
        retry = 0;
        lastReport = null;
    }

    private static void stop() {
        VoskTranscriber current = transcriber;
        transcriber = null;
        if (current != null) current.stop();
    }

    public static void clientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        reportDownload(mc);
        if (!hasSpeechPowers(player)) {
            if (transcriber != null && ++idle >= IDLE_STOP_TICKS) stop();
            return;
        }
        idle = 0;
        BirdsongClientConfig config = BirdsongClientConfig.get();
        if (config.speech() == BirdsongClientConfig.Speech.OFF) {
            if (transcriber != null) stop();
            return;
        }
        if (config.speech() == BirdsongClientConfig.Speech.ASK) {
            if (!prompted) {
                prompted = true;
                prompt(mc);
            }
            return;
        }
        VoskTranscriber current = transcriber;
        if (current == null) {
            if (SpeechModels.busy() || retry-- > 0) return;
            retry = RETRY_TICKS;
            start(mc, config);
            return;
        }
        if (current.hasFailed()) {
            String reason = current.failure();
            stop();
            retry = FAILURE_RETRY_TICKS;
            say(mc, Component.translatable("birdsong.speech.failed", reason), ChatFormatting.RED);
            return;
        }
        if (current.consumeSilentCapture()) {
            say(mc, Component.translatable("birdsong.speech.silent"), ChatFormatting.YELLOW);
        }
        if (current.source() == VoskTranscriber.Source.VOICE_CHAT) return;
        if (mc.screen != null) {
            current.setListening(false);
            return;
        }
        if (!config.pushToTalk()) {
            current.setListening(true);
            return;
        }
        KeyMapping key = pushToTalk;
        current.setListening(key != null && !key.isUnbound() && ApoliKeyMappings.isDown(key));
    }

    public static boolean hasSpeechPowers(LocalPlayer player) {
        PowerContainer container = PowerContainer.of(player);
        return container != null && !container.isEmpty() && !container.powersOfType(BirdsongIds.ACTION_ON_SPEECH).isEmpty();
    }

    private static void start(Minecraft mc, BirdsongClientConfig config) {
        String minecraftLanguage = mc.options.languageCode;
        SpeechModels.Installed model = SpeechModels.find(config.model(), minecraftLanguage);
        if (model == null) {
            if (SpeechModels.progress().phase() == SpeechModels.Phase.IDLE) {
                say(mc, Component.translatable("birdsong.speech.download.start"), ChatFormatting.GRAY);
                SpeechModels.download(minecraftLanguage);
            }
            return;
        }
        boolean voiceChat = useVoiceChat(config);
        if (!voiceChat && config.pushToTalk() && (pushToTalk == null || pushToTalk.isUnbound())) {
            report(mc, "no-key", Component.translatable("birdsong.speech.no_key"), ChatFormatting.YELLOW);
            return;
        }
        language = model.language() != null ? model.language() : SpeechLanguage.toBcp47(minecraftLanguage);
        modelName = model.name();
        VoskTranscriber created = new VoskTranscriber(model.path(), config.inputDevice(),
            voiceChat ? VoskTranscriber.Source.VOICE_CHAT : VoskTranscriber.Source.MICROPHONE,
            config.pushToTalk(), config.echo(), LISTENER);
        transcriber = created;
        created.start();
        if (voiceChat) {
            report(mc, "ready-voicechat", Component.translatable("birdsong.speech.ready.voicechat"), ChatFormatting.GRAY);
        } else if (config.pushToTalk() && pushToTalk != null) {
            report(mc, "ready-push", Component.translatable("birdsong.speech.ready.push_to_talk",
                pushToTalk.getTranslatedKeyMessage()), ChatFormatting.GRAY);
        } else {
            report(mc, "ready-open", Component.translatable("birdsong.speech.ready.open"), ChatFormatting.GRAY);
        }
    }

    private static boolean useVoiceChat(BirdsongClientConfig config) {
        return switch (config.source()) {
            case MICROPHONE -> false;
            case VOICECHAT -> true;
            case AUTO -> Platform.isModLoaded("voicechat");
        };
    }

    private static void reportDownload(Minecraft mc) {
        SpeechModels.Progress progress = SpeechModels.progress();
        SpeechModels.Phase phase = progress.phase();
        if (phase == SpeechModels.Phase.DOWNLOADING && progress.offer() != null) {
            int percent = progress.percent();
            if (reportedPhase != SpeechModels.Phase.DOWNLOADING) {
                reportedPercent = -1;
                say(mc, Component.translatable("birdsong.speech.download.offer", progress.offer().languageName(),
                    megabytes(progress.offer().size())), ChatFormatting.GRAY);
            }
            if (percent / 25 != reportedPercent / 25 && percent < 100) {
                reportedPercent = percent;
                mc.gui.setOverlayMessage(Component.translatable("birdsong.speech.download.progress", percent), false);
            }
        } else if (phase == SpeechModels.Phase.DONE) {
            say(mc, Component.translatable("birdsong.speech.download.done", progress.offer() == null ? "" : progress.offer().name()),
                ChatFormatting.GREEN);
            SpeechModels.acknowledge();
            retry = 0;
        } else if (phase == SpeechModels.Phase.FAILED) {
            say(mc, Component.translatable("birdsong.speech.download.failed", progress.error(), link(MODELS_PAGE),
                SpeechModels.directory().toAbsolutePath().toString()), ChatFormatting.RED);
            SpeechModels.acknowledge();
            retry = FAILURE_RETRY_TICKS;
        }
        reportedPhase = phase;
    }

    private static String megabytes(long bytes) {
        return bytes <= 0 ? "?" : Long.toString(Math.max(1L, bytes >> 20));
    }

    private static void prompt(Minecraft mc) {
        MutableComponent message = Component.translatable("birdsong.speech.prompt").withStyle(ChatFormatting.GRAY);
        if (SpeechModels.find(BirdsongClientConfig.get().model(), mc.options.languageCode) == null) {
            message.append(" ").append(Component.translatable("birdsong.speech.prompt.download").withStyle(ChatFormatting.GRAY));
        }
        message.append("\n")
            .append(button("birdsong.speech.prompt.on", "/birdsong speech on", ChatFormatting.GREEN))
            .append(" ")
            .append(button("birdsong.speech.prompt.later", "/birdsong speech later", ChatFormatting.YELLOW))
            .append(" ")
            .append(button("birdsong.speech.prompt.never", "/birdsong speech off", ChatFormatting.RED));
        say(mc, message, ChatFormatting.GRAY);
    }

    private static Component button(String key, String command, ChatFormatting colour) {
        return Component.translatable(key).withStyle(style -> style
            .withColor(colour)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(key + ".hover"))));
    }

    private static Component link(String url) {
        return Component.literal(url).withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
    }

    public static Component enable() {
        BirdsongClientConfig.get().setSpeech(BirdsongClientConfig.Speech.ON);
        restart();
        return Component.translatable("birdsong.speech.enabled");
    }

    public static Component disable() {
        BirdsongClientConfig.get().setSpeech(BirdsongClientConfig.Speech.OFF);
        stop();
        return Component.translatable("birdsong.speech.disabled");
    }

    public static Component later() {
        prompted = true;
        return Component.translatable("birdsong.speech.later");
    }

    public static Component status() {
        BirdsongClientConfig config = BirdsongClientConfig.get();
        VoskTranscriber current = transcriber;
        Component state = current == null
            ? Component.translatable("birdsong.speech.status.stopped")
            : Component.translatable(current.source() == VoskTranscriber.Source.VOICE_CHAT
                ? "birdsong.speech.status.voicechat" : "birdsong.speech.status.microphone");
        return Component.translatable("birdsong.speech.status", config.speech().getSerializedName(), state,
            modelName == null ? "-" : modelName, language == null ? "-" : language);
    }

    private static void matchAndSend(Minecraft mc, String text, boolean complete) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        boolean echo = BirdsongClientConfig.get().echo();
        if (complete && echo) say(mc, Component.translatable("birdsong.speech.heard", text), ChatFormatting.DARK_GRAY);
        if (!ClientPlatform.canSendTrigger()) {
            if (complete) report(mc, "no-server", Component.translatable("birdsong.speech.no_server"), ChatFormatting.YELLOW);
            return;
        }
        PowerContainer container = PowerContainer.of(player);
        if (container == null || container.isEmpty()) return;
        List<ResourceLocation> powers = container.powersOfType(BirdsongIds.ACTION_ON_SPEECH);
        if (powers.isEmpty()) return;
        PhraseMatcher.Transcript transcript = PhraseMatcher.transcript(text);
        int matched = 0;
        for (int i = 0, n = powers.size(); i < n; i++) {
            ResourceLocation powerId = powers.get(i);
            if (container.isSuppressed(powerId)) continue;
            Power power = ApoliPowers.get(powerId);
            if (power == null || !(power.config() instanceof ActionOnSpeechPower.Config config)) continue;
            if (ActionOnSpeechPower.matchedTrigger(config, transcript, language, complete) == null) continue;
            matched++;
            if (FIRED.add(powerId)) ClientPlatform.sendTrigger(powerId);
        }
        if (complete && echo && matched == 0) say(mc, Component.translatable("birdsong.speech.no_match"), ChatFormatting.DARK_GRAY);
    }

    private static void report(Minecraft mc, String id, Component message, ChatFormatting colour) {
        if (id.equals(lastReport)) return;
        lastReport = id;
        say(mc, message, colour);
    }

    private static void say(Minecraft mc, Component message, ChatFormatting colour) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.literal("[Birdsong] ").withStyle(colour)
            .append(message.copy().withStyle(style -> style.getColor() == null ? style.withColor(colour) : style)), false);
    }
}
