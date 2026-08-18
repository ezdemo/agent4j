package site.sorghum.loopra.integration.cutin.plugin.usage;

public interface LoopraTokenSpeedHost {

    void emitTokenSpeed(long completionTokens, double tokensPerSecond, double avgTokensPerSecond, boolean done);

    default void emitTokenSpeed(long completionTokens, double tokensPerSecond, boolean done) {
        emitTokenSpeed(completionTokens, tokensPerSecond, tokensPerSecond, done);
    }
}
