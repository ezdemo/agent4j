package site.sorghum.agent4j.bin.goal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.UserMessage;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.LogLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * GoalEngine — 目标执行引擎。
 * <p>
 * 负责目标创建（LLM 拆解步骤）、状态查询、暂停/恢复、巡检派发。
 * 不直接执行步骤——步骤由主 Agent 或巡检子代理执行。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalEngine {

    @Inject
    private WorkspaceManager workspaceManager;

    /**
     * 创建新目标：调用 LLM 拆解步骤。
     */
    public Goal createGoal(String description, String verifyCmd, ChatCommandContext ctx) {
        Agent4jAgent agent = ctx.getAgent();
        String sessionId = agent.getSessionStore().currentName();

        String breakdownPrompt = """
                请将以下目标拆解为 3-8 个具体的、可执行的步骤，每个步骤应是一个独立可验证的任务。
                请以 JSON 数组格式返回，每个元素包含 "description" 字段。
                不要包含任何其他文本，只返回 JSON 数组。
                
                目标：%s
                """.formatted(description);

        String llmResponse;
        try {
            llmResponse = agent.chat(UserMessage.of(breakdownPrompt));
        } catch (Exception e) {
            log.error("[goal] LLM 拆解失败", e);
            llmResponse = """
                    [{"description": "%s"}]
                    """.formatted(description);
        }

        List<GoalStep> steps = parseSteps(llmResponse, description);

        Goal goal = Goal.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .sessionId(sessionId)
                .workspaceHash(workspaceManager.getCurrentWorkspaceHash())
                .title(description.length() > 50 ? description.substring(0, 50) + "..." : description)
                .description(description)
                .status(GoalStatus.ACTIVE)
                .maxRetries(3)
                .verifyCommand(verifyCmd)
                .steps(steps)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return goal;
    }

    /**
     * 持久化目标并输出通知。
     */
    public void activateGoal(Goal goal, ChatCommandContext ctx) throws Exception {
        GoalStore store = workspaceManager.getGoalStore();
        store.save(goal);
        log.info("[goal] 目标已保存: {} - {}", goal.getId(), goal.getTitle());

        String stepsText = IntStream.range(0, goal.getSteps().size())
                .mapToObj(i -> {
                    GoalStep s = goal.getSteps().get(i);
                    return "  [" + (i + 1) + "] " + s.getDescription() + " (" + s.getStatus() + ")";
                })
                .collect(Collectors.joining("\n"));

        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "🎯 目标已创建: " + goal.getTitle() + "\n步骤:\n" + stepsText);
    }

    /**
     * 获取当前会话的目标。
     */
    public Goal getCurrentGoal(ChatCommandContext ctx) throws Exception {
        String sessionId = ctx.getAgent().getSessionStore().currentName();
        GoalStore store = workspaceManager.getGoalStore();
        return store.findBySession(sessionId);
    }

    /**
     * 暂停目标。
     */
    public void pause(Goal goal, ChatCommandContext ctx) throws Exception {
        goal.setStatus(GoalStatus.PAUSED);
        goal.setUpdatedAt(Instant.now());
        workspaceManager.getGoalStore().save(goal);
    }

    /**
     * 恢复目标。
     */
    public void resume(Goal goal, ChatCommandContext ctx) throws Exception {
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setUpdatedAt(Instant.now());
        workspaceManager.getGoalStore().save(goal);
    }

    /**
     * 标记某步骤已完成。
     */
    public void markStepDone(Goal goal, int stepIndex, ChatCommandContext ctx) throws Exception {
        if (stepIndex < 0 || stepIndex >= goal.getSteps().size()) return;
        GoalStep step = goal.getSteps().get(stepIndex);
        step.setStatus(StepStatus.DONE);
        step.setCompletedAt(Instant.now());
        goal.setUpdatedAt(Instant.now());

        if (goal.isAllDone()) {
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(Instant.now());
        }

        workspaceManager.getGoalStore().save(goal);
    }

    /**
     * 生成 spawn 巡检子代理的命令文本（由 GoalCommand 注入到 LLM）。
     */
    public String buildSpawnPatrolCommand(Goal goal) {
        return String.format("""
                请使用 task 工具启动一个巡检子代理，名称为 "goal-patrol-%s"，参数为 "开始巡检目标 %s"。
                
                巡检子代理的 system prompt 如下：
                ---
                %s
                ---
                """,
                goal.getSessionId(),
                goal.getId(),
                GoalPatrolPrompt.build(goal.getWorkspaceHash(), goal.getSessionId()));
    }

    private List<GoalStep> parseSteps(String llmResponse, String fallbackDescription) {
        try {
            String json = llmResponse;
            int startIdx = json.indexOf('[');
            int endIdx = json.lastIndexOf(']');
            if (startIdx >= 0 && endIdx > startIdx) {
                json = json.substring(startIdx, endIdx + 1);
            }

            org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(json);
            if (node.isArray()) {
                List<GoalStep> steps = new ArrayList<>();
                for (int i = 0; i < node.size(); i++) {
                    String desc = node.get(i).get("description").getString();
                    if (desc != null && !desc.isEmpty()) {
                        steps.add(GoalStep.builder()
                                .index(i)
                                .description(desc)
                                .status(StepStatus.PENDING)
                                .retryCount(0)
                                .build());
                    }
                }
                if (!steps.isEmpty()) return steps;
            }
        } catch (Exception e) {
            log.warn("[goal] 解析 LLM 步骤失败，使用 fallback", e);
        }

        return List.of(GoalStep.builder()
                .index(0)
                .description(fallbackDescription)
                .status(StepStatus.PENDING)
                .retryCount(0)
                .build());
    }
}
