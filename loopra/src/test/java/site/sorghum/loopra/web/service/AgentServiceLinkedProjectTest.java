package site.sorghum.loopra.web.service;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.web.model.ProjectInfoDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceLinkedProjectTest {

    @Test
    void formatsTrustedLinkedProjectContext() {
        String context = AgentService.formatLinkedProjectContext(List.of(
                new ProjectInfoDTO("backend-hash", "backend-service", "C:\\code\\backend", 0, 0, 3)
        ));

        assertTrue(context.contains("[系统注入：本轮关联项目]"));
        assertTrue(context.contains("名称: backend-service"));
        assertTrue(context.contains("hash: backend-hash"));
        assertTrue(context.contains("根目录: C:\\code\\backend"));
    }
}
