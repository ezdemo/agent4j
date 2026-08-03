package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.core.AgentLoop;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubAgentToolHitlInheritanceTest {

    @Test
    void subAgentInheritsEveryParentHitlModeWithoutCollapsingAuto() {
        ToolRegistry registry = new ToolRegistry();

        for (String mode : new String[]{"free", "approval", "auto"}) {
            AgentLoop parent = new AgentLoop(null, registry, null, mode);

            assertEquals(mode, SubAgentTool.resolveInheritedHitlMode(parent));
        }
    }
}
