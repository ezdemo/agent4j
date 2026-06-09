package site.sorghum.agent4j.tool.job;

/**
 * 后台作业相关常量。
 *
 * @author Sorghum
 */
public final class JobConstants {

    private JobConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 毫秒到秒的转换系数
     */
    public static final long MILLIS_PER_SECOND = 1000L;

    /**
     * 默认的尾部行数
     */
    public static final int DEFAULT_TAIL_LINES = 80;

    /**
     * 最小的尾部行数（用于预览）
     */
    public static final int MIN_TAIL_LINES = 10;

    /**
     * 最大等待超时时间（5分钟，单位毫秒）
     */
    public static final long MAX_WAIT_TIMEOUT_MS = 300_000L;

    /**
     * 默认等待超时时间（5秒，单位毫秒）
     */
    public static final long DEFAULT_WAIT_TIMEOUT_MS = 5_000L;

    /**
     * 轮询间隔（200毫秒）
     */
    public static final long POLL_INTERVAL_MS = 200L;

    /**
     * 等待结果中的最近字符数
     */
    public static final int RECENT_CHARS_FOR_WAIT = 2000;
}
