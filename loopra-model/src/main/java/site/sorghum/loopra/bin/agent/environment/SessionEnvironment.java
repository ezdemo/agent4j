package site.sorghum.loopra.bin.agent.environment;

import java.nio.file.Path;

/**
 * The filesystem roots used by one agent session.
 *
 * <p>{@code projectRoot} is the registered project directory. {@code executionRoot}
 * is where file tools run; in branch-sandbox mode this is a Git worktree.
 * {@code stateRoot} owns session persistence, goals and checklists, and defaults
 * to the project root.</p>
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
