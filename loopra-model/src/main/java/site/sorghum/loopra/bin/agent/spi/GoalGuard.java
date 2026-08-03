package site.sorghum.loopra.bin.agent.spi;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Goal 守卫 SPI —— AgentLoop 借此读取 Goal 状态、注入 Goal 指令并拦截过早结束。
 * <p>
 * 内核不感知 Goal 的具体模型与持久化实现；由上层模块（loopra-harness）提供实现。
 * 未设置实现（{@code null}）时，等价于"当前无开放 Goal"，守卫全部放行。
 * </p>
 *
 * @author Sorghum
 */
public interface GoalGuard {

    /**
     * 读取指定会话当前开放的 Goal 只读视图。
     *
     * @param workspace 工作区根目录（Goal 快照按工作区隔离）
     * @param sessionId 会话 ID
     * @return 开放中的 Goal 视图；无开放 Goal 返回 {@code null}
     * @throws IOException 读取持久化 Goal 失败时抛出，由 AgentLoop 降级为提示文案
     */
    GoalView openGoal(Path workspace, String sessionId) throws IOException;

    /**
     * 生成追加到 system prompt 的 Goal 指令片段。
     *
     * @param goal 当前开放 Goal 视图；为 {@code null} 时返回空串
     * @return Goal 指令片段（无 Goal 时为空串）
     */
    String instruction(GoalView goal);

    /**
     * Goal 只读视图 —— 内核仅依赖这些字段做守卫判断与提示拼装。
     */
    interface GoalView {
        /** 是否仍需要代理继续推进（未关闭且非暂停/阻塞终态）。 */
        boolean requiresAgentWork();

        /** Goal 标题（简短目标描述）。 */
        String title();

        /** 进度文本，如 "3/8 (37%)"。 */
        String progressText();
    }
}
