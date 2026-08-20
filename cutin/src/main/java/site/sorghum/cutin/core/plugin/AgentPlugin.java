package site.sorghum.cutin.core.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 插件注解：用于声明插件 id 与启动顺序。
 *
 * <p>注解只承担发现与排序；实际执行仍走显式拦截链。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AgentPlugin {

    /** 插件 id，优先于 {@link LoopPlugin#id()}。 */
    String id();

    /** 启动顺序，数值越小越先启动。 */
    int order() default 0;

    /** 面向使用者的插件说明；留空时由宿主提供降级描述。 */
    String remark() default "";
}
