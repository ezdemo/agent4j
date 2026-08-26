package site.sorghum.loopra.integration.extpack;

import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolDefinition;
import site.sorghum.cutin.core.tool.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * 拓展包桥接示例：供 {@link ExtPackStoreTest} 动态打包进 jar，通过
 * ServiceLoader 发现后贡献一个工具与一个拦截器。
 */
public class SampleExtPackBridge implements LoopraExtPackBridge {

    /** 无参构造，供 ServiceLoader 实例化。 */
    public SampleExtPackBridge() {
    }

    @Override
    public String id() {
        return "sample-bridge";
    }

    @Override
    public List<Tool> tools() {
        return List.of(new SampleExtTool());
    }

    @Override
    public List<RegisteredInterceptor> interceptors() {
        return List.of(new RegisteredInterceptor(
            InterceptPoint.BEFORE_STEP, 500,
            context -> {
                context.context().putVariable("extPackApplied", true);
                return InterceptDecision.pass();
            }));
    }

    /** 桥接贡献的示例工具。 */
    public static final class SampleExtTool implements Tool {
        @Override
        public String id() {
            return "sample-ext-tool";
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("sample-ext-tool", "拓展包示例工具", Map.of());
        }

        @Override
        public ToolResult call(site.sorghum.cutin.core.tool.ToolCall call, site.sorghum.cutin.core.context.LoopContext context) {
            return ToolResult.success(call.id(), "ext");
        }
    }
}
