package site.sorghum.agent4j.web.model;

/**
 * 聊天请求体。
 *
 * @author Sorghum
 */
public class ChatRequest {

    /** 用户消息内容 */
    public String message;

    /** 是否为流式模式（默认 false） */
    public boolean stream;
}
