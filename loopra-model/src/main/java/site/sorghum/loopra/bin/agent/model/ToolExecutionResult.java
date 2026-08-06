package site.sorghum.loopra.bin.agent.model;

import java.util.List;

/**
 * 工具并行执行结果。
 *
 * @param tcList        有效的工具调用条目列表（与 toolResults 严格一一对应）
 * @param toolResults   每个工具调用的执行结果
 * @param anySuppressed 是否有工具调用被风暴断路器抑制
 */
public record ToolExecutionResult(List<ToolCallEntry> tcList,
                                  List<LoopraChatMessage> toolResults,
                                  List<FileChange> fileChanges,
                                  boolean anySuppressed) {
}
