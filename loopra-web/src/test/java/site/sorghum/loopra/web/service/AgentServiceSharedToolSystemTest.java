package site.sorghum.loopra.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.bin.agent.core.AgentLoop;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.config.ConfigService;
import site.sorghum.loopra.bin.tool.ToolSystemInitializer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 共享工具系统缓存回归测试。
 * <p>
 * 复现并守护 listSessions → createAgent → getOrCreateSharedToolSystem 路径：
 * {@code computeIfAbsent} 的映射函数曾递归 {@code put} 同一 Map，
 * 触发 {@code IllegalStateException: Recursive update}。
 * </p>
 *
 * @author Sorghum
 */
class AgentServiceSharedToolSystemTest {

    @TempDir
    Path home;
    @TempDir
    Path workspaceA;
    @TempDir
    Path workspaceB;

    private String originalUserHome;

    @BeforeEach
    void isolateUserHomeAndConfig() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        // AgentService 脱离 Solon 容器实例化时，静态配置需先行就绪
        ConfigService.reload();
    }

    @AfterEach
    void restoreUserHome() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void firstAccessBuildsWithoutRecursiveUpdateAndReusesOnSecondAccess() throws Exception {
        AgentService service = new AgentService();

        // 修复前：首次访问即抛 IllegalStateException: Recursive update
        ToolSystemInitializer.Result first = getOrCreate(service, workspaceA);
        ToolSystemInitializer.Result second = getOrCreate(service, workspaceA);

        assertNotNull(first);
        assertSame(first, second, "同一工作区第二次访问应复用缓存");
    }

    @Test
    void differentWorkspacesGetIndependentToolSystems() throws Exception {
        AgentService service = new AgentService();

        ToolSystemInitializer.Result a = getOrCreate(service, workspaceA);
        ToolSystemInitializer.Result b = getOrCreate(service, workspaceB);

        assertNotSame(a, b, "不同工作区应各自构建独立的工具系统");
    }

    @Test
    void planExecutionStartRequiresSubstantiveModelOutput() throws Exception {
        AgentService service = new AgentService();
        LoopraAgent agent = getOrCreateAgent(service, workspaceA, "plan-start-detection");
        LoopraChatMessage hidden = LoopraChatMessage.ofUser("internal execution instruction");
        hidden.setWebHidden(true);
        agent.getCtx().injectHistory(hidden);
        agent.getCtx().injectHistory(LoopraChatMessage.assistant("", null, null));

        assertFalse(hasPlanExecutionStarted(service, agent));

        agent.getCtx().injectHistory(LoopraChatMessage.assistant("execution started", null, null));
        assertTrue(hasPlanExecutionStarted(service, agent));
    }

    @Test
    void truncateHistoryClearsPendingPlan() throws Exception {
        AgentService service = new AgentService();
        String sessionName = "rollback-plan";
        LoopraAgent agent = getOrCreateAgent(service, workspaceA, sessionName);
        LoopraChatMessage user = LoopraChatMessage.ofUser("plan task");
        user.setRollbackId("rollback-1");
        agent.getCtx().injectHistory(user);
        loopOf(agent).submitPlan("1. inspect\n2. implement");
        assertNotNull(loopOf(agent).getPendingPlan());

        String rollbackText = service.truncateHistoryBySnapshotId(
                workspaceA.toString(), sessionName, "rollback-1", null);

        assertEquals("plan task", rollbackText);
        assertNull(loopOf(agent).getPendingPlan());
        assertNull(agent.getSessionStore().getPendingPlan(sessionName));
    }

    private static LoopraAgent getOrCreateAgent(AgentService service, Path workspace, String sessionName) throws Exception {
        Method keyMethod = AgentService.class.getDeclaredMethod("generateSessionKey", String.class, String.class);
        keyMethod.setAccessible(true);
        String key = (String) keyMethod.invoke(service, workspace.toString(), sessionName);
        Method agentMethod = AgentService.class.getDeclaredMethod("getOrCreateAgent", String.class);
        agentMethod.setAccessible(true);
        return (LoopraAgent) agentMethod.invoke(service, key);
    }

    private static AgentLoop loopOf(LoopraAgent agent) throws Exception {
        Field field = LoopraAgent.class.getDeclaredField("loop");
        field.setAccessible(true);
        return (AgentLoop) field.get(agent);
    }

    private static boolean hasPlanExecutionStarted(AgentService service, LoopraAgent agent) throws Exception {
        Method method = AgentService.class.getDeclaredMethod("hasPlanExecutionStarted", LoopraAgent.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, agent);
    }

    private static ToolSystemInitializer.Result getOrCreate(AgentService service, Path workspace) throws Exception {
        Method method = AgentService.class.getDeclaredMethod("getOrCreateSharedToolSystem", Path.class);
        method.setAccessible(true);
        return (ToolSystemInitializer.Result) method.invoke(service, workspace);
    }
}
