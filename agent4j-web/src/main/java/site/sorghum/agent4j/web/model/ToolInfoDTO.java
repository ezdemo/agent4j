package site.sorghum.agent4j.web.model;

import java.util.List;

public record ToolInfoDTO(
        String name,
        String description,
        boolean readOnly,
        boolean stormExempt,
        boolean enabled,
        boolean autoApproved,
        List<ToolParamInfoDTO> params
) {
}