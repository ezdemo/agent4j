package site.sorghum.loopra.web.model;

import java.util.List;

/** 工具信息响应体，供管理界面展示。 */
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
