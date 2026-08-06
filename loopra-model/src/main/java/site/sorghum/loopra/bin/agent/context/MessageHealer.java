package site.sorghum.loopra.bin.agent.context;

import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 消息修复器（整流器）—— 发送前修复消息列表。
 * <p>
 * 四项修复单次遍历完成，直接操作 {@link LoopraChatMessage} 对象，
 * 避免 ChatMessage ↔ Map 的序列化/反序列化往返。
 * <ol>
 *   <li><b>fixToolCallPairing</b> — 修复不完整 tool_calls/tool 对</li>
 *   <li><b>stampMissingReasoning</b> — 为 thinking 模型补全 reasoning_content</li>
 *   <li><b>dedupToolCallId</b> — 检测并修复重复的 tool_call_id（兜底防护）</li>
 *   <li><b>dedupToolCallArrayId</b> — 检测并修复 tool_calls 数组内的重复 id</li>
 * </ol>
 * </p>
 *
 * @author Sorghum
 */
public class MessageHealer {

    /**
     * 整流结果：修复后的消息列表 + 是否发生了修改。
     */
    public record HealResult(List<LoopraChatMessage> messages, boolean changed) {}

    /**
     * 对消息列表执行全部修复，单次遍历（不创建临时列表）。
     * 直接操作 {@link LoopraChatMessage} 对象，消除 ChatMessage ↔ Map 序列化往返。
     * 修复：
     * 1. 修复 tool_calls/tool 配对（丢弃孤立的 tool 和未配对的 tool_calls）
     * 2. 为推理模型补全 reasoning_content 字段
     * 3. 检测并修复重复的 tool_call_id（兜底，避免 API 400）
     * 4. 检测并修复 tool_calls 数组内的重复 id
     *
     * @param messages 原始消息列表
     * @return 整流结果（修复后的消息列表 + 是否发生修改）
     */
    public static HealResult heal(List<LoopraChatMessage> messages) {
        List<LoopraChatMessage> out = new ArrayList<>();
        int pendingToolCount = 0;
        int lastAssistantWithTcIdx = -1;
        boolean changed = false;

        // 跟踪所有已出现的 tool_call_id（用于重复检测兜底）
        Set<String> seenToolCallIds = new HashSet<>();

        for (LoopraChatMessage msg : messages) {
            String role = msg.getRole();

            // ======== 1. ensure tool_call_id + 重复 tool_call_id 检测 ========
            LoopraChatMessage current = msg;
            if ("tool".equals(role)) {
                String rawTcId = current.getToolCallId();
                boolean hasNoId = rawTcId == null;

                if (hasNoId) {
                    // 防御：缺少 tool_call_id 时补一个占位符
                    current = msg.copy();
                    current.setToolCallId("healed_" + System.currentTimeMillis());
                    changed = true;
                    rawTcId = current.getToolCallId();
                }

                // ★ 重复 tool_call_id 检测：同一批消息中不允许两个 tool 结果有相同 ID
                if (rawTcId != null && !seenToolCallIds.add(rawTcId)) {
                    int dedupIdx = 0;
                    String newId;
                    do {
                        newId = rawTcId + "_dedup_" + (dedupIdx++);
                    } while (!seenToolCallIds.add(newId));
                    if (current == msg) {
                        current = msg.copy();
                    }
                    current.setToolCallId(newId);
                    changed = true;
                }

                // ★ 截断超大 tool 结果（~8000 tokens 以上），防止 token 溢出
                String toolContent = current.getContent();
                if (toolContent != null && toolContent.length() > 16_000) {
                    int keepLen = Math.min(12_000, toolContent.length() / 2);
                    String truncated = toolContent.substring(0, keepLen)
                            + "\n\n[truncated: 原文 " + toolContent.length() + " 字符，已截断至 " + keepLen + " 字符]";
                    if (current == msg) {
                        current = msg.copy();
                    }
                    current.setContent(truncated);
                    changed = true;
                }
            }

            // ======== 1.5 修复空 assistant 消息（用户中断/历史损坏） ========
            if ("assistant".equals(role)
                    && !current.hasToolCalls()
                    && current.getReasoningContent() == null) {
                boolean noContent = !current.hasContent();
                if (noContent) {
                    current = msg.copy();
                    current.setContent("");
                    changed = true;
                }
            }

            // ======== 2. fix tool_calls/tool pairing + 清除无效 name + 重复 id 检测 ========
            if ("assistant".equals(role) && current.hasToolCalls()) {
                List<ToolCallEntry> tcs = current.getToolCalls();
                if (tcs != null) {
                    Set<String> seenTcIds = new HashSet<>();
                    List<ToolCallEntry> cleaned = new ArrayList<>();
                    boolean tcListModified = false;
                    for (ToolCallEntry tc : tcs) {
                        String tcName = tc.name();
                        if (tcName == null || tcName.isEmpty()) {
                            changed = true;
                            tcListModified = true;
                            continue; // 丢弃无效 tool_call
                        }
                        Object arguments = tc.arguments();
                        if (arguments instanceof String argStr){
                            // 是不是JSON格式 不是则改为空JSON串
                            try {
                                ONode.ofJson(argStr).toJson();
                            }catch (Exception e){
                                changed = true;
                                tcListModified = true;
                                cleaned.add(new ToolCallEntry(tc.id(), tcName, "{}", tc.responseReasoning()));
                                continue;
                            }
                        }

                        // ★ 重复 id 检测：tool_calls 数组中不允许重复 id
                        String tcId = tc.id();
                        if (tcId != null && !seenTcIds.add(tcId)) {
                            int dedupIdx = 0;
                            String newId;
                            do {
                                newId = tcId + "_dedup_" + (dedupIdx++);
                            } while (!seenTcIds.add(newId));
                            cleaned.add(new ToolCallEntry(newId, tcName, tc.arguments(), tc.responseReasoning()));
                            changed = true;
                            tcListModified = true;
                        } else {
                            cleaned.add(tc);
                        }
                    }
                    if (tcListModified || cleaned.size() != tcs.size()) {
                        if (current == msg) {
                            current = msg.copy();
                        }
                        current.setToolCalls(cleaned);
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
            if ( "assistant".equals(role) && current.getReasoningContent() == null) {
                LoopraChatMessage stamped = current.copy();
                stamped.setReasoningContent("");
                out.add(stamped);
                // reasoning_content 补空不算"修改"（这是正常的模型适配）
            } else {
                out.add(current);
            }
        }

        // ======== 末尾未配对的 tool_calls → 剥离 ========
        if (pendingToolCount > 0 && lastAssistantWithTcIdx >= 0) {
            LoopraChatMessage m = out.get(lastAssistantWithTcIdx);
            boolean hasContent = m.hasContent();
            boolean hasReasoning = m.getReasoningContent() != null;
            LoopraChatMessage stripped = new LoopraChatMessage("assistant");
            if (hasContent) stripped.setContent(m.getContent());
            if (hasReasoning) stripped.setReasoningContent(m.getReasoningContent());
            if (!hasContent && !hasReasoning) {
                stripped.setContent("");
            }
            out.set(lastAssistantWithTcIdx, stripped);
            for (int i = out.size() - 1; i > lastAssistantWithTcIdx; i--) {
                if ("tool".equals(out.get(i).getRole())) {
                    out.remove(i);
                }
            }
            changed = true;
        }

        return new HealResult(out, changed);
    }
}
