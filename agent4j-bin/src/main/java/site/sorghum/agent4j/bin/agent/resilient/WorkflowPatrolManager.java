package site.sorghum.agent4j.bin.agent.resilient;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.workflow.*;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作流巡检管理器 —— 从 {@link AgentLoop} 中抽取的 patrol / workflow-retry 逻辑。
 * <p>
 * 职责：
 * <ul>
 *   <li>定时巡检：30 秒间隔扫描工作流状态，将 FAILED 节点重置为 PENDING</li>
 *   <li>自动重试：查找可重试的工作流节点，注入上下文让 LLM 重新执行</li>
 *   <li>生命周期：巡检启动/停止由外部控制</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class WorkflowPatrolManager {

    /** 巡检间隔（秒） */
    private static final int PATROL_INTERVAL_SEC = 30;

    private final SessionService sessionService;
    private final ConversationContext ctx;
    private final AtomicBoolean running;

    private ScheduledExecutorService patrolScheduler;
    private volatile boolean patrolRunning;

    public WorkflowPatrolManager(SessionService sessionService, ConversationContext ctx, AtomicBoolean running) {
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
            Thread t = new Thread(r, "workflow-patrol");
            t.setDaemon(true);
            return t;
        });
        patrolScheduler.scheduleAtFixedRate(this::tick, PATROL_INTERVAL_SEC, PATROL_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("[workflow] 定时巡检已启动（间隔 {} 秒）", PATROL_INTERVAL_SEC);
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
        log.info("[workflow] 定时巡检已停止");
    }

    /**
     * 巡检是否正在运行。
     */
    public boolean isRunning() {
        return patrolRunning;
    }

    // ==================== 巡检周期任务 ====================

    /**
     * 巡检周期任务：检查工作流状态，重置失败节点。
     */
    public void tick() {
        try {
            if (running.get()) return;

            if (sessionService == null) return;
            String sid = sessionService.getStore().currentName();
            if (sid == null) return;

            WorkflowStore workflowStore = resolveWorkflowStore(sid);
            if (workflowStore == null) {
                stopPatrol();
                return;
            }

            Workflow workflow = workflowStore.findBySession(sid);
            if (workflow == null || workflow.getStatus() == WorkflowStatus.COMPLETED
                    || workflow.getStatus() == WorkflowStatus.FAILED) {
                stopPatrol();
                return;
            }

            boolean hasWork = false;
            for (WorkflowNode node : workflow.getNodes()) {
                if (node.getStatus() == NodeStatus.FAILED
                        && node.getRetryCount() < workflow.getMaxRetries()) {
                    hasWork = true;
                    node.setStatus(NodeStatus.PENDING);
                    node.setRetryCount(node.getRetryCount() + 1);
                    log.info("[workflow] [定时巡检] 检测到失败节点，重置为 PENDING: node={}, retry={}/{}",
                            node.getId(), node.getRetryCount(), workflow.getMaxRetries());
                }
            }

            if (hasWork) {
                workflow.setUpdatedAt(java.time.Instant.now());
                workflowStore.save(workflow);
                ctx.addUser(
                        "⏰ [定时巡检] 检测到有节点执行失败，已自动重置。\n"
                                + "当前进度：" + workflow.progressText() + "\n"
                                + "请检查失败原因并继续执行。");
            }
        } catch (Exception e) {
            log.warn("[workflow] 定时巡检异常: {}", e.getMessage());
        }
    }

    // ==================== 自动重试 ====================

    /**
     * 查找是否有需要自动重试的工作流和节点。
     *
     * @return WorkflowAndStore 或 null（无可重试工作流）
     */
    public WorkflowAndStore findRetriableWorkflow() {
        try {
            if (sessionService == null) return null;
            String sessionId = sessionService.getStore().currentName();
            if (sessionId == null) return null;

            WorkflowStore workflowStore = resolveWorkflowStore(sessionId);
            if (workflowStore == null) return null;

            Workflow workflow = workflowStore.findBySession(sessionId);
            if (workflow == null || workflow.getStatus() != WorkflowStatus.ACTIVE) return null;

            boolean hasRetriable = false;
            for (WorkflowNode n : workflow.getNodes()) {
                if (n.getStatus() == NodeStatus.FAILED
                        && n.getRetryCount() < workflow.getMaxRetries()) {
                    hasRetriable = true;
                    break;
                }
            }
            if (!hasRetriable) return null;

            return new WorkflowAndStore(workflow, workflowStore);
        } catch (Exception e) {
            log.warn("[workflow] 查找可重试工作流失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过会话 ID 查找对应的 WorkflowStore。
     */
    private WorkflowStore resolveWorkflowStore(String sessionId) {
        try {
            Path workspaceDir = Paths.get(System.getProperty("user.home"), ".agent4j", "workspace");
            if (!Files.isDirectory(workspaceDir)) return null;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(workspaceDir)) {
                for (Path wsDir : ds) {
                    if (Files.isDirectory(wsDir)) {
                        Path workflowFile = wsDir.resolve("workflows").resolve(sessionId + ".jsonl");
                        if (Files.exists(workflowFile)) {
                            return new JsonlWorkflowStore(wsDir);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[workflow] 解析 WorkflowStore 失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 内部数据类 ====================

    /**
     * 工作流和其存储的临时容器。
     */
    public record WorkflowAndStore(Workflow workflow, WorkflowStore workflowStore) {
    }
}