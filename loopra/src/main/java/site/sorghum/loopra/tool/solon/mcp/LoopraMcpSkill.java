package site.sorghum.loopra.tool.solon.mcp;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.talents.gateway.McpGatewayTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.loopra.tool.SolonToTools;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 将 Solon MCP 网关能力暴露为 Loopra 可调用的工具集合。 */
@Component
public class LoopraMcpSkill extends McpGatewayTalent implements SolonToTools {
    public LoopraMcpSkill() {

    }

    /**
     * 将网关中的 MCP FunctionTool 包装为带外部工具标记的实例。
     *
     * <p>调用方有时直接使用 {@link #getTools(Prompt)}，因此不能只在
     * {@link #getSolonTools()} 中包装。</p>
     */
    @Override
    public Collection<FunctionTool> getTools(Prompt prompt) {
        Collection<FunctionTool> tools = super.getTools(prompt);
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .filter(Objects::nonNull)
                .map(tool -> tool instanceof McpFunctionTool ? tool : new McpFunctionTool(tool))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools(null);
    }

    @Override
    public String getSystemPrompt() {
        return "\n" + getInstruction(null);
    }
}
