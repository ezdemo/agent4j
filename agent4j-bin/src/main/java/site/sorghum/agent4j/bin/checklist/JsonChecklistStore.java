package site.sorghum.agent4j.bin.checklist;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JsonChecklistStore — 基于 JSON 文件的 Checklist 持久化。
 * <p>
 * 每个会话一个 JSON 文件，存放在 {workspaceDir}/.agent4j/checklists/{sessionId}.json。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class JsonChecklistStore implements ChecklistStore {

    private final Path storeDir;

    public JsonChecklistStore(Path workspaceDir) {
        this.storeDir = workspaceDir.resolve(".agent4j").resolve("checklists");
        try {
            Files.createDirectories(this.storeDir);
        } catch (IOException e) {
            log.warn("[checklist] 创建存储目录失败: {}", e.getMessage());
        }
    }

    @Override
    public void save(Checklist checklist) {
        try {
            Path file = getFilePath(checklist.getSessionId());
            String json = serialize(checklist);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[checklist] 保存清单失败: sessionId={}", checklist.getSessionId(), e);
        }
    }

    @Override
    public Checklist findBySession(String sessionId) {
        try {
            Path file = getFilePath(sessionId);
            if (!Files.exists(file)) return null;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return deserialize(json);
        } catch (Exception e) {
            log.warn("[checklist] 读取清单失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    @Override
    public void delete(String sessionId) {
        try {
            Path file = getFilePath(sessionId);
            Files.deleteIfExists(file);
        } catch (Exception e) {
            log.warn("[checklist] 删除清单失败: sessionId={}", sessionId, e);
        }
    }

    private Path getFilePath(String sessionId) {
        String safeName = sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return storeDir.resolve(safeName + ".json");
    }

    private String serialize(Checklist cl) {
        ONode root = new ONode();
        root.set("id", cl.getId());
        root.set("sessionId", cl.getSessionId());
        root.set("workspaceHash", cl.getWorkspaceHash());
        root.set("title", cl.getTitle());
        root.set("description", cl.getDescription());
        root.set("currentStepIndex", cl.getCurrentStepIndex());
        root.set("status", cl.getStatus());

        ONode stepsArr = ONode.ofJson("[]").asArray();
        root.set("steps", stepsArr);
        for (ChecklistStep step : cl.getSteps()) {
            ONode s = stepsArr.addNew();
            s.set("id", step.getId());
            s.set("description", step.getDescription());
            s.set("kind", step.getKind().name());
            s.set("status", step.getStatus().name());
            if (step.getResult() != null) s.set("result", step.getResult());
            if (step.getCreatedAt() != null) s.set("createdAt", step.getCreatedAt().toString());
            if (step.getCompletedAt() != null) s.set("completedAt", step.getCompletedAt().toString());
        }

        if (cl.getCreatedAt() != null) root.set("createdAt", cl.getCreatedAt().toString());
        if (cl.getUpdatedAt() != null) root.set("updatedAt", cl.getUpdatedAt().toString());
        if (cl.getCompletedAt() != null) root.set("completedAt", cl.getCompletedAt().toString());

        return root.toJson();
    }

    private Checklist deserialize(String json) {
        ONode root = ONode.ofJson(json);

        Checklist.ChecklistBuilder cb = Checklist.builder()
                .id(root.get("id").getString())
                .sessionId(root.get("sessionId").getString())
                .workspaceHash(root.get("workspaceHash").getString())
                .title(root.get("title").getString())
                .description(root.get("description").getString())
                .currentStepIndex(root.get("currentStepIndex").getInt())
                .status(root.get("status").getString());

        if (root.get("createdAt").isObject()) {
            String ca = root.get("createdAt").getString();
            if (ca != null) cb.createdAt(java.time.Instant.parse(ca));
        }
        if (root.get("updatedAt").isObject()) {
            String ua = root.get("updatedAt").getString();
            if (ua != null) cb.updatedAt(java.time.Instant.parse(ua));
        }
        if (root.get("completedAt").isObject()) {
            String ca2 = root.get("completedAt").getString();
            if (ca2 != null) cb.completedAt(java.time.Instant.parse(ca2));
        }

        var steps = new java.util.ArrayList<ChecklistStep>();
        ONode stepsArr = root.get("steps");
        if (stepsArr.isArray()) {
            for (int i = 0; i < stepsArr.getArray().size(); i++) {
                ONode s = stepsArr.getArray().get(i);
                ChecklistStep.ChecklistStepBuilder sb = ChecklistStep.builder()
                        .id(s.get("id").getString())
                        .description(s.get("description").getString())
                        .kind(StepKind.valueOf(s.get("kind").getString()))
                        .status(StepStatus.valueOf(s.get("status").getString()))
                        .result(s.get("result").getString());
                if (s.get("createdAt").isObject()) {
                    String sca = s.get("createdAt").getString();
                    if (sca != null) sb.createdAt(java.time.Instant.parse(sca));
                }
                if (s.get("completedAt").isObject()) {
                    String sca2 = s.get("completedAt").getString();
                    if (sca2 != null) sb.completedAt(java.time.Instant.parse(sca2));
                }
                steps.add(sb.build());
            }
        }
        cb.steps(steps);

        return cb.build();
    }
}
