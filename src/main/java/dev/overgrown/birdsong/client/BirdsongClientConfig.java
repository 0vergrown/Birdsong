package dev.overgrown.birdsong.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.birdsong.Birdsong;
import dev.overgrown.birdsong.Platform;
import net.minecraft.util.StringRepresentable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class BirdsongClientConfig {
    private static final com.google.gson.Gson PRINTER = new GsonBuilder().setPrettyPrinting().create();

    public enum Speech implements StringRepresentable {
        ASK, ON, OFF;

        public static final Codec<Speech> CODEC = StringRepresentable.fromEnum(Speech::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Source implements StringRepresentable {
        AUTO, VOICECHAT, MICROPHONE;

        public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Data(Speech speech, Source source, boolean pushToTalk, boolean instant, boolean echo,
                        String inputDevice, String model) {
        private static final Data DEFAULTS = new Data(Speech.ASK, Source.AUTO, true, true, false, "", "");

        private static final Codec<Data> READ = RecordCodecBuilder.create(i -> i.group(
            Speech.CODEC.optionalFieldOf("speech", DEFAULTS.speech).forGetter(Data::speech),
            Source.CODEC.optionalFieldOf("source", DEFAULTS.source).forGetter(Data::source),
            Codec.BOOL.optionalFieldOf("push_to_talk", DEFAULTS.pushToTalk).forGetter(Data::pushToTalk),
            Codec.BOOL.optionalFieldOf("instant", DEFAULTS.instant).forGetter(Data::instant),
            Codec.BOOL.optionalFieldOf("echo", DEFAULTS.echo).forGetter(Data::echo),
            Codec.STRING.optionalFieldOf("input_device", DEFAULTS.inputDevice).forGetter(Data::inputDevice),
            Codec.STRING.optionalFieldOf("model", DEFAULTS.model).forGetter(Data::model)
        ).apply(i, Data::new));

        private static final Codec<Data> WRITE = RecordCodecBuilder.create(i -> i.group(
            Speech.CODEC.fieldOf("speech").forGetter(Data::speech),
            Source.CODEC.fieldOf("source").forGetter(Data::source),
            Codec.BOOL.fieldOf("push_to_talk").forGetter(Data::pushToTalk),
            Codec.BOOL.fieldOf("instant").forGetter(Data::instant),
            Codec.BOOL.fieldOf("echo").forGetter(Data::echo),
            Codec.STRING.fieldOf("input_device").forGetter(Data::inputDevice),
            Codec.STRING.fieldOf("model").forGetter(Data::model)
        ).apply(i, Data::new));
    }

    private static BirdsongClientConfig instance;

    private Data data;

    private BirdsongClientConfig(Data data) {
        this.data = data;
    }

    public static BirdsongClientConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public Speech speech() {
        return data.speech;
    }

    public Source source() {
        return data.source;
    }

    public boolean pushToTalk() {
        return data.pushToTalk;
    }

    public boolean instant() {
        return data.instant;
    }

    public boolean echo() {
        return data.echo;
    }

    public String inputDevice() {
        return data.inputDevice;
    }

    public String model() {
        return data.model;
    }

    public void setSpeech(Speech value) {
        update(new Data(value, data.source, data.pushToTalk, data.instant, data.echo, data.inputDevice, data.model));
    }

    public void setSource(Source value) {
        update(new Data(data.speech, value, data.pushToTalk, data.instant, data.echo, data.inputDevice, data.model));
    }

    public void setPushToTalk(boolean value) {
        update(new Data(data.speech, data.source, value, data.instant, data.echo, data.inputDevice, data.model));
    }

    public void setInstant(boolean value) {
        update(new Data(data.speech, data.source, data.pushToTalk, value, data.echo, data.inputDevice, data.model));
    }

    public void setEcho(boolean value) {
        update(new Data(data.speech, data.source, data.pushToTalk, data.instant, value, data.inputDevice, data.model));
    }

    public void setInputDevice(String value) {
        update(new Data(data.speech, data.source, data.pushToTalk, data.instant, data.echo, value == null ? "" : value.trim(), data.model));
    }

    public void setModel(String value) {
        update(new Data(data.speech, data.source, data.pushToTalk, data.instant, data.echo, data.inputDevice, value == null ? "" : value.trim()));
    }

    private void update(Data next) {
        if (next.equals(data)) return;
        data = next;
        save(next);
    }

    private static Path path() {
        return Platform.configDir().resolve("birdsong-client.json");
    }

    private static BirdsongClientConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                JsonElement json = JsonParser.parseString(Files.readString(path));
                Data loaded = Data.READ.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> Birdsong.LOGGER.warn("[Birdsong] birdsong-client.json: {}", error))
                    .orElse(Data.DEFAULTS);
                return new BirdsongClientConfig(loaded);
            } catch (Exception e) {
                Birdsong.LOGGER.warn("[Birdsong] Could not read birdsong-client.json; using defaults", e);
                return new BirdsongClientConfig(Data.DEFAULTS);
            }
        }
        Data migrated = migrate();
        save(migrated);
        return new BirdsongClientConfig(migrated);
    }

    private static Data migrate() {
        Path legacy = Platform.configDir().resolve("apoli-client.json");
        if (!Files.exists(legacy)) return Data.DEFAULTS;
        try {
            Dynamic<JsonElement> old = new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(legacy)));
            boolean enabled = old.get("speechToAction").asBoolean(false);
            String source = old.get("speechSource").asString("auto").toLowerCase(Locale.ROOT);
            Data migrated = new Data(
                enabled ? Speech.ON : Speech.ASK,
                switch (source) {
                    case "voicechat" -> Source.VOICECHAT;
                    case "microphone" -> Source.MICROPHONE;
                    default -> Source.AUTO;
                },
                old.get("speechPushToTalk").asBoolean(true),
                old.get("speechInstant").asBoolean(true),
                old.get("speechEcho").asBoolean(false),
                old.get("speechInputDevice").asString(""),
                "");
            if (!migrated.equals(Data.DEFAULTS)) {
                Birdsong.LOGGER.info("[Birdsong] Carried the speech settings over from apoli-client.json.");
            }
            return migrated;
        } catch (Exception e) {
            return Data.DEFAULTS;
        }
    }

    private static void save(Data data) {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            JsonElement json = Data.WRITE.encodeStart(JsonOps.INSTANCE, data)
                .resultOrPartial(error -> Birdsong.LOGGER.warn("[Birdsong] birdsong-client.json: {}", error))
                .orElse(null);
            if (json != null) Files.writeString(path, PRINTER.toJson(json));
        } catch (Exception e) {
            Birdsong.LOGGER.warn("[Birdsong] Could not write birdsong-client.json", e);
        }
    }
}
