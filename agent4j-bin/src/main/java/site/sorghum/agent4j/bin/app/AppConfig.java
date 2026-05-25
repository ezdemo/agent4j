package site.sorghum.agent4j.bin.app;

import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.config.Agent4jConfig;

import java.io.IOException;

/**
 * Solon IoC 配置 —— 将核心组件注册为 Solon 管理的 Bean。
 * <p>
 * agent4j-tool 模块的工具由 @Component 扫描自动发现，
 * agent4j-bin 模块的 builtin 工具同样自动发现。
 * </p>
 *
 * @author Sorghum
 */
@Configuration
public class AppConfig {

    @Bean
    public ModelClient modelClient() throws IOException {
        Agent4jConfig config = Agent4jConfig.load();
        return new HttpModelClient(
                config.chatApiUrl(),
                config.apiKey(),
                config.model(),
                config.reasoningEffort()
        );
    }

    @Bean
    public Agent4jConfig agent4jConfig() throws IOException {
        return Agent4jConfig.load();
    }
}
