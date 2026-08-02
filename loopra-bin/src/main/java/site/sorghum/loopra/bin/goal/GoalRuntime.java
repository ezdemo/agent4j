package site.sorghum.loopra.bin.goal;

import site.sorghum.loopra.bin.workspace.WorkspaceManager;
import site.sorghum.loopra.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Path;

/** Goal 与当前工作区、会话的绑定，供命令、工具和循环共用。 */
public final class GoalRuntime {
    private GoalRuntime() {
    }

    public record Scope(GoalStore store, String sessionId, String workspaceHash) {
        public Goal load() throws IOException {
            return store.findBySession(sessionId);
        }

        public void save(Goal goal) throws IOException {
            store.save(goal);
        }

        public Goal createIfNoOpenGoal(Goal goal) throws IOException {
            return store.createIfNoOpenGoal(goal);
        }

        public Goal update(GoalStore.GoalMutation mutation) throws IOException {
            return store.update(sessionId, mutation);
        }
    }

    public static Scope forTool(ToolContext context) {
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            throw new IllegalStateException("Goal 只能在已初始化会话中使用");
        }
        return forWorkspace(context.getRootDir(), context.getSessionId());
    }

    public static Scope forWorkspace(Path workspace, String sessionId) {
        if (workspace == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("工作区或会话未初始化");
        }
        WorkspaceManager manager = WorkspaceManager.getOrCreate(workspace.toAbsolutePath().normalize().toString());
        return new Scope(manager.getGoalStore(), sessionId, manager.getCurrentWorkspaceHash());
    }
}
