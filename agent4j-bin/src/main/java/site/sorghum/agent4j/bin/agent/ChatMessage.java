package site.sorghum.agent4j.bin.agent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.snack4.annotation.ONodeAttr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息 —— 替代 Map&lt;String, Object&gt; 表示消息的强类型封装。
 * <p>
 * 统一表示 OpenAI 兼容 API 的四种消息角色：system / user / assistant / tool。
 * </p>
 * <p>
 * {@link #content} 支持两种模式：
 * <ul>
 *   <li>纯文本模式 —— {@code content} 为普通字符串</li>
 *   <li>多模态模式 —— {@link #contentParts} 非空时，{@code content} 被忽略，
 *       序列化为 JSON 数组 {@code [{"type":"text",...},{"type":"image_url",...}]}</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Data
public class ChatMessage {

    private final String role;

    @ONodeAttr(name = "content")
    private String content;

    /**
     * 多模态内容段（图片 + 文本）。
     * 非空时优先于 {@link #content} 序列化。
     */
    private List<ContentPart> contentParts;

    @ONodeAttr(name = "tool_calls")
    private List<ToolCallEntry> toolCalls;

    @ONodeAttr(name = "tool_call_id")
    private String toolCallId;

    @ONodeAttr(name = "reasoning_content")
    private String reasoningContent;

    /**
     * 快照检查点 ID（仅 user 消息有效）。
     * 非空时表示该消息发送前工作区已保存快照，可用于撤回 AI 修改。
     */
    @ONodeAttr(name = "snapshot_id")
    private String snapshotId;

    /**
     * 消息时间戳（Unix 毫秒），用于前端渲染消息时间。
     */
    @ONodeAttr(name = "timestamp")
    private Long timestamp;

    // ==================== 内容段模型 ====================

    public static ChatMessage user(String content) {
        ChatMessage msg = new ChatMessage("user");
        msg.content = content;
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    // ==================== 工厂方法 ====================

    public static ChatMessage system(String content) {
        ChatMessage msg = new ChatMessage("system");
        msg.content = content;
        return msg;
    }

    /**
     * 创建多模态用户消息（文本 + 图片）。
     *
     * @param text   文本内容
     * @param images 图片 URL 列表（公开 URL 或 Base64 Data URI）
     * @return 用户消息
     */
    public static ChatMessage userWithImages(String text, List<String> images) {
        ChatMessage msg = new ChatMessage("user");
        msg.contentParts = new ArrayList<>();
        for (String img : images) {
            msg.contentParts.add(ContentPart.imageUrl(img));
        }
        if (text != null && !text.isEmpty()) {
            msg.contentParts.add(ContentPart.text(text));
        }
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    @SuppressWarnings("unchecked")
    public static ChatMessage fromMap(Map<String, Object> m) {
        String role = String.valueOf(m.getOrDefault("role", "user"));
        ChatMessage msg = new ChatMessage(role);
        Object content = m.get("content");
        if (content instanceof List) {
            // 多模态内容段：[{"type":"text",...},{"type":"image_url",...}]
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content;
            msg.contentParts = new ArrayList<>();
            for (Map<String, Object> part : parts) {
                String type = String.valueOf(part.get("type"));
                ContentPart cp = new ContentPart();
                cp.setType(type);
                if ("text".equals(type)) {
                    Object textVal = part.get("text");
                    cp.setText(textVal != null ? textVal.toString() : null);
                } else if ("image_url".equals(type)) {
                    Map<String, Object> iuMap = (Map<String, Object>) part.get("image_url");
                    if (iuMap != null) {
                        ContentPart.ImageUrl iu = new ContentPart.ImageUrl();
                        Object urlVal = iuMap.get("url");
                        iu.setUrl(urlVal != null ? urlVal.toString() : null);
                        Object detailVal = iuMap.get("detail");
                        iu.setDetail(detailVal != null ? detailVal.toString() : null);
                        cp.setImageUrl(iu);
                    }
                }
                msg.contentParts.add(cp);
            }
        } else {
            msg.content = content != null ? content.toString() : null;
        }
        Object reasoning = m.get("reasoning_content");
        msg.reasoningContent = reasoning != null ? reasoning.toString() : null;
        Object toolCallId = m.get("tool_call_id");
        msg.toolCallId = toolCallId != null ? toolCallId.toString() : null;
        Object snapshotId = m.get("snapshot_id");
        msg.snapshotId = snapshotId != null ? snapshotId.toString() : null;
        Object timestamp = m.get("timestamp");
        if (timestamp instanceof Number) {
            msg.timestamp = ((Number) timestamp).longValue();
        } else if (timestamp != null) {
            try {
                msg.timestamp = Long.parseLong(timestamp.toString());
            } catch (NumberFormatException e) {
                // 忽略无效的时间戳
            }
        }
        if (m.containsKey("tool_calls")) {
            List<Map<String, Object>> tcMaps = (List<Map<String, Object>>) m.get("tool_calls");
            if (tcMaps != null) {
                msg.toolCalls = new ArrayList<>();
                for (Map<String, Object> tc : tcMaps) {
                    String tcId = String.valueOf(tc.getOrDefault("id", "unknown"));
                    String tcName;
                    Object tcArgsObj;
                    Object funcObj = tc.get("function");
                    if (funcObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> func = (Map<String, Object>) funcObj;
                        tcName = String.valueOf(func.getOrDefault("name", "unknown"));
                        tcArgsObj = func.get("arguments");
                    } else {
                        tcName = String.valueOf(tc.getOrDefault("name", "unknown"));
                        tcArgsObj = tc.get("arguments");
                    }
                    if (tcArgsObj == null) tcArgsObj = "{}";
                    if (tcArgsObj instanceof String tcArgsStr) {
                        try {
                            tcArgsObj = ONode.ofJson(tcArgsStr).toBean();
                        } catch (Exception e) {
                            log.debug("工具调用参数 JSON 解析失败，保留原始字符串: {}", e.getMessage());
                        }
                    }
                    msg.toolCalls.add(new ToolCallEntry(tcId, tcName, tcArgsObj));
                }
            }
        }
        return msg;
    }

    public static ChatMessage assistant(String content, List<ToolCallEntry> toolCalls, String reasoningContent) {
        ChatMessage msg = new ChatMessage("assistant");
        msg.content = content;
        msg.toolCalls = toolCalls;
        msg.reasoningContent = reasoningContent;
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    public static ChatMessage tool(String toolCallId, String content) {
        ChatMessage msg = new ChatMessage("tool");
        msg.toolCallId = toolCallId;
        msg.content = content != null ? content : "(empty)";
        msg.timestamp = System.currentTimeMillis();
        return msg;
    }

    // ==================== 便捷判断 ====================

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        if (contentParts != null && !contentParts.isEmpty()) {
            List<Map<String, Object>> partsList = new ArrayList<>();
            for (ContentPart part : contentParts) {
                Map<String, Object> partMap = new LinkedHashMap<>();
                partMap.put("type", part.getType());
                if ("text".equals(part.getType())) {
                    partMap.put("text", part.getText());
                } else if ("image_url".equals(part.getType())) {
                    Map<String, Object> urlMap = new LinkedHashMap<>();
                    ContentPart.ImageUrl iu = part.getImageUrl();
                    if (iu != null) {
                        urlMap.put("url", iu.getUrl());
                        if (iu.getDetail() != null) urlMap.put("detail", iu.getDetail());
                    }
                    partMap.put("image_url", urlMap);
                }
                partsList.add(partMap);
            }
            m.put("content", partsList);
        } else if (content != null) {
            m.put("content", content);
        }
        if (toolCallId != null) m.put("tool_call_id", toolCallId);
        if (reasoningContent != null) m.put("reasoning_content", reasoningContent);
        if (snapshotId != null) m.put("snapshot_id", snapshotId);
        if (timestamp != null) m.put("timestamp", timestamp);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> tcMaps = new ArrayList<>();
            for (ToolCallEntry tc : toolCalls) {
                tcMaps.add(tc.toMap());
            }
            m.put("tool_calls", tcMaps);
        }
        return m;
    }

    public boolean isSystem() {
        return "system".equals(role);
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }

    public boolean isTool() {
        return "tool".equals(role);
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    public boolean hasReasoningContent() {
        return reasoningContent != null && !reasoningContent.isEmpty();
    }

    public boolean hasToolCallId() {
        return toolCallId != null && !toolCallId.isEmpty();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 创建当前消息的浅拷贝（用于 MessageHealer 的复制后修改）。
     * toolCalls 列表是独立副本，但 ToolCallEntry 本身不可变。
     * snapshotId 也一并复制，避免回滚场景下快照 ID 丢失。
     */
    public ChatMessage copy() {
        ChatMessage copy = new ChatMessage(this.role);
        copy.content = this.content;
        if (this.contentParts != null) {
            copy.contentParts = new ArrayList<>(this.contentParts);
        }
        if (this.toolCalls != null) {
            copy.toolCalls = new ArrayList<>(this.toolCalls);
        }
        copy.toolCallId = this.toolCallId;
        copy.reasoningContent = this.reasoningContent;
        copy.snapshotId = this.snapshotId;
        copy.timestamp = this.timestamp;
        return copy;
    }

    // ==================== 反序列化 ====================

    // ==================== 序列化 ====================

    /**
     * OpenAI 多模态消息中的一个内容段。
     * 支持 text 和 image_url 两种类型。
     */
    @lombok.Data
    public static class ContentPart {
        /**
         * 类型: "text" 或 "image_url"
         */
        private String type;
        /**
         * text 类型时的文本内容
         */
        private String text;
        /**
         * image_url 类型时的图片信息
         */
        private ImageUrl imageUrl;

        public static ContentPart text(String text) {
            ContentPart part = new ContentPart();
            part.setType("text");
            part.setText(text);
            return part;
        }

        public static ContentPart imageUrl(String url) {
            return imageUrl(url, null);
        }

        public static ContentPart imageUrl(String url, String detail) {
            ContentPart part = new ContentPart();
            part.setType("image_url");
            ImageUrl iu = new ImageUrl();
            iu.setUrl(url);
            iu.setDetail(detail);
            part.setImageUrl(iu);
            return part;
        }

        @lombok.Data
        public static class ImageUrl {
            /**
             * 图片 URL，可以是：
             * - 公开的 HTTP/HTTPS 地址
             * - Base64 Data URI: {@code data:image/jpeg;base64,...}
             */
            private String url;
            /**
             * 图片细节级别："auto" / "low" / "high"（可选）
             */
            private String detail;
        }
    }
}
