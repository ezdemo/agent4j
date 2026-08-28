package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 更新会话模型设置的请求。未提供的字段保持当前会话值不变。
 */
@Data
public class SessionSettingsRequest {

    private String model;

    private String modelChannelId;

    private String reasoningEffort;
}
