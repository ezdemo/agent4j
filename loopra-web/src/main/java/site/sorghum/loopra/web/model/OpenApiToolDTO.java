package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * OpenAPI 工具信息 DTO —— 描述一个从 OpenAPI 文档解析出的接口工具。
 *
 * @author Sorghum
 */
@Data
public class OpenApiToolDTO {

    /**
     * 接口名称（用作 call_api 的 api_name）
     */
    private String name;

    /**
     * 功能描述
     */
    private String description;

    /**
     * 所属业务分组
     */
    private String category;

    /**
     * HTTP 方法（GET/POST/PUT/DELETE 等）
     */
    private String method;

    /**
     * 接口路径
     */
    private String path;

    /**
     * 来源文档地址
     */
    private String docUrl;

    /**
     * 接口基地址
     */
    private String baseUrl;

    /**
     * 是否已弃用
     */
    private boolean deprecated;
}
