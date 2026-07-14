package site.sorghum.agent4j.bin.builtin;

import org.junit.jupiter.api.Test;
import site.sorghum.agent4j.bin.agent.core.AgentLoop;
import site.sorghum.agent4j.bin.tool.ToolRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskToolHitlInheritanceTest {

    @Test
    void subAgentInheritsEveryParentHitlModeWithoutCollapsingAuto() {
        ToolRegistry registry = new ToolRegistry();

        for (String mode : new String[]{"free", "approval", "auto"}) {
            AgentLoop parent = new AgentLoop(null, registry, null, mode);

            assertEquals(mode, TaskTool.resolveInheritedHitlMode(parent));
        }
    }
}
