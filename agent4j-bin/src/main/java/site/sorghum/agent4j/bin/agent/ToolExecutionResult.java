package site.sorghum.agent4j.bin.agent;

import java.util.List;

/**
 * 工具并行执行结果 —— 从 AgentLoop 提取为独立类型。
 *
 * @param tcList        工具调用条目列表
 * @param toolResults   工具执行结果消息列表
 * @param anySuppressed 是否有任何调用被风暴断路器抑制
 * @author Sorghum
 */
record ToolExecutionResult(List<ToolCallEntry> tcList,
                           List<ChatMessage> toolResults,
                           boolean anySuppressed) {}
