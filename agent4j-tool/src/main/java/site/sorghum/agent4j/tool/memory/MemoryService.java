package site.sorghum.agent4j.tool.memory;

import org.noear.snack4.ONode;
import org.noear.solon.annotation.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 记忆服务 —— 持久化键值记忆的 CRUD。
 *
 * @author Sorghum
 */
@Component
public class MemoryService {

    private static final Path MEMORY_DIR = Paths.get(
            System.getProperty("user.home"), ".agent4j", "memory");

    private static Path memoryFile(String name) {
        return MEMORY_DIR.resolve(name.replaceAll("[^a-zA-Z0-9_\\-.]", "_") + ".json");
    }

    public String remember(String name, String type, String scope, String description,
                           String content, Integer priority) throws IOException {
        Files.createDirectories(MEMORY_DIR);
        ONode json = new ONode()
                .set("name", name)
                .set("type", type)
                .set("scope", scope)
                .set("description", description)
                .set("content", content);
        if (priority != null) {
            json.set("priority", priority);
        }
        Files.writeString(memoryFile(name), json.toJson());
        return "saved memory: " + name + " (" + description + ")";
    }

    public String recallMemory(String name) throws IOException {
        Path f = memoryFile(name);
        if (!Files.exists(f)) return "memory '" + name + "' not found";
        return Files.readString(f);
    }

    public String forget(String name) throws IOException {
        Path f = memoryFile(name);
        if (Files.deleteIfExists(f)) return "forgotten: " + name;
        return "memory '" + name + "' not found";
    }
}
