package site.sorghum.loopra.bin.agent.spi;

import java.util.Map;
import java.util.Set;

/**
 * 工具启用策略 SPI —— ToolRegistry 借此读取"禁用工具"和"只读覆盖"策略。
 * <p>
 * 内核不感知策略的存储与热更新实现；由上层模块（loopra-harness 基于 ConfigService）
 * 提供实现并注入。未设置（{@code null}）时 ToolRegistry 回退到静态快照 / 空策略。
 * </p>
 *
 * @author Sorghum
 */
public interface ToolPolicyProvider {

    /**
     * 当前被禁用的工具名称集合。
     *
     * @return 禁用工具名集合；无禁用返回空集
     */
    Set<String> disabledTools();

    /**
     * 工具只读属性覆盖表（toolName -&gt; readOnly）。
     *
     * @return 只读覆盖映射；无覆盖返回空映射
     */
    Map<String, Boolean> toolReadOnlyOverrides();
}
