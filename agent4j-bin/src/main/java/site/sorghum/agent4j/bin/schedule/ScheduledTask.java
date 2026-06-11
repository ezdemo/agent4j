package site.sorghum.agent4j.bin.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 定时任务数据模型。
 * <p>
 * 每个定时任务绑定到一个工作区下的某个会话，
 * 在指定时间向该会话的 Agent 发送消息并获取回复。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTask implements Serializable {

    /** 唯一标识 */
    private String id;

    /** 任务名称 */
    private String name;

    /** 目标会话名称 */
    private String sessionName;

    /**
     * Cron 表达式（如 "0 0 9 * * ?" 表示每天 9:00）。
     * 优先级高于 intervalSec，非空时使用 cron 调度。
     */
    private String cronExpr;

    /**
     * 固定间隔（秒），与 cronExpr 二选一。
     * cronExpr 为空时生效。
     */
    private Long intervalSec;

    /** 要发送的消息内容 */
    private String message;

    /** 是否启用 */
    private boolean enabled;

    /** 上次执行时间戳（ms） */
    private long lastRunAt;

    /** 下次执行时间戳（ms） */
    private long nextRunAt;

    /** 执行次数累计 */
    private int runCount;

    /** 上次执行结果摘要 */
    private String lastResult;

    /** 上次错误信息 */
    private String lastError;

    /** 创建时间 */
    private long createdAt;

    /** 更新时间 */
    private long updatedAt;

    /**
     * 计算下次执行时间（基于 cronExpr 或 intervalSec）。
     *
     * @return 下次执行时间戳（ms），-1 表示无法计算
     */
    public long computeNextRunAt() {
        long now = System.currentTimeMillis();
        long base = lastRunAt > 0 ? lastRunAt : now;

        if (cronExpr != null && !cronExpr.isBlank()) {
            // 使用简化的 cron 解析
            return CronParser.nextTime(cronExpr, base);
        }

        if (intervalSec != null && intervalSec > 0) {
            return base + intervalSec * 1000L;
        }

        return -1;
    }
}
