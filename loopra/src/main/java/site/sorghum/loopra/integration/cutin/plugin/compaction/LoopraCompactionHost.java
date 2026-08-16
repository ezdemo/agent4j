package site.sorghum.loopra.integration.cutin.plugin.compaction;

import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.loopra.bin.agent.model.PreparedMessages;

/**
  * 持有模型请求前 Loopra 折叠策略的宿主切片。
 */
public interface LoopraCompactionHost {

    PreparedMessages prepareCutinMessages(DefaultLoopContext context, int step);
}
