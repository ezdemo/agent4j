package site.sorghum.loopra.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.bin.config.ConfigService;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentService.listBashSessions 聚合接口测试。
 * <p>
 * 纯 JUnit 环境无 Solon 容器（LoopraSkillProvider 构造依赖 Solon bean），
 * cliSkillProviderMap 为空 —— 此处验证接口链路可用、空镜像时返回空列表且不抛异常；
 * 镜像记录/标记/清理逻辑由 harness 侧 SessionTerminalTalentBashSessionTest 覆盖。
 * </p>
 *
 * @author Sorghum
 */
class AgentServiceBashSessionTest {

    @TempDir
    Path home;

    private String originalUserHome;

    @BeforeEach
    void isolateUserHomeAndConfig() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        ConfigService.reload();
    }

    @AfterEach
    void restoreUserHome() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void emptyMirrorReturnsEmptyListForAllWorkspaces() {
        AgentService service = new AgentService();

        assertTrue(service.listBashSessions(null).isEmpty(), "全工作区查询在空镜像时应返回空列表");
        assertTrue(service.listBashSessions("").isEmpty(), "空路径查询应等同全工作区");
    }

    @Test
    void emptyMirrorReturnsEmptyListForSpecificWorkspace() {
        AgentService service = new AgentService();

        List<?> sessions = service.listBashSessions(home.toString());
        assertTrue(sessions.isEmpty(), "指定工作区查询在空镜像时应返回空列表");
    }

    @Test
    void terminateUnknownSessionReturnsNull() {
        AgentService service = new AgentService();

        assertNull(service.terminateBashSession("cmd_unknown", null), "空镜像下终止未知会话应返回 null");
        assertNull(service.terminateBashSession("cmd_unknown", home.toString()), "指定工作区下终止未知会话也应返回 null");
        assertNull(service.terminateBashSession("", null), "空 sessionId 应返回 null");
    }
}
