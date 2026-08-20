package site.sorghum.loopra.integration.cutin.plugin.session;

/**
  * 在单个 cutin 回合结束后提交会话状态的宿主切片。
 */
public interface LoopraSessionHost {

    void beginCutinLoop();

    void endCutinLoop();

    /**
      * 在新的 cutin 回合开始时执行一次。人工审批重入时用户消息为空，
      * 宿主不能把它当作新的用户回合。
     */
    void beforeTurn(String userMessage);

    void afterTurn();
}
