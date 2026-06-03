package site.sorghum.agent4j.web.model;

/**
 * OpenAPI 工具信息 DTO —— 描述一个从 OpenAPI 文档解析出的接口工具。
 *
 * @author Sorghum
 */
public class OpenApiToolDTO {

    /**
     * 接口名称（用作 call_api 的 api_name）
     */
    public String name;

    /**
     * 功能描述
     */
    public String description;

    /**
     * 所属业务分组
     */
    public String category;

    /**
     * HTTP 方法（GET/POST/PUT/DELETE 等）
     */
    public String method;

    /**
     * 接口路径
     */
    public String path;

    /**
     * 来源文档地址
     */
    public String docUrl;

    /**
     * 接口基地址
     */
    public String baseUrl;

    /**
     * 是否已弃用
     */
    public boolean deprecated;
}
