package site.sorghum.agent4j.tool.solon;

import site.sorghum.agent4j.tool.AgentTool;

import java.util.List;


public interface SolonToTools {

    List<AgentTool> getTools();

    default String getSystemPrompt() {
        return null;
    }
}
