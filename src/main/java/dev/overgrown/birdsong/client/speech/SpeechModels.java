package dev.overgrown.birdsong.client.speech;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.birdsong.Birdsong;
import dev.overgrown.birdsong.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SpeechModels {
    private static final String LIST_URL = "https://alphacephei.com/vosk/models/model-list.json";
    private static final String META_FILE = "birdsong-model.json";
    private static final long MAX_DOWNLOAD = 2L << 30;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final Codec<Map<String, String>> META = Codec.unboundedMap(Codec.STRING, Codec.STRING);

    public record Installed(Path path, String name, @Nullable String language) {}

    public record Offer(String name, String language, String languageName, long size, String md5, String url) {
        private static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Offer::name),
            Codec.STRING.fieldOf("lang").forGetter(Offer::language),
            Codec.STRING.optionalFieldOf("lang_text", "").forGetter(Offer::languageName),
            Codec.LONG.optionalFieldOf("size", 0L).forGetter(Offer::size),
            Codec.STRING.optionalFieldOf("md5", "").forGetter(Offer::md5),
            Codec.STRING.fieldOf("url").forGetter(Offer::url)
        ).apply(i, Offer::new));
    }

    public enum Phase { IDLE, LISTING, DOWNLOADING, UNPACKING, DONE, FAILED }

    public record Progress(Phase phase, @Nullable Offer offer, long bytes, @Nullable String error) {
        public int percent() {
            if (offer == null || offer.size() <= 0) return 0;
            return (int) Math.min(100L, bytes * 100L / offer.size());
        }
    }

    private static volatile Progress progress = new Progress(Phase.IDLE, null, 0L, null);

    private SpeechModels() {}

    public static Path directory() {
        return Platform.gameDir().resolve("birdsong").resolve("models");
    }

    private static Path legacyDirectory() {
        return Platform.gameDir().resolve("apoli").resolve("vosk-model");
    }

    public static Progress progress() {
        return progress;
    }

    public static boolean busy() {
        Phase phase = progress.phase();
        return phase == Phase.LISTING || phase == Phase.DOWNLOADING || phase == Phase.UNPACKING;
    }

    public static void acknowledge() {
        Phase phase = progress.phase();
        if (phase == Phase.DONE || phase == Phase.FAILED) progress = new Progress(Phase.IDLE, null, 0L, null);
    }

    public static @Nullable Installed find(String configured, String minecraftLanguage) {
        if (!configured.isBlank()) {
            Path direct = Path.of(configured);
            Path candidate = direct.isAbsolute() ? direct : directory().resolve(configured);
            Path model = modelRoot(candidate);
            if (model != null) return new Installed(model, model.getFileName().toString(), languageOf(model));
            Birdsong.LOGGER.warn("[Birdsong] The configured speech model \"{}\" is not a Vosk model folder.", configured);
        }
        List<Installed> installed = installed();
        if (!installed.isEmpty()) {
            List<String> wanted = candidates(minecraftLanguage);
            for (String code : wanted) {
                for (Installed model : installed) {
                    if (code.equals(model.language())) return model;
                }
            }
            for (String code : wanted) {
                for (Installed model : installed) {
                    if (model.language() != null && model.language().startsWith(code + "-")) return model;
                }
            }
            return installed.get(0);
        }
        Path legacy = modelRoot(legacyDirectory());
        return legacy == null ? null : new Installed(legacy, "apoli/vosk-model", null);
    }

    public static List<Installed> installed() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) return List.of();
        List<Installed> found = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path child : children) {
                if (child.getFileName().toString().startsWith(".")) continue;
                Path model = modelRoot(child);
                if (model != null) found.add(new Installed(model, child.getFileName().toString(), languageOf(child)));
            }
        } catch (IOException e) {
            Birdsong.LOGGER.warn("[Birdsong] Could not list speech models in {}: {}", dir, e.getMessage());
        }
        found.sort(Comparator.comparing(Installed::name));
        return found;
    }

    private static @Nullable Path modelRoot(Path dir) {
        if (!Files.isDirectory(dir)) return null;
        if (isModel(dir)) return dir;
        try (Stream<Path> children = Files.list(dir)) {
            List<Path> folders = children.filter(Files::isDirectory).toList();
            if (folders.size() == 1 && isModel(folders.get(0))) return folders.get(0);
        } catch (IOException ignored) {
        }
        return null;
    }

    private static boolean isModel(Path dir) {
        return Files.isDirectory(dir.resolve("am")) || Files.isDirectory(dir.resolve("conf"));
    }

    private static @Nullable String languageOf(Path model) {
        Path meta = model.resolve(META_FILE);
        if (!Files.isRegularFile(meta)) return null;
        try {
            return new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(meta)))
                .get("language").asString().result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> candidates(String minecraftLanguage) {
        String code = minecraftLanguage == null ? "en-us" : minecraftLanguage.toLowerCase(Locale.ROOT).replace('_', '-');
        int dash = code.indexOf('-');
        String base = dash < 0 ? code : code.substring(0, dash);
        String vosk = switch (base) {
            case "zh" -> "cn";
            case "uk" -> "ua";
            case "vi" -> "vn";
            case "kk" -> "kz";
            default -> base;
        };
        List<String> out = new ArrayList<>(4);
        out.add(code);
        if (!out.contains(vosk)) out.add(vosk);
        if (vosk.equals("en") && !out.contains("en-us")) out.add("en-us");
        return out;
    }

    public static void download(String minecraftLanguage) {
        if (busy()) return;
        progress = new Progress(Phase.LISTING, null, 0L, null);
        Thread worker = new Thread(() -> run(minecraftLanguage), "Birdsong-Model-Download");
        worker.setDaemon(true);
        worker.start();
    }

    private static void run(String minecraftLanguage) {
        Offer offer = null;
        try {
            offer = choose(fetchOffers(), minecraftLanguage);
            progress = new Progress(Phase.DOWNLOADING, offer, 0L, null);
            Path dir = directory();
            Files.createDirectories(dir);
            Path zip = dir.resolve(offer.name() + ".zip.part");
            fetch(offer, zip);
            progress = new Progress(Phase.UNPACKING, offer, offer.size(), null);
            unpack(zip, dir, offer);
            Files.deleteIfExists(zip);
            progress = new Progress(Phase.DONE, offer, offer.size(), null);
            Birdsong.LOGGER.info("[Birdsong] Installed speech model {} in {}.", offer.name(), dir);
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            progress = new Progress(Phase.FAILED, offer, 0L, reason);
            Birdsong.LOGGER.error("[Birdsong] Could not download a speech model", e);
        }
    }

    private static List<Offer> fetchOffers() throws IOException {
        HttpURLConnection connection = open(LIST_URL);
        try (InputStream in = connection.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<Offer> offers = new ArrayList<>();
            for (Dynamic<?> entry : new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(body)).asList(d -> d)) {
                if (!"small".equals(entry.get("type").asString("")) || !"false".equals(entry.get("obsolete").asString("true"))) continue;
                Offer.CODEC.parse(entry).result().ifPresent(offers::add);
            }
            if (offers.isEmpty()) throw new IOException("the model list is empty");
            return offers;
        } finally {
            connection.disconnect();
        }
    }

    private static Offer choose(List<Offer> offers, String minecraftLanguage) throws IOException {
        List<String> wanted = candidates(minecraftLanguage);
        for (String code : wanted) {
            for (Offer offer : offers) {
                if (offer.language().equals(code)) return offer;
            }
        }
        for (String code : wanted) {
            for (Offer offer : offers) {
                if (offer.language().startsWith(code + "-")) return offer;
            }
        }
        for (Offer offer : offers) {
            if (offer.language().equals("en-us")) return offer;
        }
        throw new IOException("no speech model is available for " + minecraftLanguage);
    }

    private static void fetch(Offer offer, Path target) throws Exception {
        if (!offer.url().startsWith("https://")) throw new IOException("refusing a model URL that is not HTTPS");
        if (offer.size() > MAX_DOWNLOAD) throw new IOException("the model is too large");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        HttpURLConnection connection = open(offer.url());
        long total = 0L;
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                md5.update(buffer, 0, read);
                total += read;
                if (total > MAX_DOWNLOAD) throw new IOException("the download is larger than expected");
                progress = new Progress(Phase.DOWNLOADING, offer, total, null);
            }
        } finally {
            connection.disconnect();
        }
        String digest = HexFormat.of().formatHex(md5.digest());
        if (!offer.md5().isBlank() && !offer.md5().equalsIgnoreCase(digest)) {
            Files.deleteIfExists(target);
            throw new IOException("the download was corrupted (checksum mismatch), please try again");
        }
    }

    private static void unpack(Path zip, Path dir, Offer offer) throws IOException {
        Path staging = dir.resolve("." + offer.name() + ".unpack");
        deleteTree(staging);
        Files.createDirectories(staging);
        Path root = staging.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("the model archive has an unsafe path: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path model = modelRoot(staging);
        if (model == null) throw new IOException("the archive did not contain a Vosk model");
        Path target = dir.resolve(offer.name());
        deleteTree(target);
        Files.move(model, target, StandardCopyOption.ATOMIC_MOVE);
        Files.writeString(target.resolve(META_FILE), META.encodeStart(JsonOps.INSTANCE,
            Map.of("language", offer.language(), "name", offer.name())).result()
            .orElseThrow(() -> new IOException("could not write the model description")).toString());
        deleteTree(staging);
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Birdsong/" + Platform.version() + " (Minecraft mod)");
        int status = connection.getResponseCode();
        if (status / 100 != 2) {
            connection.disconnect();
            throw new IOException("the server answered HTTP " + status + " for " + url);
        }
        return connection;
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
