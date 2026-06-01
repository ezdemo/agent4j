package site.sorghum.agent4j.bin.agent;

import java.util.List;

/**
 * 消息准备结果（含是否发生了折叠）—— 从 AgentLoop 提取为独立类型。
 *
 * @param messages       准备好的消息列表
 * @param foldedThisStep 本次是否发生了上下文折叠
 * @author Sorghum
 */
record PreparedMessages(List<ChatMessage> messages, boolean foldedThisStep) {
}
