package site.sorghum.agent4j.bin.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息修复器 —— 发送前修复消息列表。
 * <p>
 * 三项修复单次遍历完成：
 * 1. fixToolCallPairing — 修复不完整 tool_calls/tool 对
 * 2. stampMissingReasoning — 为 thinking 模型补全 reasoning_content
 * </p>
 *
 * @author Sorghum
 */
public class MessageHealer {

    /**
     * 对消息列表执行全部修复，单次遍历（不创建临时列表）。
     * 修复：
     * 1. 修复 tool_calls/tool 配对（丢弃孤立的 tool 和未配对的 tool_calls）
     * 2. 为推理模型补全 reasoning_content 字段
     *
     * @param messages       原始消息列表
     * @param isThinkingMode 是否为推理模型（需要 reasoning_content）
     * @return 修复后的消息列表
     */
    public static List<ChatMessage> heal(List<ChatMessage> messages,
                                         boolean isThinkingMode) {
        List<Map<String, Object>> mapMessages = new ArrayList<>();
        for (ChatMessage m : messages) mapMessages.add(m.toMap());
        List<Map<String, Object>> result = healInternal(mapMessages, isThinkingMode);
        List<ChatMessage> out = new ArrayList<>();
        for (Map<String, Object> m : result) out.add(ChatMessage.fromMap(m));
        return out;
    }

    private static List<Map<String, Object>> healInternal(List<Map<String, Object>> messages,
                                                          boolean isThinkingMode) {
        List<Map<String, Object>> out = new ArrayList<>();
        int pendingToolCount = 0;
        int lastAssistantWithTcIdx = -1; // 跟踪最后一个带 tool_calls 的 assistant 在 out 中的位置

        for (Map<String, Object> m : messages) {
            String role = (String) m.get("role");

            // 1. ensure tool_call_id
            Map<String, Object> msg = m;
            if ("tool".equals(role)) {
                // 防御：缺少 tool_call_id 时补一个占位符
                if (!msg.containsKey("tool_call_id") || msg.get("tool_call_id") == null) {
                    msg = new LinkedHashMap<>(m);
                    msg.put("tool_call_id", "healed_" + System.currentTimeMillis());
                }
            }

            // 1.5 修复空 assistant 消息（用户中断/历史损坏）
            // assistant 消息必须至少包含 content、tool_calls 或 reasoning_content 之一
            if ("assistant".equals(role)
                    && !msg.containsKey("content")
                    && !msg.containsKey("tool_calls")
                    && !msg.containsKey("reasoning_content")) {
                msg = new LinkedHashMap<>(msg);
                msg.put("content", "");
            }

            // 2. fix tool_calls/tool pairing + 清除 name 为 null 的 tool_call
            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tcs = (List<Map<String, Object>>) msg.get("tool_calls");
                if (tcs != null) {
                    // 过滤 name 为 null 的 tool_call（历史损坏 / SSE 截断残留）
                    // OpenAI 格式: name 在 function.name 下; 兼容顶层 name
                    List<Map<String, Object>> cleaned = new ArrayList<>();
                    for (Map<String, Object> tc : tcs) {
                        String tcName = null;
                        // 优先从 function.name 取（OpenAI 格式）
                        Object func = tc.get("function");
                        if (func instanceof Map) {
                            Object fnName = ((Map<?, ?>) func).get("name");
                            if (fnName instanceof String) tcName = (String) fnName;
                        }
                        // 回退到顶层 name
                        if (tcName == null) {
                            Object topName = tc.get("name");
                            if (topName instanceof String) tcName = (String) topName;
                        }
                        if (tcName != null && !tcName.isEmpty()) {
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
