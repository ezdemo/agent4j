package site.sorghum.loopra.integration.cutin.plugin.toolbatch;

/**
  * 工具批次超时/取消切入点的载荷。
 */
public record LoopraToolBatchEvent(
    String toolName,
    int timeoutSeconds,
    boolean subAgent,
    int cancelledCount
) {
}
