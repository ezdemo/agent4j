package site.sorghum.loopra.bin.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 全局用户 ID 提供者。
 * <p>
 * 启动时确保 {@code ~/.loopra/.user_id} 存在，并在内存中缓存该值。
 * 该 ID 用于 AI 请求的身份标识，避免将用户身份与会话 ID 混用。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public final class UserIdProvider {

    private static volatile String userId;

    private UserIdProvider() {
    }

    /**
     * 获取全局用户 ID。
     * <p>
     * 首次访问时自动从 {@code ~/.loopra/.user_id} 加载；若文件不存在、内容为空或非法，则重新生成并持久化。
     * </p>
     *
     * @return 全局用户 ID
     */
    public static String getUserId() {
        if (userId != null) {
            return userId;
        }
        synchronized (UserIdProvider.class) {
            if (userId != null) {
                return userId;
            }
            userId = loadOrCreateUserId();
            return userId;
        }
    }

    /**
     * 仅供测试重置内部缓存。
     */
    static void resetForTesting() {
        userId = null;
    }

    private static String loadOrCreateUserId() {
        Path configDir = Paths.get(System.getProperty("user.home"), ".loopra");
        Path userIdPath = configDir.resolve(".user_id");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            log.warn("[user] 创建 ~/.loopra 目录失败: {}", e.getMessage());
        }

        String persisted = readPersistedUserId(userIdPath);
        if (isValidUserId(persisted)) {
            log.debug("[user] 使用已有用户 ID: {}", userIdPath);
            return persisted;
        }

        String generated = UUID.randomUUID().toString();
        writePersistedUserId(userIdPath, generated);
        log.info("[user] 已生成并保存用户 ID: {}", userIdPath);
        return generated;
    }

    private static String readPersistedUserId(Path userIdPath) {
        try {
            if (!Files.exists(userIdPath)) {
                return null;
            }
            String content = Files.readString(userIdPath, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            log.warn("[user] 读取用户 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    private static void writePersistedUserId(Path userIdPath, String value) {
        try {
            Files.writeString(userIdPath, value + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("[user] 持久化用户 ID 失败: {}", e.getMessage(), e);
        }
    }

    private static boolean isValidUserId(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("[user] 发现非法用户 ID，重新生成");
            return false;
        }
    }
}
