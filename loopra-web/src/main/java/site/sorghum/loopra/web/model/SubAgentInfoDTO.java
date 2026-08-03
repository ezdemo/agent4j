package site.sorghum.loopra.web.model;

import java.util.List;

public record SubAgentInfoDTO(
        String id,
        String name,
        String description,
        boolean readOnly,
        String systemPrompt,
        List<String> tools,
        List<String> allowedTools,
        Boolean enable,
        boolean builtin
) {
}
