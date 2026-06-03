package site.sorghum.agent4j.bin.agent;

import java.util.*;

/**
 * 消息修复器（整流器）—— 发送前修复消息列表。
 * <p>
 * 四项修复单次遍历完成：
 * 1. fixToolCallPairing — 修复不完整 tool_calls/tool 对
 * 2. stampMissingReasoning — 为 thinking 模型补全 reasoning_content
 * 3. dedupToolCallId — 检测并修复重复的 tool_call_id（兜底防护）
 * 4. dedupToolCallArrayId — 检测并修复 tool_calls 数组内的重复 id
 * </p>
 *
 * @author Sorghum
 */
public class MessageHealer {

    /**
     * 整流结果：修复后的消息列表 + 是否发生了修改。
     */
    public record HealResult(List<ChatMessage> messages, boolean changed) {}

    /**
     * 内部修复结果（Map 层，避免重复转换）。
     */
    private record HealInternalResult(List<Map<String, Object>> messages, boolean changed) {}

    /**
     * 对消息列表执行全部修复，单次遍历（不创建临时列表）。
     * 修复：
     * 1. 修复 tool_calls/tool 配对（丢弃孤立的 tool 和未配对的 tool_calls）
     * 2. 为推理模型补全 reasoning_content 字段
     * 3. 检测并修复重复的 tool_call_id（兜底，避免 API 400）
     * 4. 检测并修复 tool_calls 数组内的重复 id
     *
     * @param messages       原始消息列表
     * @param isThinkingMode 是否为推理模型（需要 reasoning_content）
     * @return 整流结果（修复后的消息列表 + 是否发生修改）
     */
    public static HealResult heal(List<ChatMessage> messages,
                                         boolean isThinkingMode) {
        List<Map<String, Object>> mapMessages = new ArrayList<>();
        for (ChatMessage m : messages) mapMessages.add(m.toMap());
        HealInternalResult internal = healInternal(mapMessages, isThinkingMode);
        List<ChatMessage> out = new ArrayList<>();
        for (Map<String, Object> m : internal.messages()) out.add(ChatMessage.fromMap(m));
        return new HealResult(out, internal.changed());
    }

    /**
     * 内部修复逻辑（在 Map 层操作，避免 ChatMessage 反复创建）。
     */
    private static HealInternalResult healInternal(List<Map<String, Object>> messages,
                                                          boolean isThinkingMode) {
        List<Map<String, Object>> out = new ArrayList<>();
        int pendingToolCount = 0;
        int lastAssistantWithTcIdx = -1;
        boolean changed = false;

        // 跟踪所有已出现的 tool_call_id（用于重复检测兜底）
        Set<String> seenToolCallIds = new HashSet<>();

        for (Map<String, Object> m : messages) {
            String role = (String) m.get("role");

            // ======== 1. ensure tool_call_id + 重复 tool_call_id 检测 ========
            Map<String, Object> msg = m;
            if ("tool".equals(role)) {
                String rawTcId = (String) msg.get("tool_call_id");
                boolean hasNoId = !msg.containsKey("tool_call_id") || rawTcId == null;

                if (hasNoId) {
                    // 防御：缺少 tool_call_id 时补一个占位符
                    msg = new LinkedHashMap<>(m);
                    msg.put("tool_call_id", "healed_" + System.currentTimeMillis());
                    changed = true;
                    rawTcId = (String) msg.get("tool_call_id");
                }

                // ★ 重复 tool_call_id 检测：同一批消息中不允许两个 tool 结果有相同 ID
                if (rawTcId != null && !seenToolCallIds.add(rawTcId)) {
                    int dedupIdx = 0;
                    String newId;
                    do {
                        newId = rawTcId + "_dedup_" + (dedupIdx++);
                    } while (!seenToolCallIds.add(newId));
                    if (msg == m) {
                        msg = new LinkedHashMap<>(m);
                    }
                    msg.put("tool_call_id", newId);
                    changed = true;
                }

                // ★ 截断超大 tool 结果（~8000 tokens 以上），防止 token 溢出
                Object contentObj = msg.get("content");
                if (contentObj instanceof String toolContent && toolContent.length() > 16_000) {
                    int keepLen = Math.min(12_000, toolContent.length() / 2);
                    String truncated = toolContent.substring(0, keepLen)
                            + "\n\n[truncated: 原文 " + toolContent.length() + " 字符，已截断至 " + keepLen + " 字符]";
                    if (msg == m) {
                        msg = new LinkedHashMap<>(m);
                    }
                    msg.put("content", truncated);
                    changed = true;
                }
            }

            // ======== 1.5 修复空 assistant 消息（用户中断/历史损坏） ========
            if ("assistant".equals(role)
                    && !msg.containsKey("tool_calls")
                    && !msg.containsKey("reasoning_content")) {
                Object cv = msg.get("content");
                boolean noContent = !msg.containsKey("content") || cv == null
                        || (cv instanceof String && ((String) cv).isEmpty());
                if (noContent) {
                    msg = new LinkedHashMap<>(msg);
                    msg.put("content", "");
                    changed = true;
                }
            }

            // ======== 2. fix tool_calls/tool pairing + 清除无效 name + 重复 id 检测 ========
            if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tcs = (List<Map<String, Object>>) msg.get("tool_calls");
                if (tcs != null) {
                    Set<String> seenTcIds = new HashSet<>();
                    List<Map<String, Object>> cleaned = new ArrayList<>();
                    boolean tcListModified = false;
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
                        if (tcName == null || tcName.isEmpty()) {
                            changed = true;
                            tcListModified = true;
                            continue; // 丢弃无效 tool_call
                        }

                        // ★ 重复 id 检测：tool_calls 数组中不允许重复 id
                        String tcId = (String) tc.get("id");
                        if (tcId != null && !seenTcIds.add(tcId)) {
                            int dedupIdx = 0;
                            String newId;
                            do {
                                newId = tcId + "_dedup_" + (dedupIdx++);
                            } while (!seenTcIds.add(newId));
                            Map<String, Object> fixedTc = new LinkedHashMap<>(tc);
                            fixedTc.put("id", newId);
                            cleaned.add(fixedTc);
                            changed = true;
                            tcListModified = true;
                        } else {
                            cleaned.add(tc);
                        }
                    }
                    // 注意：cleaned.size() != tcs.size() 只在丢弃 null-name 时成立，
                    // 去重 id 时数量不变，因此需额外检查 tcListModified 标志
                    if (tcListModified || cleaned.size() != tcs.size()) {
                        if (msg == m) {
                            msg = new LinkedHashMap<>(m);
                        }
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

            // ======== 3. stamp missing reasoning_content ========
            if (isThinkingMode && "assistant".equals(role) && !msg.containsKey("reasoning_content")) {
                Map<String, Object> stamped = new LinkedHashMap<>(msg);
                stamped.put("reasoning_content", "");
                out.add(stamped);
                // reasoning_content 补空不算"修改"（这是正常的模型适配）
            } else {
                out.add(msg);
            }
        }

        // ======== 末尾未配对的 tool_calls → 剥离 ========
        if (pendingToolCount > 0 && lastAssistantWithTcIdx >= 0) {
            Map<String, Object> m = out.get(lastAssistantWithTcIdx);
            Map<String, Object> stripped = new LinkedHashMap<>();
            stripped.put("role", "assistant");
            boolean hasContent = m.containsKey("content") && m.get("content") != null
                    && !(m.get("content") instanceof String && ((String) m.get("content")).isEmpty());
            boolean hasReasoning = m.containsKey("reasoning_content") && m.get("reasoning_content") != null
                    && !(m.get("reasoning_content") instanceof String && ((String) m.get("reasoning_content")).isEmpty());
            if (hasContent) stripped.put("content", m.get("content"));
            if (hasReasoning) stripped.put("reasoning_content", m.get("reasoning_content"));
            if (!hasContent && !hasReasoning) {
                stripped.put("content", "");
            }
            out.set(lastAssistantWithTcIdx, stripped);
            for (int i = out.size() - 1; i > lastAssistantWithTcIdx; i--) {
                if ("tool".equals(out.get(i).get("role"))) {
                    out.remove(i);
                }
            }
            changed = true;
        }

        return new HealInternalResult(out, changed);
    }
}
