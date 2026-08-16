package site.sorghum.loopra.integration.cutin.plugin.recovery;

/**
  * 持有 Loopra 上下文超限恢复策略的宿主切片。
 */
public interface LoopraErrorRecoveryHost {

    int maxContextRecoveries();

    boolean compactAfterContextOverflow(int recoveryAttempt);
}
