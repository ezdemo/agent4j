package site.sorghum.loopra.bin.tool;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.chat.tool.FunctionTool;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 工具扫描工具类 —— 统一管理工具扫描逻辑。
 * <p>
 * 内核层仅定义扫描入口，具体扫描来源（Solon IoC 容器 AgentTool Bean、
 * Skill 文件系统等）由上层模块通过 {@link ToolScanProvider} SPI 提供，
 * 并在启动时调用 {@link #install} 安装。未安装任何提供者时返回空结果，
 * 保证内核在无上层模块的环境下仍可独立使用（如单元测试）。
 * </p>
 * <p>
 * 供 {@link ToolSystemInitializer} 初始化和 {@link ToolRegistry#refresh()} 动态刷新共用，
 * 消除扫描逻辑的重复。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class ToolScanUtil {

    /** 未安装提供者时的空实现 */
    private static final ToolScanProvider EMPTY = new ToolScanProvider() {
        @Override
        public List<FunctionTool> scanTools(Path workspace) {
            return Collections.emptyList();
        }

        @Override
        public String getSkillToolDescription(Path workspace) {
            return "";
        }
    };

    private static volatile ToolScanProvider provider = EMPTY;

    /**
     * 安装工具扫描提供者（由 harness 等上层模块在启动时调用）。
     * 传 null 恢复为空实现。
     */
    public static void install(ToolScanProvider scanProvider) {
        provider = scanProvider != null ? scanProvider : EMPTY;
        log.info("[tool-scan] 已安装工具扫描提供者: {}", provider.getClass().getName());
    }

    /**
     * 扫描并返回所有可用工具。
     *
     * @param workspace 工作区根目录，用于加载 Skill 工具。传 null 则跳过 Skill 扫描。
     * @return AgentTool 列表（未安装提供者时为空列表）
     */
    public static List<FunctionTool> scanTools(Path workspace) {
        return provider.scanTools(workspace);
    }

    /**
     * 返回 Skill 类工具的系统提示词描述。
     */
    public static String getSkillToolDescription(Path workspace) {
        return provider.getSkillToolDescription(workspace);
    }
}
