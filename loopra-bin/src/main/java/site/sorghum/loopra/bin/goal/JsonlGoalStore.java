package site.sorghum.loopra.bin.goal;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 每会话一个 JSON 快照的 Goal 存储。
 * 文件扩展名保留 .jsonl，兼容早期安装；内容始终是一条完整 JSON 记录。
 */
@Slf4j
public class JsonlGoalStore implements GoalStore {

    private final Path goalsDir;

    public JsonlGoalStore(Path workspaceDir) {
        this.goalsDir = workspaceDir.resolve("goals");
    }

    @Override
    public void save(Goal goal) throws IOException {
        Files.createDirectories(goalsDir);
        Path target = fileFor(goal.getSessionId());
        Path temporary = Files.createTempFile(goalsDir, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, serializeGoal(goal) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public Goal findBySession(String sessionId) throws IOException {
        Path file = fileFor(sessionId);
        if (!Files.exists(file)) return null;
        String json = Files.readString(file, StandardCharsets.UTF_8).trim();
        return json.isEmpty() ? null : deserializeGoal(json);
    }

    @Override
    public List<Goal> findActiveByWorkspace(String workspaceHash) throws IOException {
        List<Goal> goals = new ArrayList<>();
        if (!Files.isDirectory(goalsDir)) return goals;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(goalsDir, "*.jsonl")) {
            for (Path file : files) {
                try {
                    Goal goal = deserializeGoal(Files.readString(file, StandardCharsets.UTF_8).trim());
                    if ((workspaceHash == null || workspaceHash.equals(goal.getWorkspaceHash())) && goal.isOpen()) {
                        goals.add(goal);
                    }
                } catch (Exception e) {
                    log.warn("[goal] 忽略无法读取的目标快照 {}: {}", file, e.getMessage());
                }
            }
        }
        return goals;
    }

    @Override
    public boolean delete(String sessionId) throws IOException {
        return Files.deleteIfExists(fileFor(sessionId));
    }

    static String serializeGoal(Goal goal) {
        ONode root = ONode.ofJson("{}");
        root.set("id", goal.getId());
        root.set("sessionId", goal.getSessionId());
        root.set("workspaceHash", goal.getWorkspaceHash());
        root.set("title", goal.getTitle());
        root.set("description", goal.getDescription());
        root.set("status", goal.getStatus().name());
        root.set("verifyCommand", goal.getVerifyCommand());
        root.set("completionSummary", goal.getCompletionSummary());
        root.set("blockedReason", goal.getBlockedReason());
        setInstant(root, "createdAt", goal.getCreatedAt());
        setInstant(root, "updatedAt", goal.getUpdatedAt());
        setInstant(root, "completedAt", goal.getCompletedAt());

        ONode steps = root.getOrNew("steps").asArray();
        for (GoalStep step : goal.getSteps()) {
            ONode item = steps.addNew();
            item.set("index", step.getIndex());
            item.set("description", step.getDescription());
            item.set("status", step.getStatus().name());
            item.set("evidence", step.getEvidence());
            setInstant(item, "startedAt", step.getStartedAt());
            setInstant(item, "completedAt", step.getCompletedAt());
        }
        return root.toJson();
    }

    static Goal deserializeGoal(String json) {
        ONode root = ONode.ofJson(json);
        Goal.GoalBuilder builder = Goal.builder()
                .id(root.get("id").getString())
                .sessionId(root.get("sessionId").getString())
                .workspaceHash(root.get("workspaceHash").getString())
                .title(root.get("title").getString())
                .description(root.get("description").getString())
                .status(parseGoalStatus(root.get("status").getString()))
                .verifyCommand(root.get("verifyCommand").getString())
                .completionSummary(root.get("completionSummary").getString())
                .blockedReason(root.get("blockedReason").getString())
                .createdAt(getInstant(root, "createdAt"))
                .updatedAt(getInstant(root, "updatedAt"))
                .completedAt(getInstant(root, "completedAt"));

        List<GoalStep> steps = new ArrayList<>();
        ONode items = root.get("steps");
        if (items.isArray()) {
            for (ONode item : items.getArray()) {
                steps.add(GoalStep.builder()
                        .index(item.get("index").getInt())
                        .description(item.get("description").getString())
                        .status(parseStepStatus(item.get("status").getString()))
                        .evidence(firstNonBlank(item.get("evidence").getString(), item.get("lastError").getString()))
                        .startedAt(getInstant(item, "startedAt"))
                        .completedAt(getInstant(item, "completedAt"))
                        .build());
            }
        }
        return builder.steps(steps).build();
    }

    private Path fileFor(String sessionId) {
        String safe = sessionId == null ? "unknown" : sessionId.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return goalsDir.resolve(safe + ".jsonl");
    }

    private static void setInstant(ONode node, String key, Instant value) {
        if (value != null) node.set(key, value.toEpochMilli());
    }

    private static Instant getInstant(ONode node, String key) {
        ONode value = node.get(key);
        return value == null || value.isNull() ? null : Instant.ofEpochMilli(value.getLong());
    }

    private static GoalStatus parseGoalStatus(String value) {
        try {
            return GoalStatus.valueOf(value);
        } catch (Exception ignored) {
            return GoalStatus.ACTIVE;
        }
    }

    private static StepStatus parseStepStatus(String value) {
        if ("FAILED".equals(value)) return StepStatus.BLOCKED;
        try {
            return StepStatus.valueOf(value);
        } catch (Exception ignored) {
            return StepStatus.PENDING;
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
}
