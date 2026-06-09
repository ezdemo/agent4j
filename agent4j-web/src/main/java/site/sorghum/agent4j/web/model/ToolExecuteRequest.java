package site.sorghum.agent4j.web.model;

import lombok.Data;

import java.util.Map;

/**
 * 工具直接执行请求体。
 *
 * @author Sorghum
 */
@Data
public class ToolExecuteRequest {

    /**
     * 工具参数（JSON Map）
     */
    private Map<String, Object> arguments;
}
