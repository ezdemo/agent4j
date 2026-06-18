package site.sorghum.agent4j.tool.solon;

import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.agent4j.tool.AgentTool;

import java.util.Collection;
import java.util.Collections;
import java.util.List;


public interface SolonToTools {

    List<AgentTool> getTools();

    default Collection<FunctionTool> getSolonTools() {
        return Collections.emptyList();
    }

    default String getSystemPrompt() {
        return null;
    }
}
