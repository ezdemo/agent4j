package site.sorghum.agent4j.bin.workflow2;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JsonSimpleWorkflowStore — 基于 JSON 文件的 SimpleWorkflow 持久化。
 * <p>
 * 每个会话一个 JSON 文件，存放在 {workspaceDir}/.agent4j/workflows/{sessionId}.json。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class JsonSimpleWorkflowStore implements SimpleWorkflowStore {

    private final Path storeDir;

    public JsonSimpleWorkflowStore(Path workspaceDir) {
        this.storeDir = workspaceDir.resolve(".agent4j").resolve("workflows2");
        try {
            Files.createDirectories(this.storeDir);
        } catch (IOException e) {
            log.warn("[workflow2] 创建存储目录失败: {}", e.getMessage());
        }
    }

    @Override
    public void save(SimpleWorkflow workflow) {
        try {
            Path file = getFilePath(workflow.getSessionId());
            String json = serialize(workflow);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[workflow2] 保存工作流失败: sessionId={}", workflow.getSessionId(), e);
        }
    }

    @Override
    public SimpleWorkflow findBySession(String sessionId) {
        try {
            Path file = getFilePath(sessionId);
            if (!Files.exists(file)) return null;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return deserialize(json);
        } catch (Exception e) {
            log.warn("[workflow2] 读取工作流失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    @Override
    public void delete(String sessionId) {
        try {
            Path file = getFilePath(sessionId);
            Files.deleteIfExists(file);
        } catch (Exception e) {
            log.warn("[workflow2] 删除工作流失败: sessionId={}", sessionId, e);
        }
    }

    private Path getFilePath(String sessionId) {
        String safeName = sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return storeDir.resolve(safeName + ".json");
    }

    private String serialize(SimpleWorkflow wf) {
        ONode root = new ONode();
        root.set("id", wf.getId());
        root.set("sessionId", wf.getSessionId());
        root.set("workspaceHash", wf.getWorkspaceHash());
        root.set("title", wf.getTitle());
        root.set("description", wf.getDescription());
        root.set("currentStepIndex", wf.getCurrentStepIndex());
        root.set("status", wf.getStatus());

        ONode stepsArr = ONode.ofJson("[]").asArray();
        root.set("steps", stepsArr);
        for (WorkflowStep step : wf.getSteps()) {
            ONode s = stepsArr.addNew();
            s.set("id", step.getId());
            s.set("description", step.getDescription());
            s.set("kind", step.getKind().name());
            s.set("status", step.getStatus().name());
            if (step.getResult() != null) s.set("result", step.getResult());
            if (step.getCreatedAt() != null) s.set("createdAt", step.getCreatedAt().toString());
            if (step.getCompletedAt() != null) s.set("completedAt", step.getCompletedAt().toString());
        }

        if (wf.getCreatedAt() != null) root.set("createdAt", wf.getCreatedAt().toString());
        if (wf.getUpdatedAt() != null) root.set("updatedAt", wf.getUpdatedAt().toString());
        if (wf.getCompletedAt() != null) root.set("completedAt", wf.getCompletedAt().toString());

        return root.toJson();
    }

    private SimpleWorkflow deserialize(String json) {
        ONode root = ONode.ofJson(json);

        SimpleWorkflow.SimpleWorkflowBuilder wb = SimpleWorkflow.builder()
                .id(root.get("id").getString())
                .sessionId(root.get("sessionId").getString())
                .workspaceHash(root.get("workspaceHash").getString())
                .title(root.get("title").getString())
                .description(root.get("description").getString())
                .currentStepIndex(root.get("currentStepIndex").getInt())
                .status(root.get("status").getString());

        if (root.get("createdAt").isObject()) {
            String ca = root.get("createdAt").getString();
            if (ca != null) wb.createdAt(java.time.Instant.parse(ca));
        }
        if (root.get("updatedAt").isObject()) {
            String ua = root.get("updatedAt").getString();
            if (ua != null) wb.updatedAt(java.time.Instant.parse(ua));
        }
        if (root.get("completedAt").isObject()) {
            String ca2 = root.get("completedAt").getString();
            if (ca2 != null) wb.completedAt(java.time.Instant.parse(ca2));
        }

        var steps = new java.util.ArrayList<WorkflowStep>();
        ONode stepsArr = root.get("steps");
        if (stepsArr.isArray()) {
            for (int i = 0; i < stepsArr.getArray().size(); i++) {
                ONode s = stepsArr.getArray().get(i);
                WorkflowStep.WorkflowStepBuilder sb = WorkflowStep.builder()
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
        wb.steps(steps);

        return wb.build();
    }
}
