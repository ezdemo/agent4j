package site.sorghum.agent4j.bin.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.agent.UserMessage;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;
import site.sorghum.agent4j.tool.LogLevel;

/**
 * /init — 自动分析项目生成 agent4j.md。
 * <p>
 * 使用 LLM 全面分析项目代码库，生成包含项目概述、目录结构、
 * 技术栈、架构设计、工具列表和运行方式的项目文档。
 * </p>
 *
 * @author Sorghum
 */
@Component
@Slf4j
public class InitCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "init";
    }

    @Override
    public String getDescription() {
        return "/init        自动分析项目生成 agent4j.md";
    }

    @Override
    public String getCommandType() {
        return "tool";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        log.info("正在分析项目...\n");
        context.getAgent().getOutput().onReasoning("正在分析项目...");
        String prompt = """
                请全面分析这个项目的代码库，生成 agent4j.md 放在项目根目录。
                
                要求：
                1. 用 tree / glob 了解项目结构
                2. 阅读核心源文件
                3. agent4j.md 应包含：项目概述、目录结构树、技术栈表格、架构设计、全部工具列表、运行方式
                4. 用 write_file 写入 agent4j.md
                5. 完成后一句话总结""";
        try {
            String reply = context.getAgent().chat(UserMessage.of(prompt));
            context.getAgent().getOutput().onReasoning(reply);
        } catch (Exception e) {
            context.getAgent().getOutput().onLog(LogLevel.ERROR, "分析项目失败: " + e.getMessage());
        }
        return CommandResult.CONTINUE;
    }
}
