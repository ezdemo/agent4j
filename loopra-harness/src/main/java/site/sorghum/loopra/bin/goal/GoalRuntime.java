package site.sorghum.loopra.bin.goal;

import site.sorghum.loopra.bin.project.ProjectRegistry;
import site.sorghum.loopra.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Path;

/** Goal 与当前项目、会话的绑定，供命令、工具和循环共用。 */
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

        /** 直接删除当前会话的 Goal 快照（不解析内容，损坏快照也可清除）。 */
        public boolean delete() throws IOException {
            return store.delete(sessionId);
        }
    }

    public static Scope forTool(ToolContext context) {
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            throw new IllegalStateException("Goal 只能在已初始化会话中使用");
        }
        // 状态根：隔离分支模式下 Goal 归属主项目，文件根则指向执行根
        return forWorkspace(context.getStateRootDir(), context.getSessionId());
    }

    public static Scope forWorkspace(Path workspace, String sessionId) {
        if (workspace == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("项目或会话未初始化");
        }
        ProjectRegistry manager = ProjectRegistry.getOrCreate(workspace.toAbsolutePath().normalize().toString());
        return new Scope(manager.getGoalStore(), sessionId, manager.getCurrentProjectHash());
    }
}
