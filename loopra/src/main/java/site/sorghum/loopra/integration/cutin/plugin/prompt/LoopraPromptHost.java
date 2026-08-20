package site.sorghum.loopra.integration.cutin.plugin.prompt;

import site.sorghum.loopra.bin.agent.environment.SessionEnvironment;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.nio.file.Path;

/**
 * Prompt 插件依赖的宿主能力。
 * <p>
 * 由 {@code AgentLoop} 实现，插件通过 {@code PluginContext.getBean(LoopraPromptHost.class)}
 * 拿到环境与工具信息，避免直接依赖 AgentLoop。
 * </p>
 */
public interface LoopraPromptHost {

    SessionEnvironment environment();

    default Path workspace() {
        SessionEnvironment env = environment();
        if (env == null || env.executionRoot() == null) return null;
        return env.executionRoot();
    }

    ToolRegistry toolRegistry();

    PromptRegistry promptRegistry();

    /**
     * 工具协作约定的动态尾部（Goal 指令、Plan Mode 指令等），
     * 由 {@code AgentLoop} 按当前会话状态实时提供；无动态内容时返回空字符串。
     */
    default String dynamicToolContractTail() {
        return "";
    }
}
