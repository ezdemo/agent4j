package site.sorghum.loopra.bin.schedule;

/**
 * 定时任务执行器接口 —— 由上层（web/tui）实现，将定时消息投递到目标会话的 Agent。
 *
 * @author Sorghum
 */
@FunctionalInterface
public interface TaskExecutor {

    /**
     * 执行定时任务。
     *
     * @param workspacePath 项目路径
     * @param sessionName   目标会话名称
     * @param message       要发送的消息
     * @return 执行结果（Agent 回复内容）
     * @throws Exception 执行失败时抛出
     */
    String execute(String workspacePath, String sessionName, String message) throws Exception;
}
