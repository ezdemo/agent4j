package site.sorghum.loopra.integration.cutin.plugin.lifecycle;

/**
  * 持有单次推理回合重置与收尾逻辑的宿主切片。
 */
public interface LoopraLifecycleHost {

    void beginCutinLoop();

    void endCutinLoop();
}
