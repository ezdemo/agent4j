package site.sorghum.loopra.bin.tool;

import org.noear.solon.ai.chat.tool.FunctionTool;

import java.nio.file.Path;
import java.util.List;

/**
 * 工具扫描提供者 SPI —— 将"工具从哪里来"与核心工具系统解耦。
 * <p>
 * loopra-model 内核不依赖具体的工具来源（Solon IoC / Skill 文件系统等）；
 * 由 harness 等上层模块提供实现并通过 {@link ToolScanUtil#install} 安装。
 * </p>
 *
 * @author Sorghum
 */
public interface ToolScanProvider {

    /**
     * 扫描并返回所有可用工具（建议按稳定顺序排序）。
     *
     * @param workspace 项目根目录，可为 null
     */
    List<FunctionTool> scanTools(Path workspace);

    /**
     * 返回 Skill 类工具的系统提示词描述（追加到系统提示词），可为空字符串。
     *
     * @param workspace 项目根目录，可为 null
     */
    String getSkillToolDescription(Path workspace);
}
