package site.sorghum.loopra.web.model;

import java.util.List;

/** 子代理信息响应体，供管理界面展示。 */
public record SubAgentInfoDTO(
        String id,
        String name,
        String description,
        boolean readOnly,
        String systemPrompt,
        List<String> tools,
        List<String> allowedTools,
        Boolean enable,
        boolean builtin,
        String modelChannel,
        String model
) {
}
