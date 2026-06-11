package site.sorghum.agent4j.tool.interact;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.List;

@Component
public class FinishSkill implements SolonToTools {
    @Override
    public List<AgentTool> getTools() {
        return List.of(new FinishTool());
    }

    @Override
    public String getSystemPrompt() {
        return """
                AI 认为对话可以结束并准备给出最终回答时调用finish工具退出推理循环
                """;
    }
}
