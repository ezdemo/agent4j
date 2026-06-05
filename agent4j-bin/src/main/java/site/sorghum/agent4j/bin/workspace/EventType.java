package site.sorghum.agent4j.bin.workspace;

/**
 * 工作区事件类型枚举。
 *
 * @author Sorghum
 */
public enum EventType {

    /**
     * 新建条目
     */
    WRITE,

    /**
     * 更新已有条目
     */
    UPDATE,

    /**
     * 删除条目
     */
    DELETE
}
