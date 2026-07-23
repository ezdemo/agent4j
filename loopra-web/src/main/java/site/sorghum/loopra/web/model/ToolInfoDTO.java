package site.sorghum.loopra.web.model;

import java.util.List;

public record ToolInfoDTO(
        String name,
        String description,
        boolean readOnly,
        Boolean readOnlyOverride,
        boolean stormExempt,
        boolean enabled,
        boolean autoApproved,
        List<ToolParamInfoDTO> params
) {
}