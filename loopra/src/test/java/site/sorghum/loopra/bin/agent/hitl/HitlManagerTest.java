package site.sorghum.loopra.bin.agent.hitl;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitlManagerTest {

    @Test
    void freeModeNeverRequiresApproval() {
        assertFalse(new HitlManager("free").requiresHITL(toolCalls("bash"), List.of()));
    }

    @Test
    void approvalModePreservesExemptTools() {
        HitlManager manager = new HitlManager("approval");

        assertFalse(manager.requiresHITL(toolCalls("finish"), List.of()));
        assertTrue(manager.requiresHITL(toolCalls("bash"), List.of()));
    }

    @Test
    void autoModeOnlyRequiresApprovalForWhitelistMisses() {
        HitlManager manager = new HitlManager("auto");

        assertFalse(manager.requiresHITL(toolCalls("workspace_read"), List.of("workspace_*")));
        assertTrue(manager.requiresHITL(toolCalls("bash"), List.of("workspace_*")));
    }

    private static ONode toolCalls(String name) {
        ONode calls = new ONode().asArray();
        calls.addNew().then(call -> {
            call.set("id", "call-1");
            call.getOrNew("function").then(function -> {
                function.set("name", name);
                function.set("arguments", "{}");
            });
        });
        return calls;
    }
}
