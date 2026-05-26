package site.sorghum.agent4j.bin.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变前缀 —— 缓存优先的核心。
 * <p>
 * 对应 ImmutablePrefix：
 * system prompt + tool specs 在会话期间保持稳定，实现 DeepSeek 前缀缓存命中。
 * 工具增删会改变指纹（下回合缓存 miss），但调整后稳定。
 * </p>
 *
 * @author Sorghum
 */
public class PromptPrefix {

    /** 系统提示词（turn 之间稳定） */
    public final String system;
    /** 工具定义列表（注册后冻结） */
    private final List<Map<String, Object>> toolSpecs;
    /** 缓存指纹 */
    private String fingerprintCache = null;

    public PromptPrefix(String system, List<Map<String, Object>> toolSpecs) {
        this.system = Objects.requireNonNull(system, "system is required");
        this.toolSpecs = new ArrayList<>(toolSpecs); // 冻结
        this.fingerprintCache = computeFingerprint();
    }

    /** 构建消息前缀：[{role: system, content: ...}] */
    public List<Map<String, Object>> toMessages() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        java.util.Map<String, Object> sys = new java.util.LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", system);
        msgs.add(sys);
        return msgs;
    }

    /** 工具定义（stable reference） */
    public List<Map<String, Object>> tools() {
        return toolSpecs;
    }

    /** 替换系统提示词（计划模式切换时用，会导致下一次 API 调用缓存 miss） */
    public void replaceSystem(String newSystem) {
        // system 是 final 字段，但我们需要运行时替换
        try {
            java.lang.reflect.Field f = PromptPrefix.class.getDeclaredField("system");
            f.setAccessible(true);
            f.set(this, newSystem);
        } catch (Exception ignored) {}
        this.fingerprintCache = computeFingerprint();
    }

    /**
     * 获取缓存指纹。
     * 如果 system prompt 或 tools 发生变化，指纹值也会变化，
     * 用于检测前缀缓存是否失效。
     */
    public String fingerprint() {
        return fingerprintCache;
    }

    private String computeFingerprint() {
        String blob = "system=" + system.hashCode() + "|tools=" + toolSpecs.toString().hashCode();
        return Integer.toHexString(blob.hashCode());
    }
}
