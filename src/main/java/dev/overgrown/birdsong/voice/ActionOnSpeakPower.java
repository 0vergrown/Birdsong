package dev.overgrown.birdsong.voice;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.action.BiEntityAction;
import dev.overgrown.apoli.codec.LoggedOptionalField;
import dev.overgrown.apoli.action.EntityAction;
import dev.overgrown.apoli.condition.BiEntityCondition;
import dev.overgrown.apoli.power.PowerType;

import java.util.Optional;

public final class ActionOnSpeakPower extends PowerType<ActionOnSpeakPower.Config> {
    public record Config(Optional<EntityAction> actionOnSpeak,
                         Optional<EntityAction> actionOnStopSpeaking,
                         Optional<BiEntityAction> bientityActionOnSpeak,
                         Optional<BiEntityAction> bientityActionOnStopSpeaking,
                         Optional<BiEntityCondition> bientityCondition) {}

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            LoggedOptionalField.of("entity_action", EntityAction.CODEC).forGetter(Config::actionOnSpeak),
            LoggedOptionalField.of("entity_action_stop", EntityAction.CODEC).forGetter(Config::actionOnStopSpeaking),
            LoggedOptionalField.of("bientity_action", BiEntityAction.CODEC).forGetter(Config::bientityActionOnSpeak),
            LoggedOptionalField.of("bientity_action_stop", BiEntityAction.CODEC).forGetter(Config::bientityActionOnStopSpeaking),
            LoggedOptionalField.strict("bientity_condition", BiEntityCondition.CODEC).forGetter(Config::bientityCondition)
        ).apply(i, Config::new));
    }
}
