package site.sorghum.loopra.integration.cutin.plugin.session;

/**
  * 在单个 cutin 回合结束后提交会话状态的宿主切片。
 */
public interface LoopraSessionHost {

    void beginCutinLoop();

    void endCutinLoop();

    /**
      * 在用户消息清洗完成后（preflight-sanitize 节点之后）执行一次，
      * 传入清洗后的消息文本。HITL 人工审批重入不经过 sanitize 节点，
      * 因此不会重复触发 —— 重入不属于新的用户回合。
      */
    void beforeTurn(String userMessage);

    void afterTurn();
}
