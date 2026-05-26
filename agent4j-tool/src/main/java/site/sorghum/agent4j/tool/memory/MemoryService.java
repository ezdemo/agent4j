package site.sorghum.agent4j.tool.memory;

import org.noear.solon.annotation.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 记忆服务 —— 持久化键值记忆的 CRUD。
 *
 * @author Sorghum
 */
@Component
public class MemoryService {

    private static final Path MEMORY_DIR = Paths.get(
            System.getProperty("user.home"), ".agent4j", "memory");

    public String remember(String name, String type, String scope, String description,
                            String content, Integer priority) throws IOException {
        Files.createDirectories(MEMORY_DIR);
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("name", name);
        mem.put("type", type);
        mem.put("scope", scope);
        mem.put("description", description);
        mem.put("content", content);
        if (priority != null) mem.put("priority", priority);
        StringBuilder json = new StringBuilder();
        json.append("{");
        appendJsonString(json, "name", name);
        json.append(",");
        appendJsonString(json, "type", type);
        json.append(",");
        appendJsonString(json, "scope", scope);
        json.append(",");
        appendJsonString(json, "description", description);
        json.append(",");
        appendJsonString(json, "content", content);
        if (priority != null) {
            json.append(",");
            appendJsonString(json, "priority", String.valueOf(priority));
        }
        json.append("}");
        Files.write(memoryFile(name), json.toString().getBytes(StandardCharsets.UTF_8));
        return "saved memory: " + name + " (" + description + ")";
    }

    public String recallMemory(String name) throws IOException {
        Path f = memoryFile(name);
        if (!Files.exists(f)) return "memory '" + name + "' not found";
        return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
    }

    public String forget(String name) throws IOException {
        Path f = memoryFile(name);
        if (Files.deleteIfExists(f)) return "forgotten: " + name;
        return "memory '" + name + "' not found";
    }

    private static Path memoryFile(String name) {
        return MEMORY_DIR.resolve(name.replaceAll("[^a-zA-Z0-9_\\-.]", "_") + ".json");
    }

    private static void appendJsonString(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"");
        if (value != null) {
            sb.append(value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
        }
        sb.append("\"");
    }
}
