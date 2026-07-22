package site.sorghum.loopra.web.model;

import java.util.List;

public record SubAgentInfoDTO(
        String id,
        boolean readOnly,
        String systemPrompt,
        List<String> tools
) {
}
