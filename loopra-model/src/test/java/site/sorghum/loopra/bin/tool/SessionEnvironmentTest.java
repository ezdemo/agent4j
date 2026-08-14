package site.sorghum.loopra.bin.tool;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 状态项目（stateWorkspace）链路测试 —— 隔离分支模式的核心解耦：
 * 工具文件根（worktree）与会话身份根（主项目）分离，且默认行为与旧版完全一致。
 */
class SessionEnvironmentTest {

    @Test
    void registryDefaultsStateRootToProjectRoot() {
        ToolRegistry registry = new ToolRegistry();
        Path workspace = Path.of("/tmp/main-repo");
        registry.setRefreshContext(workspace, "url", "key", null);
        assertEquals(workspace, registry.getEnvironment().executionRoot());
        assertEquals(workspace, registry.getEnvironment().stateRoot());
    }

    @Test
    void registrySupportsSeparateStateRoot() {
        ToolRegistry registry = new ToolRegistry();
        Path worktree = Path.of("/home/u/.loopra/worktree/abc-sess1");
        Path main = Path.of("/tmp/main-repo");
        registry.setRefreshContext(worktree, main, "url", "key", null);
        assertEquals(worktree, registry.getEnvironment().executionRoot());
        assertEquals(main, registry.getEnvironment().stateRoot());
    }

    @Test
    void copyPreservesStateRoot() {
        ToolRegistry registry = new ToolRegistry();
        Path worktree = Path.of("/home/u/.loopra/worktree/abc-sess1");
        Path main = Path.of("/tmp/main-repo");
        registry.setRefreshContext(worktree, main, "url", "key", null);
        ToolRegistry copy = registry.copy();
        assertEquals(worktree, copy.getEnvironment().executionRoot());
        assertEquals(main, copy.getEnvironment().stateRoot());
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
    void initializerWiresStateRootIntoRegistry() {
        ToolSystemInitializer.Result result = ToolSystemInitializer.initialize(
                Path.of("/tmp/worktree"), Path.of("/tmp/main"), "url", "key", null, null, "prompt");
        assertEquals(Path.of("/tmp/worktree"), result.toolRegistry.getEnvironment().executionRoot());
        assertEquals(Path.of("/tmp/main"), result.toolRegistry.getEnvironment().stateRoot());
    }
}
