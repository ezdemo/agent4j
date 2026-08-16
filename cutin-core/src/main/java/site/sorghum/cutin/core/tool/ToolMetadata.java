package site.sorghum.cutin.core.tool;

import java.util.Map;
import java.util.Set;

/**
 * 面向产品层的工具分类元数据。
 *
 * <p>权限、审批、超时等策略插件可以直接依赖这里的只读标记、角色与属性，
 * 而不需要了解各家 Provider 内部的工具注册表结构。</p>
 */
public record ToolMetadata(
    boolean readOnly,
    boolean stormExempt,
    long timeoutMillis,
    Set<String> roles,
    Map<String, Object> attributes
) {

    /** 默认工具元数据：非只读、不受风暴豁免、不限超时、无角色与属性。 */
    public static final ToolMetadata DEFAULT =
        new ToolMetadata(false, false, 0L, Set.of(), Map.of());

    /** 记录构造校验：对角色与属性做不可变拷贝。 */
    public ToolMetadata {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** 生成只读标记不同的新元数据。 */
    public ToolMetadata withReadOnly(boolean readOnly) {
        return new ToolMetadata(readOnly, stormExempt, timeoutMillis, roles, attributes);
    }

    /** 生成风暴豁免标记不同的新元数据。 */
    public ToolMetadata withStormExempt(boolean stormExempt) {
        return new ToolMetadata(readOnly, stormExempt, timeoutMillis, roles, attributes);
    }
}
