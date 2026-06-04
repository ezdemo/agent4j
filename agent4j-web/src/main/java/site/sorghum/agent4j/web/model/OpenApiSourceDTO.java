package site.sorghum.agent4j.web.model;

import lombok.Data;

import java.util.Map;

/**
 * OpenAPI 源信息 DTO —— 描述一个已注册的 OpenAPI 定义源。
 *
 * @author Sorghum
 */
@Data
public class OpenApiSourceDTO {

    /**
     * OpenAPI 定义文档地址（http://... 或 classpath:...）
     */
    public String docUrl;

    /**
     * 请求头
     */
    public Map<String, String> headers;

    /**
     * 认证方式：none / bearer / apikey / basic
     */
    public String authType;

    /**
     * 认证配置（不同 authType 含义不同）
     */
    public Map<String, String> authConfig;

    /**
     * 是否启用
     */
    public boolean enabled = true;

    /**
     * 状态：loaded / error / disabled
     */
    public String status = "loaded";

    /**
     * 加载失败时的错误信息
     */
    public String errorMessage;

    public static OpenApiSourceDTO ok(String docUrl,
                                      Map<String, String> headers,
                                      String authType, Map<String, String> authConfig) {
        OpenApiSourceDTO dto = new OpenApiSourceDTO();
        dto.docUrl = docUrl;
        dto.headers = headers;
        dto.authType = authType;
        dto.authConfig = authConfig;
        dto.enabled = true;
        dto.status = "loaded";
        return dto;
    }

    public static OpenApiSourceDTO error(String docUrl, String errorMessage) {
        OpenApiSourceDTO dto = new OpenApiSourceDTO();
        dto.docUrl = docUrl;
        dto.authType = "none";
        dto.enabled = true;
        dto.status = "error";
        dto.errorMessage = errorMessage;
        return dto;
    }
}
