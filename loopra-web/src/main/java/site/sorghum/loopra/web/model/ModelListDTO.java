package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * 可用模型列表。
 */
public record ModelListDTO(
        String current,
        String currentChannelId,
        List<ModelInfoDTO> models
) {
}
