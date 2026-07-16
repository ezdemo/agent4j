package site.sorghum.agent4j.bin.goal;

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

    /** 按会话 ID 加载目标 */
    Goal findBySession(String sessionId) throws IOException;

    /** 加载工作区内所有未关闭目标（恢复与管理用） */
    List<Goal> findActiveByWorkspace(String workspaceHash) throws IOException;

    /** 删除目标 */
    boolean delete(String sessionId) throws IOException;
}
