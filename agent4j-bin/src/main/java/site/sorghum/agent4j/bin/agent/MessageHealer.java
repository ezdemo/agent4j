package site.sorghum.agent4j.bin.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息修复器 —— 发送前修复消息列表。
 * <p>
 * 三项修复单次遍历完成：
 * 1. shrinkOversizedToolResults — 按 token 截断过大 tool 结果
 * 2. fixToolCallPairing — 修复不完整 tool_calls/tool 对
 * 3. stampMissingReasoning — 为 thinking 模型补全 reasoning_content
 * </p>
 *
 * @author Sorghum
 */
public class MessageHealer {

    private static final int MAX_TOOL_TOKENS = 8000;

    /**
     * 对消息列表执行全部修复，单次遍历（不创建临时列表）。
     * 三项修复：
     * 1. 截断过大的 tool 结果（超过 8000 tokens）
     * 2. 修复 tool_calls/tool 配对（丢弃孤立的 tool 和未配对的 tool_calls）
     * 3. 为推理模型补全 reasoning_content 字段
     *
     * @param messages       原始消息列表
     * @param isThinkingMode 是否为推理模型（需要 reasoning_content）
     * @return 修复后的消息列表
     */
    public static List<Map<String, Object>> heal(List<Map<String, Object>> messages,
                                                  boolean isThinkingMode) {
        List<Map<String, Object>> out = new ArrayList<>();
        int pendingToolCount = 0;
        int lastAssistantWithTcIdx = -1; // 跟踪最后一个带 tool_calls 的 assistant 在 out 中的位置

        for (Map<String, Object> m : messages) {
            String role = (String) m.get("role");

            // 1. shrink oversized tool results + ensure tool_call_id
            Map<String, Object> msg = m;
            if ("tool".equals(role)) {
                // 防御：缺少 tool_call_id 时补一个占位符
                if (!msg.containsKey("tool_call_id") || msg.get("tool_call_id") == null) {
                    msg = new LinkedHashMap<>(m);
                    msg.put("tool_call_id", "healed_" + System.currentTimeMillis());
                }
                String content = (String) m.getOrDefault("content", "");
                int estimatedTokens = content.length() / 2;
                if (estimatedTokens > MAX_TOOL_TOKENS) {
                    int maxChars = MAX_TOOL_TOKENS * 2;
                    if (content.length() > maxChars) {
                        // 如果上面已经 copy 过就别再 copy
                        if (msg == m) msg = new LinkedHashMap<>(m);
                        String truncated = content.substring(0, Math.min(content.length(), maxChars))
                                + "\n\n[… truncated " + (content.length() - maxChars)
                                + " chars (~" + (estimatedTokens - MAX_TOOL_TOKENS) + " tokens) …]";
                        msg.put("content", truncated);
                    }
                }
            }

            // 2. fix tool_calls/tool pairing + 清除 name 为 null 的 tool_call
            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tcs = (List<Map<String, Object>>) msg.get("tool_calls");
                if (tcs != null) {
                    // 过滤 name 为 null 的 tool_call（历史损坏 / SSE 截断残留）
                    List<Map<String, Object>> cleaned = new ArrayList<>();
                    for (Map<String, Object> tc : tcs) {
                        Object nm = tc.get("name");
                        if (nm != null && (!(nm instanceof String) || !((String) nm).isEmpty())) {
                            cleaned.add(tc);
                        }
                    }
                    if (cleaned.size() != tcs.size()) {
                        msg = new LinkedHashMap<>(msg);
                        msg.put("tool_calls", cleaned);
                        tcs = cleaned;
                    }
                }
                pendingToolCount += tcs != null ? tcs.size() : 0;
                lastAssistantWithTcIdx = out.size();
            } else if ("tool".equals(role)) {
                if (pendingToolCount > 0) {
                    pendingToolCount--;
                } else {
                    continue; // 丢弃孤立 tool
                }
            }

            // 3. stamp missing reasoning_content
            if (isThinkingMode && "assistant".equals(role) && !msg.containsKey("reasoning_content")) {
                Map<String, Object> stamped = new LinkedHashMap<>(msg);
                stamped.put("reasoning_content", "");
                out.add(stamped);
            } else {
                out.add(msg);
            }
        }

        // 末尾未配对的 tool_calls → 剥离 tool_calls 并删除孤儿 tool 结果
        if (pendingToolCount > 0 && lastAssistantWithTcIdx >= 0) {
            // 剥离最后一个 assistant 的 tool_calls
            Map<String, Object> m = out.get(lastAssistantWithTcIdx);
            Map<String, Object> stripped = new LinkedHashMap<>();
            stripped.put("role", "assistant");
            if (m.containsKey("content")) stripped.put("content", m.get("content"));
            if (m.containsKey("reasoning_content"))
                stripped.put("reasoning_content", m.get("reasoning_content"));
            out.set(lastAssistantWithTcIdx, stripped);
            // 同步删除该 assistant 之后所有孤儿 tool 消息
            for (int i = out.size() - 1; i > lastAssistantWithTcIdx; i--) {
                if ("tool".equals(out.get(i).get("role"))) {
                    out.remove(i);
                }
            }
        }

        return out;
    }
}
