package site.sorghum.agent4j.bin.agent;

import org.noear.snack4.ONode;

import site.sorghum.agent4j.bin.model.ModelClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文折叠 —— 将旧消息语义摘要，替代机械截断。
 * <p>
 * 参考 Agent4j TS 的 ContextManager.fold()：
 * 保留尾部（~80KB），头部用一次 API 调用压缩为一句总结。
 * </p>
 *
 * @author Sorghum
 */
public class ContextFolding {

    private static final int HEAD_CHARS_LIMIT = 60_000;

    /**
     * 对消息列表执行折叠。如果消息总字符未超阈值则不操作。
     *
     * @param messages  当前全部消息（含 system prompt）
     * @param maxChars  触发折叠的阈值
     * @param keepChars 保留的尾部字符预算
     * @param client    API 客户端
     * @return 折叠后的消息列表，未触发折叠时返回原列表
     */
    public static List<Map<String, Object>> fold(
            List<Map<String, Object>> messages,
            int maxChars, int keepChars,
            ModelClient client) throws IOException {

        int total = estimateChars(messages);
        if (total <= maxChars) return messages;

        // 找到折叠边界：从尾部往前累积，留够 keepChars
        // 确保边界不切在 tool_calls/tool 对中间
        int cum = 0;
        int boundary = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            int sz = estimateChars(messages.get(i));
            if (cum + sz > keepChars) break;
            cum += sz;
            boundary = i;
        }

        // 向后调整边界，避免切在 tool_calls/tool 对中间
        boundary = adjustBoundary(messages, boundary);

        if (boundary <= 0 || boundary >= messages.size()) return messages;

        List<Map<String, Object>> head = new ArrayList<>(messages.subList(0, boundary));
        List<Map<String, Object>> tail = messages.subList(boundary, messages.size());

        // 再次确保 tail 没有孤立的 tool 消息
        tail = ensureTailClean(tail);

        int dropped = head.size();
        if (dropped == 0) return messages;

        String summary = summarize(head, client);
        if (summary == null || summary.trim().isEmpty()) {
            System.err.println("[fold] 摘要失败，跳过折叠");
            return messages;
        }

        Map<String, Object> summaryMsg = new LinkedHashMap<>();
        summaryMsg.put("role", "user");
        summaryMsg.put("content", "[历史上下文折叠——以下 " + dropped + " 条较早消息已被摘要]\n" + summary.trim());

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(summaryMsg);
        result.addAll(tail);

        int before = total;
        int after = estimateChars(result);
        System.err.println("[fold] " + dropped + " 条消息 → " + summary.length() + " 字符摘要（" + before + " → ~" + after + " 字符）");
        return result;
    }

    /**
     * 调整折叠边界，确保不切在 tool_calls 与 tool 消息之间。
     * 如果 boundary 指向 tool 消息，向前退到对应的 assistant（含 tool_calls）。
     */
    private static int adjustBoundary(List<Map<String, Object>> messages, int boundary) {
        if (boundary >= messages.size()) return messages.size() - 1;
        for (int i = boundary; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            if ("tool".equals(m.get("role"))) {
                // boundary 切在 tool 开头 → 向前回退到最近的 assistant
                int back = i;
                while (back > 0) {
                    back--;
                    Map<String, Object> prev = messages.get(back);
                    if ("assistant".equals(prev.get("role")) && prev.containsKey("tool_calls")) {
                        return back; // 从 assistant 开始保留完整对
                    }
                }
            }
        }
        return boundary;
    }

    /**
     * 确保 tail 没有孤立的 tool_calls/tool 对。
     * 如果 assistant 的 tool_calls 数 > 后续 tool 消息数，则从 assistant 中去掉 tool_calls。
     * 工具结果（tool）没有配对时直接丢弃。
     */
    private static List<Map<String, Object>> ensureTailClean(List<Map<String, Object>> tail) {
        List<Map<String, Object>> clean = new ArrayList<>();
        // 当前未配对的 tool_calls 数量
        int pendingToolCount = 0;
        int lastAssistantWithTcIdx = -1; // 跟踪最后一个带 tool_calls 的 assistant 在 clean 中的位置
        for (Map<String, Object> m : tail) {
            String role = (String) m.get("role");
            if ("assistant".equals(role) && m.containsKey("tool_calls")) {
                List<?> tcs = (List<?>) m.get("tool_calls");
                int tcCount = tcs != null ? tcs.size() : 0;
                pendingToolCount += tcCount; // 累加而非覆盖
                lastAssistantWithTcIdx = clean.size(); // 记录即将写入的位置
                clean.add(m);
            } else if ("tool".equals(role) && pendingToolCount > 0) {
                clean.add(m);
                pendingToolCount--;
            } else if ("tool".equals(role)) {
                continue; // 孤立 tool，丢弃
            } else {
                clean.add(m);
            }
        }
        // 如果 tail 末尾还有未配对的 tool_calls，剥离 tool_calls 并删除孤儿 tool 结果
        if (pendingToolCount > 0 && lastAssistantWithTcIdx >= 0) {
            // 剥离最后一个 assistant 的 tool_calls
            Map<String, Object> m = clean.get(lastAssistantWithTcIdx);
            Map<String, Object> stripped = new LinkedHashMap<>();
            stripped.put("role", "assistant");
            if (m.containsKey("content")) stripped.put("content", m.get("content"));
            if (m.containsKey("reasoning_content")) stripped.put("reasoning_content", m.get("reasoning_content"));
            clean.set(lastAssistantWithTcIdx, stripped);
            // 同步删除该 assistant 之后所有孤儿 tool 消息
            for (int i = clean.size() - 1; i > lastAssistantWithTcIdx; i--) {
                if ("tool".equals(clean.get(i).get("role"))) {
                    clean.remove(i);
                }
            }
        }
        return clean;
    }

    /**
     * 对 head 消息执行语义摘要，通过一次 API 调用将旧消息压缩为一段中文描述。
     * 截断后清理 tool_calls/tool 对，避免摘要器看到工具调用细节。
     *
     * @param head   要摘要的消息子集
     * @param client API 客户端
     * @return 摘要文本，失败时返回 null
     */
    private static String summarize(List<Map<String, Object>> head, ModelClient client) throws IOException {
        String sp = "You are a conversation history summarizer for a coding agent. "
                + "Output ONE concise Chinese paragraph that preserves:\n"
                + "- 用户的整体目标\n"
                + "- 已完成的决策和结论\n"
                + "- 已检查或修改的文件\n"
                + "- 仍相关的工具结果\n"
                + "- 未完成的待办\n"
                + "不要逐轮记录，不要 markdown 标题，纯中文段落。";

        // 先截断到字符限制
        List<Map<String, Object>> trimmed = truncateForSummary(head, HEAD_CHARS_LIMIT);
        // 截断后再清理 tool_calls/tool 对（顺序重要：先截断后清理）
        trimmed = sanitizeMessagesForSummary(trimmed);

        List<Map<String, Object>> msgs = new ArrayList<>();
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", sp);
        msgs.add(sys);
        msgs.addAll(trimmed);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", "请用一段中文总结上面的对话。这段摘要将替代原始对话以释放上下文。");
        msgs.add(user);

        ONode resp = client.chat(msgs, null);
        String content = resp.get("content").getString();
        if (content != null && !content.isEmpty()) return content;

        String reasoning = resp.get("reasoning_content").getString();
        if (reasoning != null && !reasoning.isEmpty()) return reasoning;

        return null;
    }

    /**
     * 清理消息列表用于摘要 API：
     * - 保留 tool_calls 结构（维持 API 所要求的配对约束）
     * - 移除孤立的 tool role 消息（无对应 tool_calls 的）
     * - 截断过大的 tool 结果
     */
    private static List<Map<String, Object>> sanitizeMessagesForSummary(List<Map<String, Object>> msgs) {
        List<Map<String, Object>> result = new ArrayList<>();
        int pendingToolCount = 0;
        for (Map<String, Object> m : msgs) {
            String role = (String) m.get("role");
            if ("assistant".equals(role) && m.containsKey("tool_calls")) {
                // 保留 tool_calls 以维持 API 配对约束；摘要不需要参数细节，但保留结构
                result.add(m);
                List<?> tcs = (List<?>) m.get("tool_calls");
                pendingToolCount += tcs != null ? tcs.size() : 0;
            } else if ("tool".equals(role) && pendingToolCount > 0) {
                // 保留 tool 结果（包含工具的实质输出），但截断过长内容
                String content = (String) m.getOrDefault("content", "");
                if (content.length() > 4000) {
                    Map<String, Object> truncated = new LinkedHashMap<>(m);
                    truncated.put("content", content.substring(0, 4000)
                            + "\n\n[… truncated for summary …]");
                    result.add(truncated);
                } else {
                    result.add(m);
                }
                pendingToolCount--;
            } else if ("tool".equals(role)) {
                continue; // 孤立 tool，丢弃
            } else {
                result.add(m);
            }
        }
        // 末尾未配对的 tool_calls → 剥离最后一个 assistant 的 tool_calls
        // （仅当 tool 结果缺失时此分支触发，历史中很少出现）
        if (pendingToolCount > 0) {
            for (int i = result.size() - 1; i >= 0; i--) {
                Map<String, Object> m = result.get(i);
                if ("assistant".equals(m.get("role")) && m.containsKey("tool_calls")) {
                    Map<String, Object> stripped = new LinkedHashMap<>();
                    stripped.put("role", "assistant");
                    if (m.containsKey("content")) stripped.put("content", m.get("content"));
                    if (m.containsKey("reasoning_content")) stripped.put("reasoning_content", m.get("reasoning_content"));
                    result.set(i, stripped);
                    // 同步删除该 assistant 之后所有孤儿 tool 消息
                    for (int j = result.size() - 1; j > i; j--) {
                        if ("tool".equals(result.get(j).get("role"))) {
                            result.remove(j);
                        }
                    }
                    break;
                }
            }
        }
        return result;
    }

    /** 截断到字符限制，保留尾部。 */
    private static List<Map<String, Object>> truncateForSummary(List<Map<String, Object>> msgs, int limit) {
        int total = estimateChars(msgs);
        if (total <= limit) return msgs;

        int cum = 0;
        int start = msgs.size();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (cum + estimateChars(msgs.get(i)) > limit) break;
            cum += estimateChars(msgs.get(i));
            start = i;
        }
        if (start >= msgs.size()) return new ArrayList<>();
        return new ArrayList<>(msgs.subList(Math.max(0, start), msgs.size()));
    }

    // ==================== 字符估算 ====================

    /**
     * 估算消息列表的总字符数，用于判断是否触发折叠。
     * 注意：这不包含 tools JSON 的大小，实际请求体会更大。
     */
    public static int estimateChars(List<Map<String, Object>> messages) {
        int total = 0;
        for (Map<String, Object> m : messages) total += estimateChars(m);
        return total;
    }

    /** 估算单条消息的字符数（role + content + tool_calls + reasoning_content）。 */
    public static int estimateChars(Map<String, Object> m) {
        int n = 0;
        if (m.containsKey("role")) n += m.get("role").toString().length();
        if (m.containsKey("content")) n += m.get("content").toString().length();
        if (m.containsKey("tool_calls")) n += m.get("tool_calls").toString().length();
        if (m.containsKey("reasoning_content")) n += m.get("reasoning_content").toString().length();
        return n;
    }
}
