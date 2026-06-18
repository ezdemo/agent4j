package site.sorghum.agent4j.tool.solon;

import org.noear.solon.ai.chat.tool.FunctionTool;

import java.util.Collection;
import java.util.Collections;


public interface SolonToTools {

    default Collection<FunctionTool> getSolonTools() {
        return Collections.emptyList();
    }

    default String getSystemPrompt() {
        return null;
    }
}
