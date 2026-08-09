package site.sorghum.loopra.bin.requirement;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 需求池存储 —— 全局需求 JSON 文件（{@code ~/.loopra/requirements/requirements.json}）+ 内存缓存。
 * <p>
 * 需求池为全局资源（跨项目共享），采用单文件数组格式存储；
 * 读多写少，使用读写锁保护，读写均同步落盘保证一致性。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class RequirementStore {

    private static final Path BASE_DIR = Paths.get(
            System.getProperty("user.home"), ".loopra", "requirements");

    private final Path baseDir;
    private final Map<String, Requirement> cache = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 默认构造函数，使用全局需求目录（{@code ~/.loopra/requirements}）。
     */
    public RequirementStore() {
        this(BASE_DIR);
    }

    /**
     * 指定需求目录的构造函数（测试或自定义部署场景）。
     */
    public RequirementStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    private Path getFile() {
        return baseDir.resolve("requirements.json");
    }

    /**
     * 加载全部需求（首次访问时从磁盘读取，之后走内存缓存）。
     *
     * @return 需求列表（保持创建顺序）
     */
    public List<Requirement> loadAll() {
        lock.readLock().lock();
        try {
            if (!cache.isEmpty()) {
                return new ArrayList<>(cache.values());
            }
        } finally {
            lock.readLock().unlock();
        }

        // 缓存为空，从磁盘加载
        lock.writeLock().lock();
        try {
            // 双重检查
            if (!cache.isEmpty()) {
                return new ArrayList<>(cache.values());
            }

            Path file = getFile();
            Map<String, Requirement> loaded = new LinkedHashMap<>();
            if (Files.exists(file)) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    ONode node = ONode.ofJson(json);
                    if (node.isArray()) {
                        for (ONode item : node.getArray()) {
                            Requirement requirement = item.toBean(Requirement.class);
                            if (requirement != null && requirement.getId() != null) {
                                loaded.put(requirement.getId(), requirement);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[requirement] 加载需求列表失败: {}", e.getMessage());
                }
            }
            cache.putAll(loaded);
            return new ArrayList<>(loaded.values());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取单个需求，不存在返回 null。
     */
    public Requirement get(String id) {
        for (Requirement requirement : loadAll()) {
            if (requirement.getId().equals(id)) {
                return requirement;
            }
        }
        return null;
    }

    /**
     * 新增或更新需求并落盘。
     *
     * @return 已持久化的需求
     */
    public Requirement upsert(Requirement requirement) {
        lock.writeLock().lock();
        try {
            // 确保缓存已加载，避免覆盖磁盘数据
            loadAll();
            cache.put(requirement.getId(), requirement);
            save();
            return requirement;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 删除需求（不级联删除其执行会话）。
     *
     * @return true 表示存在并已删除
     */
    public boolean remove(String id) {
        lock.writeLock().lock();
        try {
            loadAll();
            boolean removed = cache.remove(id) != null;
            if (removed) {
                save();
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 将内存缓存全量写入磁盘文件。
     */
    private void save() {
        Path file = getFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            log.warn("[requirement] 创建目录失败: {}", e.getMessage());
            return;
        }

        ONode array = ONode.ofJson("[]").asArray();
        for (Requirement requirement : cache.values()) {
            array.add(ONode.ofBean(requirement));
        }

        try {
            Files.writeString(file, array.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[requirement] 写入需求文件失败: {}", e.getMessage());
        }
    }
}
