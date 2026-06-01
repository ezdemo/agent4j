package site.sorghum.agent4j.web.model;

import java.util.List;

/**
 * 可用模型列表。
 */
public record ModelListDTO(
        String current,
        List<ModelInfoDTO> models
) {
}
