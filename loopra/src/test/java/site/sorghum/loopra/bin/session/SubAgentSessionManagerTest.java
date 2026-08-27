package site.sorghum.loopra.bin.session;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.core.SubAgent;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 子代理会话管理器单测：注册/查找/取消判定/运行互斥/LRU 淘汰。
 */
class SubAgentSessionManagerTest {

    private static SubAgent sub() {
        return new SubAgent(TestLoopraProvider.builder()
                .model("test-model")
                .stream(request -> TestLoopraProvider.contentStream("ok"))
                .build(), new ToolRegistry(), "system");
    }

    @Test
    void registerAndFind() {
        SubAgentSessionManager manager = new SubAgentSessionManager();
        SubAgent agent = sub();
        manager.register("sub-1", agent);

        assertSame(agent, manager.find("sub-1"));
        assertTrue(manager.isResumable("sub-1"));
        assertNull(manager.find("sub-2"));
        assertFalse(manager.isResumable("sub-2"));
        assertFalse(manager.isResumable(null));
    }

    @Test
    void abortedSubAgentIsNotResumable() {
        SubAgentSessionManager manager = new SubAgentSessionManager();
        SubAgent agent = sub();
        agent.abort();
        manager.register("sub-1", agent);

        assertFalse(manager.isResumable("sub-1"));
        assertFalse(manager.tryBeginRun("sub-1"));
    }

    @Test
    void tryBeginRunIsMutuallyExclusive() {
        SubAgentSessionManager manager = new SubAgentSessionManager();
        manager.register("sub-1", sub());

        assertTrue(manager.tryBeginRun("sub-1"));
        // 同一会话运行中：再次续跑被拒绝
        assertFalse(manager.tryBeginRun("sub-1"));
        manager.endRun("sub-1");
        assertTrue(manager.tryBeginRun("sub-1"));
        manager.endRun("sub-1");
        // 未登记会话不可续跑
        assertFalse(manager.tryBeginRun("sub-unknown"));
    }

    @Test
    void evictsLeastRecentlyUsedBeyondLimit() {
        SubAgentSessionManager manager = new SubAgentSessionManager();
        for (int i = 0; i < 20; i++) {
            manager.register("sub-" + i, sub());
        }
        assertEquals(20, manager.size());

        // 访问 sub-0 使其成为最近使用；再注册触发淘汰最久未使用（sub-1）
        manager.find("sub-0");
        manager.register("sub-new", sub());

        assertEquals(20, manager.size());
        assertNull(manager.find("sub-1"));
        assertNotNull(manager.find("sub-0"));
        assertNotNull(manager.find("sub-new"));
        // 新会话可继续对话
        assertTrue(manager.isResumable("sub-new"));
    }
}
