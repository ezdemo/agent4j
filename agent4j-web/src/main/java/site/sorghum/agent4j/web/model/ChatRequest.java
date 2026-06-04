package site.sorghum.agent4j.web.model;

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

    /**
     * 图片列表（可选）。
     * 支持公开 URL 和 Base64 Data URI 两种格式。
     * 与 message 配合生成多模态消息。
     */
    public List<String> images;
}
