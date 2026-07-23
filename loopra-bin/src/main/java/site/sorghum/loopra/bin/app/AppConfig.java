package site.sorghum.loopra.bin.app;

import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.HttpModelClient;
import site.sorghum.loopra.bin.model.ModelClient;

import java.io.IOException;

/**
 * Solon IoC 配置 —— 将核心组件注册为 Solon 管理的 Bean。
 * <p>
 * loopra-tool 模块的工具由 @Component 扫描自动发现，
 * loopra-bin 模块的 builtin 工具同样自动发现。
 * </p>
 *
 * @author Sorghum
 */
@Configuration
public class AppConfig {

    @Bean
    public ModelClient modelClient() throws IOException {
        LoopraConfig config = LoopraConfig.load();
        return new HttpModelClient(
                config.apiUrl(),
                config.apiKey(),
                config.model(),
                config.reasoningEffort(),
                config.modelChannelId(),
                config.apiProtocol()
        );
    }

    @Bean
    public LoopraConfig loopraConfig() throws IOException {
        return LoopraConfig.load();
    }
}
