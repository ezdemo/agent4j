package site.sorghum.agent4j.web.model;

import java.util.List;

/**
 * 工具信息（安全序列化，剔除 lambda fn）。
 */
public record ToolInfoDTO(
        String name,
        String description,
        boolean readOnly,
        boolean stormExempt,
        boolean enabled,
        List<ToolParamInfoDTO> params
) {
}
