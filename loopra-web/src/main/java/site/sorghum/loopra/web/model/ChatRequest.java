package site.sorghum.loopra.web.model;

import lombok.Data;

import java.util.List;

/**
 * 聊天请求体。
 *
 * @author Sorghum
 */
@Data
public class ChatRequest {

    /**
     * 用户消息内容
     */
    private String message;

    /**
     * 是否为流式模式（默认 false）
     */
    private boolean stream;

    /**
     * 本次流式请求 ID，用于在 Agent 尚未创建时精确取消后台任务。
     */
    private String requestId;

    /**
     * 工作区 hash（用于多工作区隔离，由 /api/workspaces 返回）
     */
    private String workspaceHash;

    /**
     * 会话名称（用于多会话隔离）
     */
    private String sessionName;

    /**
     * 本次对话使用的模型名称。未指定时使用全局默认模型。
     */
    private String model;

    /**
     * 本次对话使用的模型渠道 ID。未指定时使用全局默认渠道。
     */
    private String modelChannelId;

    /**
     * 本次对话使用的思考强度。未指定时保留当前会话的设置。
     */
    private String reasoningEffort;

    /**
     * 本次对话是否启用快速模式（OpenAI service_tier=fast）。
     * 未指定时保留当前会话的设置。
     */
    private Boolean fastMode;

    /**
     * 工作树隔离模式开关（可选）。
     * 指定时在 Agent 创建前持久化到会话元数据：开启后该会话的 AI 文件操作
     * 落在 ~/.loopra/worktree/ 下的 git worktree 中，合并回主工作区需显式触发。
     */
    private Boolean worktreeMode;

    /**
     * 工作树合并模式（可选）：manual / ai-auto / ai-auto-approve。
     * 指定时持久化到会话元数据。
     */
    private String mergeMode;

    /**
     * 结构化 Web 操作（当前支持 execute_plan：批准并执行待审查计划）。
     */
    private String action;

    /**
     * 图片列表（可选）。
     * 支持公开 URL 和 Base64 Data URI 两种格式。
     * 与 message 配合生成多模态消息。
     */
    private List<String> images;
}
