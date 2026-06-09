package site.sorghum.agent4j.bin.goal;

import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JsonlGoalStore — JSONL 格式的目标持久化实现。
 * <p>
 * 存储路径：workspace/{hash}/goals/{sessionId}.jsonl
 * 每个会话一个文件，单行 JSON。
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

    @Override
    public void save(Goal goal) throws IOException {
        Files.createDirectories(goalsDir);
        Path file = goalsDir.resolve(goal.getSessionId() + ".jsonl");
        String json = ONode.serialize(goal);
        Files.writeString(file, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("[goal] 已保存目标 {} -> {}", goal.getId(), file);
    }

    @Override
    public Goal findBySession(String sessionId) throws IOException {
        Path file = goalsDir.resolve(sessionId + ".jsonl");
        if (!Files.exists(file)) return null;
        String json = Files.readString(file).trim();
        if (json.isEmpty()) return null;
        return ONode.deserialize(json, Goal.class);
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
                    Goal goal = ONode.deserialize(json, Goal.class);
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
        Path file = goalsDir.resolve(sessionId + ".jsonl");
        return Files.deleteIfExists(file);
    }
}
