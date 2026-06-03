package site.sorghum.agent4j.web.doc;

import com.github.xiaoymin.knife4j.solon.extension.OpenApiExtensionResolver;
import io.swagger.models.Scheme;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.noear.solon.docs.DocDocket;

@Configuration
public class DocConfig {

    // knife4j 的配置，由它承载
    @Inject
    OpenApiExtensionResolver openApiExtensionResolver;

    /**
     * 简单点的
     */
    @Bean("appApi")
    public DocDocket appApi() {
        return new DocDocket()
                .basicAuth(openApiExtensionResolver.getSetting().getBasic())
                .vendorExtensions(openApiExtensionResolver.buildExtensions())
                .groupName("Agent4J端")
                .schemes(Scheme.HTTP.toValue())
                .apis("site.sorghum.agent4j.web.controller");

    }

}