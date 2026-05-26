package site.sorghum.agent4j.bin.command;

import site.sorghum.agent4j.bin.agent.Agent4jAgent;

import java.util.Scanner;

/**
 * ChatCommandContext — 命令执行上下文。
 * <p>
 * 封装命令执行所需的全部依赖，由 {@link ChatCommandRegistry} 在匹配命令后构建并传入。
 * 命令实现通过此上下文访问 Agent4jAgent、Scanner 以及退出机制。
 * </p>
 *
 * @author Sorghum
 */
public class ChatCommandContext {

    private final Agent4jAgent agent;
    private final Scanner scanner;
    private final Runnable exitHandler;

    /**
     * 创建命令执行上下文。
     *
     * @param agent      Agent4jAgent 实例
     * @param scanner    用户输入扫描器
     * @param exitHandler 退出回调（由主循环设置，如关闭资源、打印再见等）
     */
    public ChatCommandContext(Agent4jAgent agent, Scanner scanner, Runnable exitHandler) {
        this.agent = agent;
        this.scanner = scanner;
        this.exitHandler = exitHandler;
    }

    /** 获取 Agent4jAgent 实例 */
    public Agent4jAgent getAgent() {
        return agent;
    }

    /** 获取用户输入扫描器 */
    public Scanner getScanner() {
        return scanner;
    }

    /** 触发退出回调（通常由 /exit 命令调用） */
    public void exit() {
        if (exitHandler != null) {
            exitHandler.run();
        }
    }
}
