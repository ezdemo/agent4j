package site.sorghum.loopra.web.doc;

import com.github.xiaoymin.knife4j.solon.extension.OpenApiExtensionResolver;
import io.swagger.models.Scheme;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.noear.solon.docs.DocDocket;

/** 文档（knife4j/OpenAPI）配置。 */
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
                .groupName("Loopra端")
                .schemes(Scheme.HTTP.toValue())
                .apis("site.sorghum.loopra.web.controller");

    }

}
