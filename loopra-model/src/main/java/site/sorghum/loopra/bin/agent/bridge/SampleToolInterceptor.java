package site.sorghum.loopra.bin.agent.bridge;

import org.noear.solon.ai.chat.interceptor.ChatInterceptor;
import org.noear.solon.ai.chat.interceptor.ToolChain;
import org.noear.solon.ai.chat.interceptor.ToolRequest;
import org.noear.solon.ai.chat.tool.ToolResult;

import java.util.Map;
import java.util.Set;

/**
 * 示例：solon-ai 工具执行拦截器。
 * <p>
 * solon-ai 在每次工具执行前都会构建 {@link ToolChain} 并依次调用拦截器的
 * {@link #interceptTool(ToolRequest, ToolChain)}（见 {@code ChatRequestDescDefault.doToolCall}）。
 * 本示例演示三个能力：
 * <ul>
 *   <li><b>拒绝执行</b>：命中黑名单的工具直接返回 {@link ToolResult#error(String)}，工具不会被执行；</li>
 *   <li><b>参数改写</b>：{@link ToolRequest#getArgs()} 允许修改，可注入/修正工具入参；</li>
 *   <li><b>放行</b>：调用 {@code chain.doIntercept(req)} 交给下一个拦截器，最终执行 {@code FunctionTool.call(args)}。</li>
 * </ul>
 * 注意：solon-ai 的注册 API（{@code interceptorAdd}）只接受 {@link ChatInterceptor}，
 * 而 {@code ChatInterceptor extends ToolInterceptor}，因此示例直接实现 {@link ChatInterceptor}，
 * 仅重写工具拦截方法 {@link #interceptTool}（其余 onPrepare / interceptCall / interceptStream 保持默认）。
 *
 * <p>注册示例（二选一）：</p>
 * <pre>{@code
 * // 1) 模型级（全局，构造 ChatModel 时注册）：
 * ChatModel model = ChatModel.of("...").defaultInterceptorAdd(new SampleToolInterceptor()).build();
 *
 * // 2) 请求级（仅本次请求生效）：
 * ChatResponse resp = model.prompt(prompt)
 *         .options(o -> o.interceptorAdd(new SampleToolInterceptor()))
 *         .call();
 * }</pre>
 *
 * @author Sorghum
 */
public class SampleToolInterceptor implements ChatInterceptor {

    /** 命中即拒绝执行的工具名单 */
    private final Set<String> deniedTools;

    public SampleToolInterceptor() {
        this(Set.of("rm", "drop_table"));
    }

    public SampleToolInterceptor(Set<String> deniedTools) {
        this.deniedTools = deniedTools;
    }

    @Override
    public ToolResult interceptTool(ToolRequest req, ToolChain chain) throws Throwable {
        String toolName = chain.getTool().name();
        Map<String, Object> args = req.getArgs();

        // 1. 拒绝规则：黑名单工具直接返回错误结果（工具不会被执行）
        if (deniedTools.contains(toolName)) {
            return ToolResult.error("[" + toolName + "] 已被拦截器拒绝执行");
        }

        // 2. 参数改写示例：为 bash 命令注入安全前缀（req.getArgs() 允许修改）
        if ("bash".equals(toolName) && args.get("command") instanceof String command) {
            args.put("command", "echo [intercepted] " + command);
        }

        // 3. 放行：交给下一个拦截器，最终执行 FunctionTool.call(args)
        return chain.doIntercept(req);
    }
}
