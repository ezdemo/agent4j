package site.sorghum.agent4j.bin.agent.resilient;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.goal.*;
import site.sorghum.agent4j.bin.session.SessionService;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 目标巡检管理器 —— 从 {@link AgentLoop} 中抽取的 patrol / goal-retry 逻辑。
 * <p>
 * 职责：
 * <ul>
 *   <li>定时巡检：30 秒间隔扫描目标状态，将 FAILED 步骤重置为 PENDING</li>
 *   <li>自动重试：查找可重试的目标步骤，注入上下文让 LLM 重新执行</li>
 *   <li>生命周期：巡检启动/停止由外部控制</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class GoalPatrolManager {

    /** 巡检间隔（秒） */
    private static final int PATROL_INTERVAL_SEC = 30;

    private final SessionService sessionService;
    private final ConversationContext ctx;
    private final AtomicBoolean running;

    private ScheduledExecutorService patrolScheduler;
    private volatile boolean patrolRunning;

    public GoalPatrolManager(SessionService sessionService, ConversationContext ctx, AtomicBoolean running) {
        this.sessionService = sessionService;
        this.ctx = ctx;
        this.running = running;
    }

    // ==================== 巡检生命周期 ====================

    /**
     * 启动定时巡检（30 秒间隔）。
     */
    public synchronized void startPatrol() {
        if (patrolRunning) return;
        patrolRunning = true;
        patrolScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "goal-patrol");
            t.setDaemon(true);
            return t;
        });
        patrolScheduler.scheduleAtFixedRate(this::tick, PATROL_INTERVAL_SEC, PATROL_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("[goal] 定时巡检已启动（间隔 {} 秒）", PATROL_INTERVAL_SEC);
    }

    /**
     * 停止定时巡检。
     */
    public synchronized void stopPatrol() {
        if (!patrolRunning) return;
        patrolRunning = false;
        if (patrolScheduler != null && !patrolScheduler.isShutdown()) {
            patrolScheduler.shutdown();
        }
        log.info("[goal] 定时巡检已停止");
    }

    /**
     * 巡检是否正在运行。
     */
    public boolean isRunning() {
        return patrolRunning;
    }

    // ==================== 巡检周期任务 ====================

    /**
     * 巡检周期任务：检查目标状态，重置失败步骤。
     */
    public void tick() {
        try {
            if (running.get()) return;

            if (sessionService == null) return;
            String sid = sessionService.getStore().currentName();
            if (sid == null) return;

            GoalStore goalStore = resolveGoalStore(sid);
            if (goalStore == null) {
                stopPatrol();
                return;
            }

            Goal goal = goalStore.findBySession(sid);
            if (goal == null || goal.getStatus() == GoalStatus.COMPLETED
                    || goal.getStatus() == GoalStatus.FAILED) {
                stopPatrol();
                return;
            }

            boolean hasWork = false;
            for (GoalStep step : goal.getSteps()) {
                if (step.getStatus() == StepStatus.FAILED
                        && step.getRetryCount() < goal.getMaxRetries()) {
                    hasWork = true;
                    step.setStatus(StepStatus.PENDING);
                    step.setRetryCount(step.getRetryCount() + 1);
                    log.info("[goal] [定时巡检] 检测到失败步骤，重置为 PENDING: step={}, retry={}/{}",
                            step.getIndex() + 1, step.getRetryCount(), goal.getMaxRetries());
                }
            }

            if (hasWork) {
                goal.setUpdatedAt(java.time.Instant.now());
                goalStore.save(goal);
                ctx.addUser(
                        "⏰ [定时巡检] 检测到有步骤执行失败，已自动重置。\n"
                                + "当前进度：" + goal.progressText() + "\n"
                                + "请检查失败原因并继续执行。");
            }
        } catch (Exception e) {
            log.warn("[goal] 定时巡检异常: {}", e.getMessage());
        }
    }

    // ==================== 自动重试 ====================

    /**
     * 查找是否有需要自动重试的目标和步骤。
     *
     * @return GoalAndStore 或 null（无可重试目标）
     */
    public GoalAndStore findRetriableGoal() {
        try {
            if (sessionService == null) return null;
            String sessionId = sessionService.getStore().currentName();
            if (sessionId == null) return null;

            GoalStore goalStore = resolveGoalStore(sessionId);
            if (goalStore == null) return null;

            Goal goal = goalStore.findBySession(sessionId);
            if (goal == null || goal.getStatus() != GoalStatus.ACTIVE) return null;

            boolean hasRetriable = false;
            for (GoalStep s : goal.getSteps()) {
                if (s.getStatus() == StepStatus.FAILED
                        && s.getRetryCount() < goal.getMaxRetries()) {
                    hasRetriable = true;
                    break;
                }
            }
            if (!hasRetriable) return null;

            return new GoalAndStore(goal, goalStore);
        } catch (Exception e) {
            log.warn("[goal] 查找可重试目标失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过会话 ID 查找对应的 GoalStore。
     */
    private GoalStore resolveGoalStore(String sessionId) {
        try {
            Path workspaceDir = Paths.get(System.getProperty("user.home"), ".agent4j", "workspace");
            if (!Files.isDirectory(workspaceDir)) return null;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(workspaceDir)) {
                for (Path wsDir : ds) {
                    if (Files.isDirectory(wsDir)) {
                        Path goalFile = wsDir.resolve("goals").resolve(sessionId + ".jsonl");
                        if (Files.exists(goalFile)) {
                            return new JsonlGoalStore(wsDir);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[goal] 解析 GoalStore 失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 内部数据类 ====================

    /**
     * 目标和其存储的临时容器。
     */
    public record GoalAndStore(Goal goal, GoalStore goalStore) {
    }
}
