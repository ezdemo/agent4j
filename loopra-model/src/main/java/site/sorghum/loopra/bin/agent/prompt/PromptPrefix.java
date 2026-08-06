package site.sorghum.loopra.bin.agent.prompt;

import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * 工具定义列表（注册后冻结）
     */
    private final ONode toolSpecs;
    /**
     * 系统提示词（turn 之间稳定，计划模式切换时会被替换）
     */
    public String system;

    public PromptPrefix(String system, ONode toolSpecs) {
        this.system = Objects.requireNonNull(system, "system is required");
        this.toolSpecs = toolSpecs; // 冻结
    }

    /**
     * 构建消息前缀：[{role: system, content: ...}]
     */
    public List<LoopraChatMessage> toMessages() {
        List<LoopraChatMessage> msgs = new ArrayList<>();
        msgs.add(LoopraChatMessage.ofSystem(system));
        return msgs;
    }

    private String computeFingerprint() {
        String blob = "system=" + system.hashCode() + "|tools=" + toolSpecs.toString().hashCode();
        return Integer.toHexString(blob.hashCode());
    }
}
