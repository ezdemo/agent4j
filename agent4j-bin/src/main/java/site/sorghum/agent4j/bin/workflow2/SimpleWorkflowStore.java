package site.sorghum.agent4j.bin.workflow2;

/**
 * SimpleWorkflowStore — 简化工作流持久化接口。
 *
 * @author Sorghum
 */
public interface SimpleWorkflowStore {

    /** 保存工作流 */
    void save(SimpleWorkflow workflow);

    /** 按会话 ID 查找工作流 */
    SimpleWorkflow findBySession(String sessionId);

    /** 删除工作流 */
    void delete(String sessionId);
}
