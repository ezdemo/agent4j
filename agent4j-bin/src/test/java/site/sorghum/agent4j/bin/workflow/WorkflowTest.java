package site.sorghum.agent4j.bin.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作流系统单元测试。
 *
 * @author Sorghum
 */
class WorkflowTest {

    @TempDir
    Path tempDir;

    @Test
    void testWorkflowCreation() {
        // 创建测试工作流
        Workflow workflow = createTestWorkflow();
        
        // 验证工作流属性
        assertNotNull(workflow);
        assertEquals("test-workflow-1", workflow.getId());
        assertEquals("测试工作流", workflow.getTitle());
        assertEquals(WorkflowStatus.DRAFT, workflow.getStatus());
        assertEquals(3, workflow.getMaxRetries());
        
        // 验证节点
        assertEquals(5, workflow.getNodes().size()); // start + 3 nodes + end
        
        // 验证边
        assertEquals(4, workflow.getEdges().size());
    }

    @Test
    void testWorkflowNodeStatus() {
        Workflow workflow = createTestWorkflow();
        
        // 测试节点状态
        WorkflowNode startNode = workflow.findNode("start");
        assertNotNull(startNode);
        assertEquals(NodeStatus.DONE, startNode.getStatus());
        assertTrue(startNode.isCompleted());
        
        WorkflowNode n1 = workflow.findNode("n1");
        assertNotNull(n1);
        assertEquals(NodeStatus.PENDING, n1.getStatus());
        assertFalse(n1.isCompleted());
        assertFalse(n1.isRunning());
        assertFalse(n1.isWaiting());
        assertFalse(n1.isBlocked());
    }

    @Test
    void testWorkflowProgress() {
        Workflow workflow = createTestWorkflow();
        
        // 初始进度
        assertEquals("1/5 (20%)", workflow.progressText()); // 只有start节点完成
        
        // 标记一个节点完成
        WorkflowNode n1 = workflow.findNode("n1");
        n1.setStatus(NodeStatus.DONE);
        n1.setCompletedAt(Instant.now());
        
        assertEquals("2/5 (40%)", workflow.progressText());
    }

    @Test
    void testWorkflowDependencies() {
        Workflow workflow = createTestWorkflow();
        
        // 测试依赖关系
        List<String> n2Predecessors = workflow.getPredecessorIds("n2");
        assertTrue(n2Predecessors.contains("n1"));
        
        List<String> n1Successors = workflow.getSuccessorIds("n1");
        assertTrue(n1Successors.contains("n2"));
    }

    @Test
    void testWorkflowReadyNodes() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：只有start节点完成，n1应该就绪
        List<WorkflowNode> readyNodes = workflow.getReadyNodes();
        assertEquals(1, readyNodes.size());
        assertEquals("n1", readyNodes.get(0).getId());
        
        // 完成n1后，n2应该就绪
        WorkflowNode n1 = workflow.findNode("n1");
        n1.setStatus(NodeStatus.DONE);
        n1.setCompletedAt(Instant.now());
        
        readyNodes = workflow.getReadyNodes();
        assertEquals(1, readyNodes.size());
        assertEquals("n2", readyNodes.get(0).getId());
    }

    @Test
    void testWorkflowCompletion() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：未完成
        assertFalse(workflow.isAllDone());
        assertFalse(workflow.hasFailed());
        
        // 完成所有节点
        for (WorkflowNode node : workflow.getNodes()) {
            if (node.getType() != NodeType.START && node.getType() != NodeType.END) {
                node.setStatus(NodeStatus.DONE);
                node.setCompletedAt(Instant.now());
            }
        }
        
        // 验证完成状态
        assertTrue(workflow.isAllDone());
        assertFalse(workflow.hasFailed());
    }

    @Test
    void testWorkflowSerialization() throws IOException {
        Workflow workflow = createTestWorkflow();
        
        // 序列化
        String json = JsonlWorkflowStore.serializeWorkflow(workflow);
        assertNotNull(json);
        assertFalse(json.isEmpty());
        
        // 反序列化
        Workflow deserialized = JsonlWorkflowStore.deserializeWorkflow(json);
        assertNotNull(deserialized);
        
        // 验证反序列化结果
        assertEquals(workflow.getId(), deserialized.getId());
        assertEquals(workflow.getTitle(), deserialized.getTitle());
        assertEquals(workflow.getStatus(), deserialized.getStatus());
        assertEquals(workflow.getNodes().size(), deserialized.getNodes().size());
        assertEquals(workflow.getEdges().size(), deserialized.getEdges().size());
    }

    @Test
    void testWorkflowStore() throws IOException {
        // 创建临时存储
        JsonlWorkflowStore store = new JsonlWorkflowStore(tempDir);
        
        // 创建测试工作流
        Workflow workflow = createTestWorkflow();
        
        // 保存工作流
        store.save(workflow);
        
        // 加载工作流
        Workflow loaded = store.findBySession("test-session-1");
        assertNotNull(loaded);
        assertEquals(workflow.getId(), loaded.getId());
        assertEquals(workflow.getTitle(), loaded.getTitle());
        
        // 验证文件存在
        Path workflowFile = tempDir.resolve("workflows").resolve("test-session-1.jsonl");
        assertTrue(java.nio.file.Files.exists(workflowFile));
        
        // 删除工作流
        assertTrue(store.delete("test-session-1"));
        assertFalse(java.nio.file.Files.exists(workflowFile));
    }

    @Test
    void testWorkflowNodeTypes() {
        Workflow workflow = createTestWorkflow();
        
        // 测试不同节点类型
        WorkflowNode startNode = workflow.findNode("start");
        assertEquals(NodeType.START, startNode.getType());
        
        WorkflowNode endNode = workflow.findNode("end");
        assertEquals(NodeType.END, endNode.getType());
        
        WorkflowNode n1 = workflow.findNode("n1");
        assertEquals(NodeType.ACTION, n1.getType());
        
        // 修改节点类型
        n1.setType(NodeType.CONDITION);
        assertEquals(NodeType.CONDITION, n1.getType());
    }

    @Test
    void testWorkflowEdgeConditions() {
        Workflow workflow = createTestWorkflow();
        
        // 添加条件边
        WorkflowEdge conditionalEdge = WorkflowEdge.builder()
                .id("e5")
                .from("n2")
                .to("n3")
                .type(EdgeType.CONDITION_TRUE)
                .condition("test.passed == true")
                .build();
        
        workflow.addEdge(conditionalEdge);
        
        // 验证条件边
        List<WorkflowEdge> n2Edges = workflow.getEdges().stream()
                .filter(e -> e.getFrom().equals("n2"))
                .toList();
        
        assertEquals(2, n2Edges.size()); // 普通边 + 条件边
        
        // 验证条件边属性
        WorkflowEdge found = n2Edges.stream()
                .filter(e -> e.isConditional())
                .findFirst()
                .orElse(null);
        
        assertNotNull(found);
        assertEquals(EdgeType.CONDITION_TRUE, found.getType());
        assertEquals("test.passed == true", found.getCondition());
    }

    @Test
    void testWorkflowNodeIdGeneration() {
        Workflow workflow = createTestWorkflow();
        
        // 测试节点ID生成
        String newId = workflow.generateNodeId();
        assertNotNull(newId);
        assertTrue(newId.startsWith("n"));
        
        // 验证ID不重复
        for (WorkflowNode node : workflow.getNodes()) {
            assertNotEquals(newId, node.getId());
        }
    }

    @Test
    void testWorkflowEdgeIdGeneration() {
        Workflow workflow = createTestWorkflow();
        
        // 测试边ID生成
        String newId = workflow.generateEdgeId();
        assertNotNull(newId);
        assertTrue(newId.startsWith("e"));
        
        // 验证ID不重复
        for (WorkflowEdge edge : workflow.getEdges()) {
            assertNotEquals(newId, edge.getId());
        }
    }

    @Test
    void testWorkflowRetriableNodes() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：无可重试节点
        List<WorkflowNode> retriableNodes = workflow.getRetriableNodes();
        assertTrue(retriableNodes.isEmpty());
        
        // 设置一个节点为失败状态
        WorkflowNode n1 = workflow.findNode("n1");
        n1.setStatus(NodeStatus.FAILED);
        n1.setRetryCount(1);
        
        // 现在应该有可重试节点
        retriableNodes = workflow.getRetriableNodes();
        assertEquals(1, retriableNodes.size());
        assertEquals("n1", retriableNodes.get(0).getId());
        
        // 超过重试次数后，不应该再可重试
        n1.setRetryCount(3);
        retriableNodes = workflow.getRetriableNodes();
        assertTrue(retriableNodes.isEmpty());
    }

    @Test
    void testWorkflowRunningNodes() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：无运行节点
        List<WorkflowNode> runningNodes = workflow.getRunningNodes();
        assertTrue(runningNodes.isEmpty());
        
        // 设置一个节点为运行状态
        WorkflowNode n1 = workflow.findNode("n1");
        n1.setStatus(NodeStatus.RUNNING);
        
        // 现在应该有运行节点
        runningNodes = workflow.getRunningNodes();
        assertEquals(1, runningNodes.size());
        assertEquals("n1", runningNodes.get(0).getId());
    }

    @Test
    void testWorkflowWaitingNodes() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：无等待节点
        List<WorkflowNode> waitingNodes = workflow.getWaitingNodes();
        assertTrue(waitingNodes.isEmpty());
        
        // 设置一个节点为等待状态
        WorkflowNode n1 = workflow.findNode("n1");
        n1.setStatus(NodeStatus.WAITING);
        
        // 现在应该有等待节点
        waitingNodes = workflow.getWaitingNodes();
        assertEquals(1, waitingNodes.size());
        assertEquals("n1", waitingNodes.get(0).getId());
    }

    @Test
    void testWorkflowFailedNodes() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：无失败节点
        List<WorkflowNode> failedNodes = workflow.getFailedNodes();
        assertTrue(failedNodes.isEmpty());
        
        // 设置一个节点为失败状态
        WorkflowNode n1 = workflow.findNode("n1");
        n1.setStatus(NodeStatus.FAILED);
        n1.setLastError("测试错误");
        
        // 现在应该有失败节点
        failedNodes = workflow.getFailedNodes();
        assertEquals(1, failedNodes.size());
        assertEquals("n1", failedNodes.get(0).getId());
        assertEquals("测试错误", failedNodes.get(0).getLastError());
    }

    @Test
    void testWorkflowNodeRetriable() {
        WorkflowNode node = WorkflowNode.builder()
                .id("n1")
                .description("测试节点")
                .type(NodeType.ACTION)
                .status(NodeStatus.FAILED)
                .retryCount(1)
                .build();
        
        // 测试可重试条件
        assertTrue(node.isRetriable(3));
        assertFalse(node.isRetriable(1));
        
        // 测试其他状态
        node.setStatus(NodeStatus.DONE);
        assertFalse(node.isRetriable(3));
        
        node.setStatus(NodeStatus.PENDING);
        assertFalse(node.isRetriable(3));
    }

    @Test
    void testWorkflowNodeCompleted() {
        WorkflowNode node = WorkflowNode.builder()
                .id("n1")
                .description("测试节点")
                .type(NodeType.ACTION)
                .status(NodeStatus.DONE)
                .build();
        
        assertTrue(node.isCompleted());
        
        node.setStatus(NodeStatus.SKIPPED);
        assertTrue(node.isCompleted());
        
        node.setStatus(NodeStatus.PENDING);
        assertFalse(node.isCompleted());
        
        node.setStatus(NodeStatus.FAILED);
        assertFalse(node.isCompleted());
    }

    @Test
    void testWorkflowEdgeConditional() {
        WorkflowEdge edge = WorkflowEdge.builder()
                .id("e1")
                .from("n1")
                .to("n2")
                .type(EdgeType.CONDITION_TRUE)
                .build();
        
        assertTrue(edge.isConditional());
        assertFalse(edge.isNormal());
        
        edge.setType(EdgeType.CONDITION_FALSE);
        assertTrue(edge.isConditional());
        assertFalse(edge.isNormal());
        
        edge.setType(EdgeType.NORMAL);
        assertFalse(edge.isConditional());
        assertTrue(edge.isNormal());
        
        edge.setType(EdgeType.DEFAULT);
        assertFalse(edge.isConditional());
        assertTrue(edge.isNormal());
    }

    @Test
    void testWorkflowNodeReady() {
        WorkflowNode node = WorkflowNode.builder()
                .id("n1")
                .description("测试节点")
                .type(NodeType.ACTION)
                .status(NodeStatus.READY)
                .build();
        
        assertTrue(node.isReady());
        
        node.setStatus(NodeStatus.PENDING);
        assertFalse(node.isReady());
        
        node.setStatus(NodeStatus.RUNNING);
        assertFalse(node.isReady());
    }

    @Test
    void testWorkflowNodeRunning() {
        WorkflowNode node = WorkflowNode.builder()
                .id("n1")
                .description("测试节点")
                .type(NodeType.ACTION)
                .status(NodeStatus.RUNNING)
                .build();
        
        assertTrue(node.isRunning());
        
        node.setStatus(NodeStatus.PENDING);
        assertFalse(node.isRunning());
        
        node.setStatus(NodeStatus.DONE);
        assertFalse(node.isRunning());
    }

    @Test
    void testWorkflowNodeWaiting() {
        WorkflowNode node = WorkflowNode.builder()
                .id("n1")
                .description("测试节点")
                .type(NodeType.ACTION)
                .status(NodeStatus.WAITING)
                .build();
        
        assertTrue(node.isWaiting());
        
        node.setStatus(NodeStatus.PENDING);
        assertFalse(node.isWaiting());
        
        node.setStatus(NodeStatus.DONE);
        assertFalse(node.isWaiting());
    }

    @Test
    void testWorkflowNodeBlocked() {
        WorkflowNode node = WorkflowNode.builder()
                .id("n1")
                .description("测试节点")
                .type(NodeType.ACTION)
                .status(NodeStatus.BLOCKED)
                .build();
        
        assertTrue(node.isBlocked());
        
        node.setStatus(NodeStatus.PENDING);
        assertFalse(node.isBlocked());
        
        node.setStatus(NodeStatus.DONE);
        assertFalse(node.isBlocked());
    }

    @Test
    void testWorkflowIsRunning() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：草稿，不是运行状态
        assertFalse(workflow.isRunning());
        
        // 设置为活跃状态
        workflow.setStatus(WorkflowStatus.ACTIVE);
        assertTrue(workflow.isRunning());
        
        // 设置为暂停状态
        workflow.setStatus(WorkflowStatus.PAUSED);
        assertFalse(workflow.isRunning());
        
        // 设置为完成状态
        workflow.setStatus(WorkflowStatus.COMPLETED);
        assertFalse(workflow.isRunning());
        
        // 设置为失败状态
        workflow.setStatus(WorkflowStatus.FAILED);
        assertFalse(workflow.isRunning());
    }

    @Test
    void testWorkflowHasFailed() {
        Workflow workflow = createTestWorkflow();
        
        // 初始状态：无失败
        assertFalse(workflow.hasFailed());
        
        // 设置一个节点为失败状态
        WorkflowNode n1 = workflow.findNode("n1");
        n1.setStatus(NodeStatus.FAILED);
        
        assertTrue(workflow.hasFailed());
    }

    @Test
    void testWorkflowAddRemoveNode() {
        Workflow workflow = createTestWorkflow();
        int initialNodeCount = workflow.getNodes().size();
        
        // 添加节点
        WorkflowNode newNode = WorkflowNode.builder()
                .id("n4")
                .description("新节点")
                .type(NodeType.ACTION)
                .status(NodeStatus.PENDING)
                .build();
        
        workflow.addNode(newNode);
        assertEquals(initialNodeCount + 1, workflow.getNodes().size());
        assertNotNull(workflow.findNode("n4"));
        
        // 移除节点
        workflow.removeNode("n4");
        assertEquals(initialNodeCount, workflow.getNodes().size());
        assertNull(workflow.findNode("n4"));
    }

    @Test
    void testWorkflowAddRemoveEdge() {
        Workflow workflow = createTestWorkflow();
        int initialEdgeCount = workflow.getEdges().size();
        
        // 添加边（使用不重复的 from→to，避免与现有边冲突）
        WorkflowEdge newEdge = WorkflowEdge.builder()
                .id("e5")
                .from("n3")
                .to("n1")
                .type(EdgeType.NORMAL)
                .build();
        
        workflow.addEdge(newEdge);
        assertEquals(initialEdgeCount + 1, workflow.getEdges().size());
        
        // 移除边
        workflow.removeEdge("n3", "n1");
        assertEquals(initialEdgeCount, workflow.getEdges().size());
    }

    // 辅助方法：创建测试工作流
    private Workflow createTestWorkflow() {
        List<WorkflowNode> nodes = new ArrayList<>(Arrays.asList(
                WorkflowNode.builder()
                        .id("start")
                        .description("开始")
                        .type(NodeType.START)
                        .status(NodeStatus.DONE)
                        .retryCount(0)
                        .build(),
                WorkflowNode.builder()
                        .id("n1")
                        .description("创建用户表")
                        .type(NodeType.ACTION)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build(),
                WorkflowNode.builder()
                        .id("n2")
                        .description("实现注册API")
                        .type(NodeType.ACTION)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build(),
                WorkflowNode.builder()
                        .id("n3")
                        .description("验证邮箱")
                        .type(NodeType.ACTION)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build(),
                WorkflowNode.builder()
                        .id("end")
                        .description("结束")
                        .type(NodeType.END)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build()
        ));

        List<WorkflowEdge> edges = new ArrayList<>(Arrays.asList(
                WorkflowEdge.builder()
                        .id("e1")
                        .from("start")
                        .to("n1")
                        .type(EdgeType.NORMAL)
                        .build(),
                WorkflowEdge.builder()
                        .id("e2")
                        .from("n1")
                        .to("n2")
                        .type(EdgeType.NORMAL)
                        .build(),
                WorkflowEdge.builder()
                        .id("e3")
                        .from("n2")
                        .to("n3")
                        .type(EdgeType.NORMAL)
                        .build(),
                WorkflowEdge.builder()
                        .id("e4")
                        .from("n3")
                        .to("end")
                        .type(EdgeType.NORMAL)
                        .build()
        ));

        return Workflow.builder()
                .id("test-workflow-1")
                .sessionId("test-session-1")
                .workspaceHash("test-hash-1")
                .title("测试工作流")
                .description("这是一个测试工作流")
                .status(WorkflowStatus.DRAFT)
                .maxRetries(3)
                .nodes(nodes)
                .edges(edges)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ========== 条件分支测试 ==========

    @Test
    void testConditionalBranchingTrue() {
        Workflow workflow = createConditionalWorkflow();

        // 初始状态：start 已完成，condition 应该就绪
        List<WorkflowNode> readyNodes = workflow.getReadyNodes();
        assertEquals(1, readyNodes.size());
        assertEquals("condition", readyNodes.get(0).getId());

        // 模拟条件结果选中 n_true 分支，标记 condition 完成
        WorkflowNode condition = workflow.findNode("condition");
        condition.setStatus(NodeStatus.DONE);
        condition.setConditionResult("n_true");
        condition.setCompletedAt(Instant.now());

        // 模拟引擎自动跳过未选分支（n_false）
        workflow.skipBranch("n_false", "condition");

        // 此时 n_true 应该就绪，n_false 不应就绪
        readyNodes = workflow.getReadyNodes();
        assertEquals(1, readyNodes.size());
        assertEquals("n_true", readyNodes.get(0).getId());

        // 完成 n_true
        WorkflowNode nTrue = workflow.findNode("n_true");
        nTrue.setStatus(NodeStatus.DONE);
        nTrue.setCompletedAt(Instant.now());

        // 此时 end 应该就绪
        readyNodes = workflow.getReadyNodes();
        assertEquals(1, readyNodes.size());
        assertEquals("end", readyNodes.get(0).getId());
    }

    @Test
    void testConditionalBranchingFalse() {
        Workflow workflow = createConditionalWorkflow();

        // 标记 condition 完成，选中 n_false 分支
        WorkflowNode condition = workflow.findNode("condition");
        condition.setStatus(NodeStatus.DONE);
        condition.setConditionResult("n_false");
        condition.setCompletedAt(Instant.now());

        // 模拟引擎自动跳过未选分支（n_true）
        workflow.skipBranch("n_true", "condition");

        // 此时 n_false 应该就绪，n_true 不应就绪
        List<WorkflowNode> readyNodes = workflow.getReadyNodes();
        assertEquals(1, readyNodes.size());
        assertEquals("n_false", readyNodes.get(0).getId());
    }

    @Test
    void testSkipBranch() {
        Workflow workflow = createConditionalWorkflow();

        // 标记 condition 完成，选中 n_true 分支
        WorkflowNode condition = workflow.findNode("condition");
        condition.setStatus(NodeStatus.DONE);
        condition.setConditionResult("n_true");
        condition.setCompletedAt(Instant.now());

        // 手动跳过未选分支（n_false 分支），传入 condition 节点ID
        workflow.skipBranch("n_false", "condition");

        // n_true 应就绪
        List<WorkflowNode> readyNodes = workflow.getReadyNodes();
        assertEquals(1, readyNodes.size());
        assertEquals("n_true", readyNodes.get(0).getId());
        assertEquals(NodeStatus.SKIPPED, workflow.findNode("n_false").getStatus());
    }

    @Test
    void testConditionResultField() {
        WorkflowNode conditionNode = WorkflowNode.builder()
                .id("cond1")
                .description("条件判断")
                .type(NodeType.CONDITION)
                .status(NodeStatus.PENDING)
                .condition("x > 10")
                .build();

        // 默认 conditionResult 为 null
        assertNull(conditionNode.getConditionResult());

        // 设置为 true
        conditionNode.setConditionResult("true");
        assertEquals("true", conditionNode.getConditionResult());

        // 设置为 false
        conditionNode.setConditionResult("false");
        assertEquals("false", conditionNode.getConditionResult());
    }

    /**
     * 创建带条件分支的测试工作流：
     * start -> condition -> n_true (CONDITION_TRUE) -> end
     *                   \n     *                    -> n_false (CONDITION_FALSE) -> end
     */
    private Workflow createConditionalWorkflow() {
        List<WorkflowNode> nodes = new ArrayList<>(Arrays.asList(
                WorkflowNode.builder()
                        .id("start")
                        .description("开始")
                        .type(NodeType.START)
                        .status(NodeStatus.DONE)
                        .retryCount(0)
                        .build(),
                WorkflowNode.builder()
                        .id("condition")
                        .description("条件判断")
                        .type(NodeType.CONDITION)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .condition("test.passed == true")
                        .build(),
                WorkflowNode.builder()
                        .id("n_true")
                        .description("条件为真时的操作")
                        .type(NodeType.ACTION)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build(),
                WorkflowNode.builder()
                        .id("n_false")
                        .description("条件为假时的操作")
                        .type(NodeType.ACTION)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build(),
                WorkflowNode.builder()
                        .id("end")
                        .description("结束")
                        .type(NodeType.END)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build()
        ));

        List<WorkflowEdge> edges = new ArrayList<>(Arrays.asList(
                WorkflowEdge.builder()
                        .id("e1")
                        .from("start")
                        .to("condition")
                        .type(EdgeType.NORMAL)
                        .build(),
                WorkflowEdge.builder()
                        .id("e2")
                        .from("condition")
                        .to("n_true")
                        .type(EdgeType.CONDITION_TRUE)
                        .condition("test.passed == true")
                        .build(),
                WorkflowEdge.builder()
                        .id("e3")
                        .from("condition")
                        .to("n_false")
                        .type(EdgeType.CONDITION_FALSE)
                        .build(),
                WorkflowEdge.builder()
                        .id("e4")
                        .from("n_true")
                        .to("end")
                        .type(EdgeType.NORMAL)
                        .build(),
                WorkflowEdge.builder()
                        .id("e5")
                        .from("n_false")
                        .to("end")
                        .type(EdgeType.NORMAL)
                        .build()
        ));

        return Workflow.builder()
                .id("test-conditional-workflow")
                .sessionId("test-session-2")
                .workspaceHash("test-hash-2")
                .title("条件分支测试工作流")
                .description("这是一个条件分支测试工作流")
                .status(WorkflowStatus.DRAFT)
                .maxRetries(3)
                .nodes(nodes)
                .edges(edges)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}