package site.sorghum.loopra.integration.cutin.plugin.preflight;

import site.sorghum.loopra.bin.agent.model.HitlState;
import site.sorghum.loopra.bin.agent.model.UserMessage;

import java.io.IOException;

/** Loopra 主图前置节点访问宿主状态的最小接口。 */
public interface LoopraPreflightHost {

    UserMessage sanitizePreflightMessage(UserMessage message);

    void appendPreflightUserMessage(UserMessage message);

    void clearSuspendedCutinState();

    HitlState hitlState();

    boolean hasSuspendedCutin();

    boolean hasSandboxPending();

    String resumeApprovedTurn() throws IOException;

    String rejectTurn();
}
