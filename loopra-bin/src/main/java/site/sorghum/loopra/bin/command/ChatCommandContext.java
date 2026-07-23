package site.sorghum.loopra.bin.command;

import lombok.Getter;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;

import java.util.Scanner;

/**
 * ChatCommandContext — 命令执行上下文。
 * <p>
 * 封装命令执行所需的全部依赖，由 {@link ChatCommandRegistry} 在匹配命令后构建并传入。
 * 命令实现通过此上下文访问 LoopraAgent、Scanner 以及退出机制。
 * </p>
 *
 * @author Sorghum
 */
public class ChatCommandContext {

    /**
     * -- GETTER --
     *  获取 LoopraAgent 实例
     */
    @Getter
    private final LoopraAgent agent;
    /**
     * -- GETTER --
     *  获取用户输入扫描器
     */
    @Getter
    private final Scanner scanner;
    private final Runnable exitHandler;

    /**
     * 创建命令执行上下文。
     *
     * @param agent       LoopraAgent 实例
     * @param scanner     用户输入扫描器
     * @param exitHandler 退出回调（由主循环设置，如关闭资源、打印再见等）
     */
    public ChatCommandContext(LoopraAgent agent, Scanner scanner, Runnable exitHandler) {
        this.agent = agent;
        this.scanner = scanner;
        this.exitHandler = exitHandler;
    }

}
