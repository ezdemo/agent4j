package site.sorghum.cutin.core.model;

import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * 一次模型调用的请求：模型 id、消息列表、工具定义与扩展选项。
 *
 * <p>构造时会做不可变拷贝，保证拦截链与 Provider 拿到的数据不会被外部修改。</p>
 */
public record ModelCallRequest(
    String modelId,
    List<Message> messages,
    List<ToolDefinition> tools,
    Map<String, Object> options
) {

    /** 记录构造校验：对消息、工具与选项做不可变拷贝。 */
    public ModelCallRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
