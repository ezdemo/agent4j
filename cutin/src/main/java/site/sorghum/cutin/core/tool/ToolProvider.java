package site.sorghum.cutin.core.tool;

import java.util.List;

/**
 * 工具提供方 SPI：一个 Provider 可以提供一组相关工具。
 *
 * <p>产品层可通过实现 {@code ToolProvider} 把 MCP、文件系统、Git、终端等
 * 外部能力批量注册进 cutin 的工具注册表。</p>
 */
public interface ToolProvider {

    /** 提供方唯一标识。 */
    String id();

    /** 该提供方管理的全部工具。 */
    List<Tool> tools();
}
