package dev.overgrown.birdsong.voice;

import dev.overgrown.apoli.data.expr.ExprVars;

public final class VoiceExpressions {
    private VoiceExpressions() {}

    public static void register() {
        ExprVars.register("voice_loudness", (e, c, l, v) -> e == null ? 0 : VoiceState.loudness(e.getUUID()));
        ExprVars.register("voice_loudness_normalized", (e, c, l, v) -> e == null ? 0 : VoiceState.loudness(e.getUUID()) / 100.0);
        ExprVars.register("voice_speaking", (e, c, l, v) -> e != null && VoiceState.isSpeaking(e.getUUID()) ? 1 : 0);
        ExprVars.register("voice_whispering", (e, c, l, v) -> e != null && VoiceState.isWhispering(e.getUUID()) ? 1 : 0);
        ExprVars.register("voice_disabled", (e, c, l, v) ->
            e != null && (VoiceState.isDisabled(e.getUUID()) || VoiceState.isDisconnected(e.getUUID())) ? 1 : 0);
    }
}
