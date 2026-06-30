package site.sorghum.agent4j.bin.workflow2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleWorkflow 与 SimpleWorkflowEngine 单元测试。
 *
 * @author Sorghum
 */
class SimpleWorkflowTest {

    @TempDir
    Path tempDir;

    private SimpleWorkflowEngine createEngine() {
        return new SimpleWorkflowEngine();
    }

    private List<SimpleWorkflowEngine.StepDef> stepDefs(String... descriptions) {
        List<SimpleWorkflowEngine.StepDef> list = new ArrayList<>();
        for (String desc : descriptions) {
            list.add(new SimpleWorkflowEngine.StepDef(desc, StepKind.STEP));
        }
        return list;
    }

    @Test
    void testCreateWorkflow() {
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-1", "hash-1", "测试工作流", "描述",
                stepDefs("第一步", "第二步", "第三步"));

        // 验证基本字段
        assertNotNull(wf);
        assertNotNull(wf.getId());
        assertEquals("测试工作流", wf.getTitle());
        assertEquals("描述", wf.getDescription());
        assertEquals("session-1", wf.getSessionId());
        assertEquals("hash-1", wf.getWorkspaceHash());

        // 验证初始状态
        assertEquals("ACTIVE", wf.getStatus());
        assertEquals(1, wf.getCurrentStepIndex());

        // 验证第一步为 RUNNING
        WorkflowStep first = wf.currentStep();
        assertNotNull(first);
        assertEquals("step-1", first.getId());
        assertEquals(StepStatus.RUNNING, first.getStatus());

        // 验证其他步骤为 PENDING
        assertEquals(3, wf.getSteps().size());
        assertEquals("step-2", wf.getSteps().get(1).getId());
        assertEquals(StepStatus.PENDING, wf.getSteps().get(1).getStatus());
        assertEquals("step-3", wf.getSteps().get(2).getId());
        assertEquals(StepStatus.PENDING, wf.getSteps().get(2).getStatus());

        // 验证时间戳
        assertNotNull(wf.getCreatedAt());
        assertNotNull(wf.getUpdatedAt());
    }

    @Test
    void testMarkCurrentDone() {
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-2", "hash-2", "标记完成", "",
                stepDefs("第一步", "第二步"));

        // 标记第一步完成
        SimpleWorkflowEngine.MarkResult result = engine.markCurrentDone(wf, "结果A");

        // 验证返回结果类型
        assertEquals("progress", result.getType());

        // 验证第一步状态
        WorkflowStep step1 = wf.getSteps().get(0);
        assertEquals(StepStatus.DONE, step1.getStatus());
        assertEquals("结果A", step1.getResult());
        assertNotNull(step1.getCompletedAt());

        // 验证自动推进到第二步（index 从 1 变为 2）
        assertEquals(2, wf.getCurrentStepIndex());

        // 验证第二步变为 RUNNING
        WorkflowStep step2 = wf.currentStep();
        assertNotNull(step2);
        assertEquals("step-2", step2.getId());
        assertEquals(StepStatus.RUNNING, step2.getStatus());

        // 验证 updatedAt 已刷新
        assertNotNull(wf.getUpdatedAt());
    }

    @Test
    void testMarkAllDone() {
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-3", "hash-3", "全部完成", "",
                stepDefs("A", "B", "C"));

        // 逐一标记所有步骤完成
        for (int i = 1; i <= 3; i++) {
            engine.markCurrentDone(wf, "第" + i + "步结果");
        }

        // 验证最终状态
        assertEquals("COMPLETED", wf.getStatus());
        assertTrue(wf.isAllDone());
        assertFalse(wf.hasFailed());
        assertNotNull(wf.getCompletedAt());

        // 验证所有步骤为 DONE
        for (WorkflowStep step : wf.getSteps()) {
            assertEquals(StepStatus.DONE, step.getStatus());
        }

        // 验证最后一步仍可通过 currentStep 访问（advance 返回 -1 但未修改 index）
        WorkflowStep lastStep = wf.currentStep();
        assertNotNull(lastStep);
        assertEquals("step-3", lastStep.getId());
        assertEquals(StepStatus.DONE, lastStep.getStatus());
    }

    @Test
    void testMarkCurrentFailed() {
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-4", "hash-4", "标记失败", "",
                stepDefs("第一步", "第二步"));

        // 标记当前步骤失败
        SimpleWorkflowEngine.MarkResult result = engine.markCurrentFailed(wf, "发生错误");

        // 验证返回结果
        assertEquals("failed", result.getType());

        // 验证工作流状态
        assertEquals("FAILED", wf.getStatus());
        assertTrue(wf.hasFailed());

        // 验证当前步骤为 FAILED
        WorkflowStep step = wf.currentStep();
        assertNotNull(step);
        assertEquals(StepStatus.FAILED, step.getStatus());
        assertEquals("发生错误", step.getResult());
        assertNotNull(step.getCompletedAt());

        // 验证后续步骤未受影响（仍为 PENDING）
        assertEquals(StepStatus.PENDING, wf.getSteps().get(1).getStatus());

        // 不是全部完成
        assertFalse(wf.isAllDone());
    }

    @Test
    void testSkipCurrent() {
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-5", "hash-5", "跳过步骤", "",
                stepDefs("第一步", "第二步", "第三步"));

        // 跳过第一步
        SimpleWorkflowEngine.MarkResult result = engine.skipCurrent(wf, "不需要");

        // 验证返回结果
        assertEquals("progress", result.getType());

        // 验证第一步被跳过
        WorkflowStep step1 = wf.getSteps().get(0);
        assertEquals(StepStatus.SKIPPED, step1.getStatus());
        assertEquals("不需要", step1.getResult());
        assertNotNull(step1.getCompletedAt());

        // 验证自动推进到第二步
        assertEquals(2, wf.getCurrentStepIndex());

        // 验证第二步变为 RUNNING
        WorkflowStep step2 = wf.currentStep();
        assertNotNull(step2);
        assertEquals("step-2", step2.getId());
        assertEquals(StepStatus.RUNNING, step2.getStatus());

        // 跳过所有步骤直到完成
        engine.skipCurrent(wf, "跳过");
        engine.skipCurrent(wf, "跳过");

        // 最终状态应为 COMPLETED（引擎将跳过最后一步后的工作流标记为完成）
        assertEquals("COMPLETED", wf.getStatus());
        // 注意：isAllDone() 只检查 DONE 状态的步骤，跳过(SKIPPED)的步骤不计入
        assertFalse(wf.isAllDone());
    }

    @Test
    void testIsAllDone() {
        // 空工作流：所有步骤（零个）都是 DONE → true
        SimpleWorkflow emptyWf = SimpleWorkflow.builder()
                .id("empty")
                .title("空工作流")
                .steps(new ArrayList<>())
                .currentStepIndex(0)
                .status("ACTIVE")
                .build();
        assertTrue(emptyWf.isAllDone());

        // 有步骤但未完成 → false
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-6", "hash-6", "部分完成", "",
                stepDefs("A", "B"));
        assertFalse(wf.isAllDone());

        // 完成一步，仍不是全部完成
        engine.markCurrentDone(wf, "A完成");
        assertFalse(wf.isAllDone());

        // 全部完成 → true
        engine.markCurrentDone(wf, "B完成");
        assertTrue(wf.isAllDone());
    }

    @Test
    void testProgressText() {
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-7", "hash-7", "进度测试", "",
                stepDefs("A", "B", "C", "D", "E"));

        // 初始：还没有 DONE 的步骤（第一步是 RUNNING，不是 DONE）
        assertEquals("0/5 (0%)", wf.progressText());

        // 完成第一步
        engine.markCurrentDone(wf, "A");
        assertEquals("1/5 (20%)", wf.progressText());

        // 完成第二步
        engine.markCurrentDone(wf, "B");
        assertEquals("2/5 (40%)", wf.progressText());

        // 完成第三、第四步
        engine.markCurrentDone(wf, "C");
        engine.markCurrentDone(wf, "D");
        assertEquals("4/5 (80%)", wf.progressText());

        // 全部完成
        engine.markCurrentDone(wf, "E");
        assertEquals("5/5 (100%)", wf.progressText());
    }

    @Test
    void testSaveAndLoad() {
        SimpleWorkflowEngine engine = createEngine();
        SimpleWorkflow wf = engine.createWorkflow(
                "session-save", "hash-save", "持久化测试",
                "测试描述",
                stepDefs("步骤1", "步骤2", "步骤3"));

        // 执行一些操作后再保存
        engine.markCurrentDone(wf, "步骤1完成");
        engine.markCurrentDone(wf, "步骤2完成");

        // 保存到临时目录
        JsonSimpleWorkflowStore store = new JsonSimpleWorkflowStore(tempDir);
        store.save(wf);

        // 重新加载
        SimpleWorkflow loaded = store.findBySession("session-save");
        assertNotNull(loaded);

        // 验证各字段一致性
        assertEquals(wf.getId(), loaded.getId());
        assertEquals(wf.getSessionId(), loaded.getSessionId());
        assertEquals(wf.getWorkspaceHash(), loaded.getWorkspaceHash());
        assertEquals(wf.getTitle(), loaded.getTitle());
        assertEquals(wf.getDescription(), loaded.getDescription());
        assertEquals(wf.getStatus(), loaded.getStatus());
        assertEquals(wf.getCurrentStepIndex(), loaded.getCurrentStepIndex());

        // 验证步骤数量一致
        assertEquals(wf.getSteps().size(), loaded.getSteps().size());

        // 验证每个步骤的字段
        for (int i = 0; i < wf.getSteps().size(); i++) {
            WorkflowStep orig = wf.getSteps().get(i);
            WorkflowStep rest = loaded.getSteps().get(i);
            assertEquals(orig.getId(), rest.getId());
            assertEquals(orig.getDescription(), rest.getDescription());
            assertEquals(orig.getKind(), rest.getKind());
            assertEquals(orig.getStatus(), rest.getStatus());
            assertEquals(orig.getResult(), rest.getResult());
        }

        // 验证业务方法结果一致
        assertEquals(wf.isAllDone(), loaded.isAllDone());
        assertEquals(wf.hasFailed(), loaded.hasFailed());
        assertEquals(wf.isRunning(), loaded.isRunning());
        assertEquals(wf.progressText(), loaded.progressText());
    }

    @Test
    void testNoActiveStep() {
        // 创建没有步骤的工作流
        SimpleWorkflow wf = SimpleWorkflow.builder()
                .id("no-step")
                .title("无步骤")
                .steps(new ArrayList<>())
                .currentStepIndex(0)
                .status("ACTIVE")
                .build();

        // currentStep() 应返回 null
        assertNull(wf.currentStep());

        // advance() 应返回 -1
        assertEquals(-1, wf.advance());

        // isAllDone() 应返回 true（零个步骤全部完成）
        assertTrue(wf.isAllDone());

        // hasFailed() 应返回 false
        assertFalse(wf.hasFailed());

        // progressText() 应显示 0/0 (0%)
        assertEquals("0/0 (0%)", wf.progressText());
    }
}
