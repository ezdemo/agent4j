package site.sorghum.agent4j.bin.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * WorkflowNode — 工作流节点。
 * <p>
 * 支持多种节点类型：执行、条件、并行、人工审批、子工作流。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {
    /** 节点ID（如 "n1", "n2"） */
    private String id;
    /** 节点描述 */
    private String description;
    /** 节点类型 */
    private NodeType type;
    /** 节点状态 */
    private NodeStatus status;
    /** 已重试次数 */
    @Builder.Default
    private int retryCount = 0;
    /** 最后一次失败的错误信息 */
    private String lastError;
    /** 执行结果 */
    private String result;
    /** 完成时间 */
    private Instant completedAt;

    // 条件节点专用
    /** 条件表达式（如 "test.passed == true"） */
    private String condition;
    /** 条件结果（"true"/"false"/null） */
    private String conditionResult;

    // 并行节点专用
    /** 并行分支的节点ID列表 */
    @Builder.Default
    private List<String> parallelBranches = new ArrayList<>();

    // 人工审批节点专用
    /** 审批提示 */
    private String approvalPrompt;
    /** 审批结果（approved/rejected） */
    private String approvalResult;

    // 子工作流节点专用
    /** 子工作流ID */
    private String subWorkflowId;

    // 循环节点专用
    /** 循环体起始节点ID（回跳目标） */
    private String loopTarget;
    /** 最大迭代次数（防死循环） */
    @Builder.Default
    private int maxIterations = 10;
    /** 当前迭代次数 */
    @Builder.Default
    private int iterationCount = 0;
    /** 循环退出条件描述 */
    private String breakCondition;

    /**
     * 判断节点是否已完成（成功或跳过）。
     */
    public boolean isCompleted() {
        return status == NodeStatus.DONE || status == NodeStatus.SKIPPED;
    }

    /**
     * 判断节点是否可以重试。
     */
    public boolean isRetriable(int maxRetries) {
        return status == NodeStatus.FAILED && retryCount < maxRetries;
    }

    /**
     * 判断节点是否就绪（所有依赖已满足）。
     */
    public boolean isReady() {
        return status == NodeStatus.READY;
    }

    /**
     * 判断节点是否正在执行。
     */
    public boolean isRunning() {
        return status == NodeStatus.RUNNING;
    }

    /**
     * 判断节点是否等待中。
     */
    public boolean isWaiting() {
        return status == NodeStatus.WAITING;
    }

    /**
     * 判断节点是否被阻塞。
     */
    public boolean isBlocked() {
        return status == NodeStatus.BLOCKED;
    }
}