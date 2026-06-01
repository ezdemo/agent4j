package site.sorghum.agent4j.bin.agent;

/**
 * 选项记录 —— 从 AgentOutput 接口提取为独立类型。
 *
 * @param value 发送的消息
 * @param title 展示文本
 * @author Sorghum
 */
public record ChoiceOption(String value, String title) {
}
