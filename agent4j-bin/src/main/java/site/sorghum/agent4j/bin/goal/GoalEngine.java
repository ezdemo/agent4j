package site.sorghum.agent4j.bin.goal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.model.UserMessage;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
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

    /**
     * 创建新目标：调用 LLM 拆解步骤。
     */
    public Goal createGoal(String description, String verifyCmd, ChatCommandContext ctx) {
        Agent4jAgent agent = ctx.getAgent();
        String sessionId = agent.getSessionStore() != null ? agent.getSessionStore().currentName() : null;
        if (sessionId == null) {
            throw new IllegalStateException("会话未初始化，无法创建目标");
        }
        String workspaceHash = agent.getWorkspaceManager().getCurrentWorkspaceHash();
        if (workspaceHash == null) {
            throw new IllegalStateException("工作区未初始化，请先初始化工作区");
        }

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
                .workspaceHash(workspaceHash)
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
    public void activateGoal(Goal goal, ChatCommandContext ctx) {
        try {
            GoalStore store = ctx.getAgent().getWorkspaceManager().getGoalStore();
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
        } catch (Exception e) {
            log.error("[goal] 激活目标失败: {}", e.getMessage());
            ctx.getAgent().getOutput().onLog(LogLevel.ERROR,
                    "❌ 目标创建失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前会话的目标。
     */
    public Goal getCurrentGoal(ChatCommandContext ctx) {
        try {
            String sessionId = ctx.getAgent().getSessionStore().currentName();
            if (sessionId == null) return null;
            GoalStore store = ctx.getAgent().getWorkspaceManager().getGoalStore();
            return store.findBySession(sessionId);
        } catch (Exception e) {
            log.warn("[goal] 获取当前目标失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 暂停目标。
     */
    public void pause(Goal goal, ChatCommandContext ctx) {
        try {
            goal.setStatus(GoalStatus.PAUSED);
            goal.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getGoalStore().save(goal);
        } catch (Exception e) {
            log.warn("[goal] 暂停目标失败: {}", e.getMessage());
        }
    }

    /**
     * 恢复目标。
     */
    public void resume(Goal goal, ChatCommandContext ctx) {
        try {
            goal.setStatus(GoalStatus.ACTIVE);
            goal.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getGoalStore().save(goal);
        } catch (Exception e) {
            log.warn("[goal] 恢复目标失败: {}", e.getMessage());
        }
    }

    /**
     * 标记某步骤已完成。
     */
    public void markStepDone(Goal goal, int stepIndex, ChatCommandContext ctx) {
        try {
            if (stepIndex < 0 || stepIndex >= goal.getSteps().size()) return;
            GoalStep step = goal.getSteps().get(stepIndex);
            step.setStatus(StepStatus.DONE);
            step.setCompletedAt(Instant.now());
            goal.setUpdatedAt(Instant.now());

            if (goal.isAllDone()) {
                goal.setStatus(GoalStatus.COMPLETED);
                goal.setCompletedAt(Instant.now());
            }

            ctx.getAgent().getWorkspaceManager().getGoalStore().save(goal);
        } catch (Exception e) {
            log.warn("[goal] 标记步骤完成失败: {}", e.getMessage());
        }
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
