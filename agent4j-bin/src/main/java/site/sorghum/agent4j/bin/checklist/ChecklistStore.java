package site.sorghum.agent4j.bin.checklist;

/**
 * ChecklistStore — 执行清单持久化接口。
 *
 * @author Sorghum
 */
public interface ChecklistStore {

    /** 保存清单 */
    void save(Checklist checklist);

    /** 按会话 ID 查找清单 */
    Checklist findBySession(String sessionId);

    /** 删除清单 */
    void delete(String sessionId);
}
