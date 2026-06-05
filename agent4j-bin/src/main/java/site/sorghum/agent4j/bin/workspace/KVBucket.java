package site.sorghum.agent4j.bin.workspace;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KV 存储桶，存储键值对及其元数据。
 *
 * @author Sorghum
 */
@Data
@Builder
public class KVBucket {

    /**
     * 存储的值
     */
    private String value;

    /**
     * 创建者
     */
    private String creator;

    /**
     * 创建时间戳（毫秒）
     */
    private long createdAt;

    /**
     * 更新时间戳（毫秒）
     */
    private long updatedAt;

    /**
     * 版本号
     */
    private int version;

    /**
     * 过期时间（毫秒），默认 -1 表示永不超时
     */
    @Builder.Default
    private long ttlMs = -1L;

    /**
     * 元数据
     */
    @Builder.Default
    private Map<String, String> metadata = new ConcurrentHashMap<>();

    /**
     * 检查当前条目是否已过期。
     * ttlMs < 0 表示永不超时。
     *
     * @return true 表示已过期
     */
    public boolean isExpired() {
        if (ttlMs < 0) {
            return false;
        }
        return System.currentTimeMillis() - createdAt > ttlMs;
    }
}
