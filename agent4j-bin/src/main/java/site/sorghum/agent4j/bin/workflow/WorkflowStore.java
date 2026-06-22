package site.sorghum.agent4j.bin.workflow;

import java.io.IOException;
import java.util.List;

/**
 * WorkflowStore — 工作流持久化仓库接口。
 * <p>
 * JSONL 格式存储于 workspace/{hash}/workflows/{sessionId}.jsonl。
 * 与 GoalStore 设计风格一致，保证可替换性。
 * </p>
 *
 * @author Sorghum
 */
public interface WorkflowStore {

    /** 保存/更新工作流（覆盖写入） */
    void save(Workflow workflow) throws IOException;

    /** 按会话 ID 加载工作流 */
    Workflow findBySession(String sessionId) throws IOException;

    /** 加载工作区内所有活跃工作流（巡检用） */
    List<Workflow> findActiveByWorkspace(String workspaceHash) throws IOException;

    /** 删除工作流 */
    boolean delete(String sessionId) throws IOException;
}