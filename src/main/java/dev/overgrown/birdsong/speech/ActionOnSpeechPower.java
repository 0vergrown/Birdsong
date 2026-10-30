package dev.overgrown.birdsong.speech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.data.message.TranslationKeyResolver;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.birdsong.BirdsongIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ActionOnSpeechPower extends PowerType<ActionOnSpeechPower.Config> {
    private static final long COOLDOWN_MS = 250L;
    private static final float DEFAULT_TOLERANCE = 0.2F;
    private static final Map<UUID, Map<ResourceLocation, Long>> RECENT = new HashMap<>();

    private static final Codec<Pattern> PATTERN = Codec.STRING.comapFlatMap(raw -> {
        try {
            return DataResult.success(Pattern.compile(TranslationKeyResolver.expandPattern(raw)));
        } catch (PatternSyntaxException e) {
            return DataResult.error(() -> "Invalid regex \"" + raw + "\": " + e.getDescription());
        }
    }, Pattern::pattern);

    public record Config(Optional<EntityAction> entityAction, Optional<String> message, List<String> messages,
                         Optional<Pattern> regex, Optional<String> language, float tolerance, PhraseMatcher phrases) {
        Config(Optional<EntityAction> entityAction, Optional<String> message, List<String> messages,
               Optional<Pattern> regex, Optional<String> language, float tolerance) {
            this(entityAction, message, messages, regex, language, Math.max(0.0F, Math.min(1.0F, tolerance)),
                PhraseMatcher.of(message, messages));
        }
    }

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            LoggedOptionalField.of("entity_action", EntityAction.CODEC).forGetter(Config::entityAction),
            Codec.STRING.optionalFieldOf("message").forGetter(Config::message),
            Codec.STRING.listOf().optionalFieldOf("messages", List.of()).forGetter(Config::messages),
            PATTERN.optionalFieldOf("regex").forGetter(Config::regex),
            Codec.STRING.optionalFieldOf("language").forGetter(Config::language),
            Codec.FLOAT.optionalFieldOf("tolerance", DEFAULT_TOLERANCE).forGetter(Config::tolerance)
        ).apply(i, Config::new));
    }

    public static @Nullable String matchedTrigger(Config config, PhraseMatcher.Transcript transcript,
                                                  @Nullable String language, boolean complete) {
        if (config.language().isPresent() && !languageMatches(config.language().get(), language)) return null;
        boolean anything = true;
        if (!config.phrases().isEmpty()) {
            anything = false;
            String phrase = config.phrases().match(transcript, config.tolerance(), complete);
            if (phrase != null) return phrase;
        }
        if (config.regex().isPresent()) {
            anything = false;
            if (config.regex().get().matcher(transcript.raw()).find()) return config.regex().get().pattern();
        }
        return anything ? transcript.raw() : null;
    }

    private static boolean languageMatches(String want, @Nullable String got) {
        if (got == null || got.isEmpty()) return false;
        String w = want.toLowerCase(Locale.ROOT).replace('_', '-');
        String g = got.toLowerCase(Locale.ROOT).replace('_', '-');
        return g.equals(w) || g.startsWith(w + "-") || w.startsWith(g + "-");
    }

    public static int fire(ServerPlayer player, String text, String language) {
        PhraseMatcher.Transcript transcript = PhraseMatcher.transcript(text);
        long now = System.currentTimeMillis();
        int[] fired = new int[1];
        PowerLookup.forEachEntry(player, BirdsongIds.ACTION_ON_SPEECH, Config.class, (powerId, cfg) -> {
            if (matchedTrigger(cfg, transcript, language, true) == null || !debounce(player.getUUID(), powerId, now)) return;
            fired[0]++;
            cfg.entityAction().ifPresent(action -> action.run(new EntityCtx(player, player.serverLevel())));
        });
        return fired[0];
    }

    public static void fireTrigger(ServerPlayer player, ResourceLocation powerId) {
        PowerContainer container = PowerContainer.of(player);
        if (container == null || !container.hasPower(powerId) || container.isSuppressed(powerId)) return;
        Power power = ApoliPowers.get(powerId);
        if (power == null || !(power.config() instanceof Config cfg)) return;
        if (!BirdsongIds.ACTION_ON_SPEECH.equals(PowerTypeRegistry.resolveId(power.typeId()))) return;
        EntityCtx ctx = new EntityCtx(player, player.serverLevel());
        if (power.condition().isPresent() && !power.condition().get().test(ctx)) return;
        if (!debounce(player.getUUID(), powerId, System.currentTimeMillis())) return;
        cfg.entityAction().ifPresent(action -> action.run(ctx));
    }

    private static boolean debounce(UUID player, ResourceLocation powerId, long now) {
        Map<ResourceLocation, Long> recent = RECENT.computeIfAbsent(player, ignored -> new HashMap<>(4));
        Long last = recent.get(powerId);
        if (last != null && now - last < COOLDOWN_MS) return false;
        recent.put(powerId, now);
        if (recent.size() > 8) recent.values().removeIf(time -> now - time > COOLDOWN_MS);
        return true;
    }

    public static void forget(UUID player) {
        RECENT.remove(player);
    }

    public static void clear() {
        RECENT.clear();
    }
}
