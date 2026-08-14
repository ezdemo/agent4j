package site.sorghum.loopra.bin.goal;

import java.io.IOException;
import java.util.List;

/**
 * GoalStore — 目标持久化仓库接口。
 * <p>
 * 每个会话保存一个目标快照，存储实现负责原子覆盖写入。
 * </p>
 *
 * @author Sorghum
 */
public interface GoalStore {

    /** 保存/更新目标（覆盖写入） */
    void save(Goal goal) throws IOException;

    /**
     * 在当前会话没有未关闭目标时创建目标。
     *
     * @return 已创建目标；存在未关闭目标时返回该目标
     */
    Goal createIfNoOpenGoal(Goal goal) throws IOException;

    /** 在单个持久化临界区内读取、转换并保存目标。 */
    Goal update(String sessionId, GoalMutation mutation) throws IOException;

    /** 按会话 ID 加载目标 */
    Goal findBySession(String sessionId) throws IOException;

    /** 加载项目内所有未关闭目标（恢复与管理用） */
    List<Goal> findActiveByWorkspace(String workspaceHash) throws IOException;

    /** 删除目标 */
    boolean delete(String sessionId) throws IOException;

    @FunctionalInterface
    interface GoalMutation {
        void apply(Goal goal);
    }
}
