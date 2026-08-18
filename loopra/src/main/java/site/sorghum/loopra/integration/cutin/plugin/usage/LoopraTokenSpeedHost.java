package site.sorghum.loopra.integration.cutin.plugin.usage;

public interface LoopraTokenSpeedHost {

    void emitTokenSpeed(long completionTokens, double tokensPerSecond, boolean done);
}
