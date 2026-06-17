package site.sorghum.agent4j.bin.agent;

import java.util.List;

/**
 * 消息准备结果 —— 包含修复、折叠后的消息列表以及是否发生了折叠。
 *
 * @param messages       修复/折叠后的消息列表
 * @param foldedThisStep 当前步骤是否触发了上下文折叠
 */
public record PreparedMessages(List<ChatMessage> messages, boolean foldedThisStep) {
}
