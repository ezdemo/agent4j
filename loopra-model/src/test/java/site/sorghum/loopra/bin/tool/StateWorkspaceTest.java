package site.sorghum.loopra.bin.tool;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 状态工作区（stateWorkspace）链路测试 —— 工作树隔离模式的核心解耦：
 * 工具文件根（worktree）与会话身份根（主工作区）分离，且默认行为与旧版完全一致。
 */
class StateWorkspaceTest {

    @Test
    void registryDefaultsStateWorkspaceToWorkspace() {
        ToolRegistry registry = new ToolRegistry();
        Path workspace = Path.of("/tmp/main-repo");
        registry.setRefreshContext(workspace, "url", "key", null);
        assertEquals(workspace, registry.getWorkspace());
        assertEquals(workspace, registry.getStateWorkspace(), "未指定状态工作区时应回退为工作区本身");
    }

    @Test
    void registrySupportsSeparateStateWorkspace() {
        ToolRegistry registry = new ToolRegistry();
        Path worktree = Path.of("/home/u/.loopra/worktree/abc-sess1");
        Path main = Path.of("/tmp/main-repo");
        registry.setRefreshContext(worktree, main, "url", "key", null);
        assertEquals(worktree, registry.getWorkspace());
        assertEquals(main, registry.getStateWorkspace());
    }

    @Test
    void copyPreservesStateWorkspace() {
        ToolRegistry registry = new ToolRegistry();
        Path worktree = Path.of("/home/u/.loopra/worktree/abc-sess1");
        Path main = Path.of("/tmp/main-repo");
        registry.setRefreshContext(worktree, main, "url", "key", null);
        ToolRegistry copy = registry.copy();
        assertEquals(worktree, copy.getWorkspace());
        assertEquals(main, copy.getStateWorkspace());
    }

    @Test
    void toolContextStateRootFallsBackToRootDir() {
        ToolContext ctx = new ToolContext(Map.of(), "/tmp/main-repo", "sess-1");
        assertEquals(Path.of("/tmp/main-repo"), ctx.getStateRootDir(),
                "三参构造（旧调用方）下状态根应回退为根目录");
    }

    @Test
    void toolContextCarriesSeparateStateRoot() {
        ToolContext ctx = new ToolContext(Map.of(), "/home/u/.loopra/worktree/abc-sess1",
                "/tmp/main-repo", "sess-1");
        assertEquals(Path.of("/home/u/.loopra/worktree/abc-sess1"), ctx.getRootDir());
        assertEquals(Path.of("/tmp/main-repo"), ctx.getStateRootDir());
    }

    @Test
    void initializerWiresStateWorkspaceIntoRegistry() {
        ToolSystemInitializer.Result result = ToolSystemInitializer.initialize(
                Path.of("/tmp/worktree"), Path.of("/tmp/main"), "url", "key", null, null, "prompt");
        assertEquals(Path.of("/tmp/worktree"), result.toolRegistry.getWorkspace());
        assertEquals(Path.of("/tmp/main"), result.toolRegistry.getStateWorkspace());
    }
}
