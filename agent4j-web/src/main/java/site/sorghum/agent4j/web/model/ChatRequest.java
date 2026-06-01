package site.sorghum.agent4j.web.model;

/**
 * 聊天请求体。
 *
 * @author Sorghum
 */
public class ChatRequest {

    /**
     * 用户消息内容
     */
    public String message;

    /**
     * 是否为流式模式（默认 false）
     */
    public boolean stream;

    /**
     * 工作区 hash（用于多工作区隔离，由 /api/workspaces 返回）
     */
    public String workspaceHash;

    /**
     * 会话名称（用于多会话隔离）
     */
    public String sessionName;
}
