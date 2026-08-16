package site.sorghum.loopra.bin.agent.environment;

import java.nio.file.Path;

/**
  * 单个 Agent 会话使用的文件系统根。
 *
  * <p>{@code projectRoot} 是已注册的项目目录。{@code executionRoot}
  * 是文件工具执行的位置；在分支沙箱模式下它是 Git worktree。
  * {@code stateRoot} 持有会话持久化、目标与清单，默认取项目根。</p>
 */
public record SessionEnvironment(Path projectRoot, Path executionRoot, Path stateRoot) {

    public SessionEnvironment {
        executionRoot = executionRoot == null ? projectRoot : executionRoot;
        stateRoot = stateRoot == null ? projectRoot : stateRoot;
    }

    public SessionEnvironment(Path projectRoot) {
        this(projectRoot, projectRoot, projectRoot);
    }

    public static SessionEnvironment local(Path projectRoot) {
        return new SessionEnvironment(projectRoot);
    }

    public static SessionEnvironment of(Path executionRoot, Path stateRoot) {
        Path projectRoot = stateRoot != null ? stateRoot : executionRoot;
        return new SessionEnvironment(projectRoot, executionRoot, stateRoot);
    }

    public static SessionEnvironment isolated(Path projectRoot, Path executionRoot) {
        return new SessionEnvironment(projectRoot, executionRoot, projectRoot);
    }

    public boolean isolated() {
        return projectRoot != null && !projectRoot.equals(executionRoot);
    }

}
