package site.sorghum.agent4j.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertEquals("path", p.getName());
        assertEquals("string", p.getType());
        assertTrue(p.isRequired());
        assertEquals("文件路径", p.getDescription());
        assertNull(p.getDefaultValue());
    }

    @Test
    @DisplayName("构造可选参数")
    void shouldCreateOptionalParam() {
        ToolParameter p = new ToolParameter("timeout", "int", false, "超时秒数");
        assertFalse(p.isRequired());
    }

    @Test
    @DisplayName("构造带默认值的参数")
    void shouldCreateParamWithDefault() {
        ToolParameter p = new ToolParameter("count", "int", false, "数量", "10");
        assertEquals("10", p.getDefaultValue());
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
        assertEquals("name", p.getName());
        assertEquals("string", p.getType());
        assertTrue(p.isRequired());
        assertEquals("名称", p.getDescription());
        assertEquals("defaultName", p.getDefaultValue());
    }
}
