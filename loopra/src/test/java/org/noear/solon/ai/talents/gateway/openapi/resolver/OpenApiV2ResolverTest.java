package org.noear.solon.ai.talents.gateway.openapi.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.talents.gateway.openapi.ApiTool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OpenApiV2Resolver} 单元测试。
 *
 * @author Sorghum
 */
@DisplayName("OpenApiV2Resolver Swagger2 解析器测试")
class OpenApiV2ResolverTest {

    /**
     * 数组 items 循环引用：response schema 数组 items → $ref Node → children[] → $ref NodeList → node → $ref Node。
     * 循环链经过 RefProperty → Model 跳转，修复前会无限递归导致 StackOverflowError。
     */
    @Test
    @DisplayName("数组 items 循环引用应自动退出并标记，而非无限递归")
    void shouldTerminateOnCircularArrayItems() throws Exception {
        String swagger = """
                swagger: "2.0"
                info:
                  title: circular-ref
                  version: "1.0"
                basePath: /api
                paths:
                  /nodes:
                    get:
                      operationId: getNodes
                      responses:
                        "200":
                          description: ok
                          schema:
                            type: array
                            items:
                              $ref: "#/definitions/Node"
                definitions:
                  Node:
                    type: object
                    properties:
                      children:
                        type: array
                        items:
                          $ref: "#/definitions/NodeList"
                  NodeList:
                    type: object
                    properties:
                      node:
                        $ref: "#/definitions/Node"
                """;

        List<ApiTool> tools = new OpenApiV2Resolver().resolve(null, swagger);

        assertEquals(1, tools.size());
        ApiTool tool = tools.get(0);
        assertTrue(tool.getOutputSchema().contains("_Circular_Reference_"),
                "循环引用应被标记为 _Circular_Reference_");
    }

    /**
     * 对象属性循环引用：body schema → $ref A → b → $ref B → b → $ref B。
     * 修复前 RefProperty B 递归进入 resolveModel 时引用名丢失，无法检测循环。
     */
    @Test
    @DisplayName("对象属性循环引用（A→B→B）应自动退出并标记")
    void shouldTerminateOnCircularObjectRef() throws Exception {
        String swagger = """
                swagger: "2.0"
                info:
                  title: circular-ref
                  version: "1.0"
                basePath: /api
                paths:
                  /items:
                    post:
                      operationId: createItem
                      parameters:
                        - in: body
                          name: body
                          schema:
                            $ref: "#/definitions/A"
                definitions:
                  A:
                    type: object
                    properties:
                      b:
                        $ref: "#/definitions/B"
                  B:
                    type: object
                    properties:
                      b:
                        $ref: "#/definitions/B"
                """;

        List<ApiTool> tools = new OpenApiV2Resolver().resolve(null, swagger);

        assertEquals(1, tools.size());
        ApiTool tool = tools.get(0);
        assertTrue(tool.getBodySchema().contains("_Circular_Reference_"),
                "循环引用应被标记为 _Circular_Reference_");
    }
}
