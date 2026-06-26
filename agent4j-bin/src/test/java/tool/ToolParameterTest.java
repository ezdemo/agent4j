package tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.sorghum.agent4j.tool.ToolParameter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolParameter} 单元测试。
 *
 * @author Sorghum
 */
@DisplayName("ToolParameter 工具参数定义测试")
class ToolParameterTest {

    @Test
    @DisplayName("构造必填参数")
    void shouldCreateRequiredParam() {
        ToolParameter p = new ToolParameter("path", "string", true, "文件路径");
        assertEquals("path", p.name());
        assertEquals("string", p.type());
        assertTrue(p.required());
        assertEquals("文件路径", p.description());
        assertNull(p.defaultValue());
        assertNull(p.properties());
        assertNull(p.items());
    }

    @Test
    @DisplayName("构造可选参数")
    void shouldCreateOptionalParam() {
        ToolParameter p = new ToolParameter("timeout", "int", false, "超时秒数");
        assertFalse(p.required());
    }

    @Test
    @DisplayName("构造带默认值的参数")
    void shouldCreateParamWithDefault() {
        ToolParameter p = new ToolParameter("count", "int", false, "数量", "10");
        assertEquals("10", p.defaultValue());
    }

    @Test
    @DisplayName("toString 应格式化")
    void toString_shouldFormat() {
        ToolParameter p = new ToolParameter("path", "string", true, "文件路径");
        String s = p.toString();
        assertTrue(s.contains("path"));
        assertTrue(s.contains("string"));
        assertTrue(s.contains("*"));
        assertTrue(s.contains("文件路径"));
    }

    @Test
    @DisplayName("全参构造器应正确设置所有字段")
    void fullConstructor_shouldSetAllFields() {
        ToolParameter p = new ToolParameter("name", "string", true, "名称", "defaultName");
        assertEquals("name", p.name());
        assertEquals("string", p.type());
        assertTrue(p.required());
        assertEquals("名称", p.description());
        assertEquals("defaultName", p.defaultValue());
        assertNull(p.properties());
        assertNull(p.items());
    }

    // ==================== 嵌套参数测试 ====================

    @Test
    @DisplayName("objectParam 工厂方法应创建嵌套对象")
    void objectParam_shouldCreateNestedObject() {
        ToolParameter sub1 = new ToolParameter("old_str", "string", true, "待替换的文本");
        ToolParameter sub2 = new ToolParameter("new_str", "string", true, "替换后的文本");

        ToolParameter p = ToolParameter.objectParam("edits", true, "编辑操作列表", List.of(sub1, sub2));

        assertEquals("edits", p.name());
        assertEquals("object", p.type());
        assertTrue(p.required());
        assertTrue(p.isObject());
        assertNotNull(p.properties());
        assertEquals(2, p.properties().size());
        assertEquals("old_str", p.properties().get(0).name());
        assertEquals("new_str", p.properties().get(1).name());
        assertNull(p.items());
    }

    @Test
    @DisplayName("arrayParam 工厂方法应创建数组类型")
    void arrayParam_shouldCreateArray() {
        ToolParameter item = ToolParameter.objectParam("", false, "数组元素",
                List.of(new ToolParameter("id", "string", true, "ID")));

        ToolParameter p = ToolParameter.arrayParam("items", true, "项目列表", item);

        assertEquals("items", p.name());
        assertEquals("array", p.type());
        assertTrue(p.required());
        assertTrue(p.isArray());
        assertNotNull(p.items());
        assertEquals("object", p.items().type());
        assertEquals(1, p.items().properties().size());
        assertEquals("id", p.items().properties().get(0).name());
        assertNull(p.properties());
    }

    @Test
    @DisplayName("isObject 和 isArray 对扁平参数返回 false")
    void isObject_isArray_shouldReturnFalseForFlatParams() {
        ToolParameter p = new ToolParameter("name", "string", true, "名称");

        assertFalse(p.isObject());
        assertFalse(p.isArray());
    }

    @Test
    @DisplayName("objectParam 空 properties 时 isObject 返回 false")
    void objectParam_emptyProperties_isObjectFalse() {
        ToolParameter p = ToolParameter.objectParam("obj", false, "空对象", List.of());
        assertFalse(p.isObject()); // 空 properties 不算嵌套
    }
}
