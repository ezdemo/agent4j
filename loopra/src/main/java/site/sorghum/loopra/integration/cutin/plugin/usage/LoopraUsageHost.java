package site.sorghum.loopra.integration.cutin.plugin.usage;

import site.sorghum.cutin.core.context.Usage;

/**
  * 接收 cutin 循环按模型上报的用量增量的宿主切片。
 */
public interface LoopraUsageHost {

    void reportCutinUsage(Usage usage);
}
