package dev.overgrown.birdsong;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.alias.AliasingOptions;
import dev.overgrown.apoli.condition.ConditionTypes;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.birdsong.speech.ActionOnSpeechPower;
import dev.overgrown.birdsong.voice.ActionOnReplyPower;
import dev.overgrown.birdsong.voice.ActionOnSpeakPower;
import dev.overgrown.birdsong.voice.ModifySpeakingRangePower;
import dev.overgrown.birdsong.voice.VoiceDisabledCondition;
import dev.overgrown.birdsong.voice.VoiceExpressions;
import dev.overgrown.birdsong.voice.VoiceListenersCondition;
import dev.overgrown.birdsong.voice.VoiceLoudnessCondition;
import dev.overgrown.birdsong.voice.VoiceSpeakingCondition;
import dev.overgrown.birdsong.voice.VoiceWhisperingCondition;

public final class BirdsongTypes {
    private BirdsongTypes() {}

    public static void register() {
        ConditionTypes.ENTITY.register(BirdsongIds.VOICE_SPEAKING, new VoiceSpeakingCondition(), legacy("voice_speaking"));
        ConditionTypes.ENTITY.register(BirdsongIds.VOICE_DISABLED, new VoiceDisabledCondition(),
            AliasingOptions.builder()
                .addTypeAlias(Apoli.id("voice_disabled"))
                .addTypeAlias(Apoli.id("voice_muted"))
                .addTypeAlias(Birdsong.id("voice_muted"))
                .build());
        ConditionTypes.ENTITY.register(BirdsongIds.VOICE_LOUDNESS, new VoiceLoudnessCondition(), legacy("voice_loudness"));
        ConditionTypes.ENTITY.register(BirdsongIds.VOICE_LISTENERS, new VoiceListenersCondition(), legacy("voice_listeners"));
        ConditionTypes.ENTITY.register(BirdsongIds.VOICE_WHISPERING, new VoiceWhisperingCondition(), legacy("voice_whispering"));

        PowerTypeRegistry.register(BirdsongIds.ACTION_ON_SPEAK, new ActionOnSpeakPower(), legacy("action_on_speak"));
        PowerTypeRegistry.register(BirdsongIds.ACTION_ON_REPLY, new ActionOnReplyPower(), legacy("action_on_reply"));
        PowerTypeRegistry.register(BirdsongIds.ACTION_ON_SPEECH, new ActionOnSpeechPower(), legacy("action_on_speech"));
        PowerTypeRegistry.register(BirdsongIds.MODIFY_SPEAKING_RANGE, new ModifySpeakingRangePower(), legacy("modify_speaking_range"));

        VoiceExpressions.register();
    }

    private static AliasingOptions legacy(String path) {
        return AliasingOptions.builder().addTypeAlias(Apoli.id(path)).build();
    }
}
