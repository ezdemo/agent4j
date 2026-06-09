package site.sorghum.agent4j.bin.goal;

import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JsonlGoalStore — JSONL 格式的目标持久化实现。
 * <p>
 * 存储路径：workspace/{hash}/goals/{sessionId}.jsonl
 * 每个会话一个文件，单行 JSON。
 * 使用与 {@code JsonlSessionStore} 一致的手动序列化模式。
 * </p>
 *
 * @author Sorghum
 */
public class JsonlGoalStore implements GoalStore {

    private static final Logger log = LoggerFactory.getLogger(JsonlGoalStore.class);

    private final Path goalsDir;

    public JsonlGoalStore(Path workspaceDir) {
        this.goalsDir = workspaceDir.resolve("goals");
    }

    private static String sanitize(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    @Override
    public void save(Goal goal) throws IOException {
        Files.createDirectories(goalsDir);
        Path file = goalsDir.resolve(sanitize(goal.getSessionId()) + ".jsonl");
        String json = serializeGoal(goal);
        Files.writeString(file, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("[goal] 已保存目标 {} -> {}", goal.getId(), file);
    }

    @Override
    public Goal findBySession(String sessionId) throws IOException {
        Path file = goalsDir.resolve(sanitize(sessionId) + ".jsonl");
        try {
            String json = Files.readString(file).trim();
            if (json.isEmpty()) return null;
            return deserializeGoal(json);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public List<Goal> findActiveByWorkspace(String workspaceHash) throws IOException {
        List<Goal> active = new ArrayList<>();
        if (!Files.isDirectory(goalsDir)) return active;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(goalsDir, "*.jsonl")) {
            for (Path file : ds) {
                try {
                    String json = Files.readString(file).trim();
                    if (json.isEmpty()) continue;
                    Goal goal = deserializeGoal(json);
                    // 如果传入了 workspaceHash，校验匹配
                    if (workspaceHash != null && !workspaceHash.isEmpty()
                            && !workspaceHash.equals(goal.getWorkspaceHash())) {
                        continue;
                    }
                    if (goal.getStatus() == GoalStatus.ACTIVE || goal.getStatus() == GoalStatus.PAUSED) {
                        active.add(goal);
                    }
                } catch (Exception e) {
                    log.warn("[goal] 读取目标文件失败: {} - {}", file, e.getMessage());
                }
            }
        }
        return active;
    }

    @Override
    public boolean delete(String sessionId) throws IOException {
        Path file = goalsDir.resolve(sanitize(sessionId) + ".jsonl");
        return Files.deleteIfExists(file);
    }

    // ========== 手动序列化/反序列化（与 JsonlSessionStore 一致） ==========

    /**
     * 将 Goal 序列化为 JSON 字符串。
     */
    static String serializeGoal(Goal goal) {
        ONode node = ONode.ofJson("{}");
        node.set("id", goal.getId());
        node.set("sessionId", goal.getSessionId());
        node.set("workspaceHash", goal.getWorkspaceHash());
        node.set("title", goal.getTitle());
        node.set("description", goal.getDescription());
        node.set("status", goal.getStatus() != null ? goal.getStatus().name() : null);
        node.set("maxRetries", goal.getMaxRetries());
        if (goal.getVerifyCommand() != null) {
            node.set("verifyCommand", goal.getVerifyCommand());
        }
        // 时间戳使用 epoch millis
        if (goal.getCreatedAt() != null) {
            node.set("createdAt", goal.getCreatedAt().toEpochMilli());
        }
        if (goal.getUpdatedAt() != null) {
            node.set("updatedAt", goal.getUpdatedAt().toEpochMilli());
        }
        if (goal.getCompletedAt() != null) {
            node.set("completedAt", goal.getCompletedAt().toEpochMilli());
        }
        // 步骤列表
        if (goal.getSteps() != null) {
            ONode stepsArr = node.getOrNew("steps").asArray();
            for (GoalStep step : goal.getSteps()) {
                ONode stepNode = stepsArr.addNew();
                stepNode.set("index", step.getIndex());
                stepNode.set("description", step.getDescription());
                stepNode.set("status", step.getStatus() != null ? step.getStatus().name() : null);
                stepNode.set("retryCount", step.getRetryCount());
                if (step.getLastError() != null) {
                    stepNode.set("lastError", step.getLastError());
                }
                if (step.getCompletedAt() != null) {
                    stepNode.set("completedAt", step.getCompletedAt().toEpochMilli());
                }
            }
        }
        return node.toJson();
    }

    /**
     * 从 JSON 字符串反序列化为 Goal。
     */
    static Goal deserializeGoal(String json) {
        ONode node = ONode.ofJson(json);
        Goal.GoalBuilder builder = Goal.builder()
                .id(node.get("id").getString())
                .sessionId(node.get("sessionId").getString())
                .workspaceHash(node.get("workspaceHash").getString())
                .title(node.get("title").getString())
                .description(node.get("description").getString())
                .status(parseGoalStatus(node.get("status").getString()))
                .maxRetries(node.get("maxRetries").getInt())
                .verifyCommand(node.get("verifyCommand").getString());

        // 时间戳
        if (!node.get("createdAt").isNull()) {
            builder.createdAt(Instant.ofEpochMilli(node.get("createdAt").getLong()));
        }
        if (!node.get("updatedAt").isNull()) {
            builder.updatedAt(Instant.ofEpochMilli(node.get("updatedAt").getLong()));
        }
        if (!node.get("completedAt").isNull()) {
            builder.completedAt(Instant.ofEpochMilli(node.get("completedAt").getLong()));
        }

        // 步骤列表
        List<GoalStep> steps = new ArrayList<>();
        ONode stepsNode = node.get("steps");
        if (stepsNode.isArray()) {
            for (ONode stepNode : stepsNode.getArray()) {
                steps.add(deserializeStep(stepNode));
            }
        }
        builder.steps(steps);

        return builder.build();
    }

    /**
     * 从 ONode 反序列化为 GoalStep。
     */
    private static GoalStep deserializeStep(ONode node) {
        GoalStep.GoalStepBuilder builder = GoalStep.builder()
                .index(node.get("index").getInt())
                .description(node.get("description").getString())
                .status(parseStepStatus(node.get("status").getString()))
                .retryCount(node.get("retryCount").getInt())
                .lastError(node.get("lastError").getString());

        if (!node.get("completedAt").isNull()) {
            builder.completedAt(Instant.ofEpochMilli(node.get("completedAt").getLong()));
        }

        return builder.build();
    }

    private static GoalStatus parseGoalStatus(String name) {
        if (name == null) return GoalStatus.ACTIVE;
        try {
            return GoalStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return GoalStatus.ACTIVE;
        }
    }

    private static StepStatus parseStepStatus(String name) {
        if (name == null) return StepStatus.PENDING;
        try {
            return StepStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return StepStatus.PENDING;
        }
    }
}
