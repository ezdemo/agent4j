package site.sorghum.loopra.extra.demo;

import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.loopra.integration.extpack.LoopraExtPackBridge;

import java.util.List;

/**
 * 示例拓展包桥接：把 Agent 能力贡献给全部存活的 AgentLoop。
 *
 * <p>实现类须提供 public 无参构造（ServiceLoader 实例化），并在 jar 内以
 * {@code META-INF/services/site.sorghum.loopra.integration.extpack.LoopraExtPackBridge}
 * 声明（见 src/main/resources/META-INF/services/）。</p>
 *
 * <p>桥接实例在拓展包启动后由 ExtPackStore 发现并注册，停止时逆序注销；
 * 新挂接的 AgentLoop 也会自动补注册本桥接贡献的能力。</p>
 */
public class DemoExtPackBridge implements LoopraExtPackBridge {

    /** 无参构造，供 ServiceLoader 实例化。 */
    public DemoExtPackBridge() {
    }

    @Override
    public String id() {
        return "demo-bridge";
    }

    @Override
    public String displayName() {
        return "示例拓展包";
    }

    @Override
    public List<Tool> tools() {
        return List.of(new DemoServerTimeTool(), new DemoGreetingTool());
    }

    @Override
    public List<RegisteredInterceptor> interceptors() {
        // 每个 Agent 步骤开始前，在上下文中打一个标记（演示拦截器能力）
        return List.of(new RegisteredInterceptor(
            InterceptPoint.BEFORE_STEP, 500,
            context -> {
                context.context().putVariable("demoExtApplied", true);
                return InterceptDecision.pass();
            }));
    }
}
