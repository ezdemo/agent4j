package tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.sorghum.loopra.tool.ToolResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolResult} 单元测试。
 *
 * @author Sorghum
 */
@DisplayName("ToolResult 工具结果封装测试")
class ToolResultTest {

    @Test
    @DisplayName("ok() 应创建纯文本成功结果")
    void ok_shouldCreateSuccessResult() {
        ToolResult r = ToolResult.ok("操作成功");
        assertTrue(r.success());
        assertEquals("操作成功", r.text());
        assertNull(r.data());
        assertNull(r.errorCode());
        assertNull(r.suggestion());
        assertFalse(r.shouldRetry());
    }

    @Test
    @DisplayName("ok(text, data) 应创建带数据的成功结果")
    void okWithData_shouldIncludeData() {
        HashMap<Object, Object> data = new HashMap<>();
        data.put("key", "value");
        ToolResult r = ToolResult.ok("带数据的结果", data);
        assertTrue(r.success());
        assertEquals("带数据的结果", r.text());
        assertEquals(data, r.data());
    }

    @Test
    @DisplayName("fail() 应创建失败结果")
    void fail_shouldCreateFailureResult() {
        ToolResult r = ToolResult.fail("FILE_NOT_FOUND", "文件未找到");
        assertFalse(r.success());
        assertEquals("FILE_NOT_FOUND", r.errorCode());
        assertEquals("文件未找到", r.text());
        assertNull(r.suggestion());
        assertFalse(r.shouldRetry());
    }

    @Test
    @DisplayName("retry() 应创建可重试的失败结果")
    void retry_shouldCreateRetryableResult() {
        ToolResult r = ToolResult.retry("LOCKED", "文件被锁定", "请稍后重试", "文件最新内容");
        assertFalse(r.success());
        assertEquals("LOCKED", r.errorCode());
        assertTrue(r.shouldRetry());
        assertEquals("请稍后重试", r.suggestion());
        assertEquals("文件最新内容", r.retryView());
    }

    @Test
    @DisplayName("toMap() 应正确序列化成功结果")
    void toMap_shouldSerializeSuccess() {
        ToolResult r = ToolResult.ok("hello");
        Map<String, Object> map = r.toMap();
        assertEquals(true, map.get("success"));
        assertEquals("hello", map.get("text"));
        assertNull(map.get("errorCode"));
    }

    @Test
    @DisplayName("toMap() 应正确序列化失败结果")
    void toMap_shouldSerializeFailure() {
        ToolResult r = ToolResult.fail("ERR", "出错啦");
        Map<String, Object> map = r.toMap();
        assertEquals(false, map.get("success"));
        assertEquals("ERR", map.get("errorCode"));
        assertEquals("出错啦", map.get("text"));
    }

    @Test
    @DisplayName("toMap() 应包含 retry 字段")
    void toMap_shouldIncludeRetryFields() {
        ToolResult r = ToolResult.retry("ERR", "msg", "建议", "最新视图");
        Map<String, Object> map = r.toMap();
        assertEquals(true, map.get("shouldRetry"));
        assertEquals("最新视图", map.get("retryView"));
        assertEquals("建议", map.get("suggestion"));
    }

    @Test
    @DisplayName("toString() 应格式化成功结果")
    void toString_shouldFormatSuccess() {
        ToolResult r = ToolResult.ok("完成");
        assertEquals("[OK] 完成", r.toString());
    }

    @Test
    @DisplayName("toString() 应格式化失败结果")
    void toString_shouldFormatFailure() {
        ToolResult r = ToolResult.fail("ERR", "失败原因");
        assertEquals("[FAIL:ERR] 失败原因", r.toString());
    }

    @Test
    @DisplayName("toString() 应包含建议信息")
    void toString_shouldIncludeSuggestion() {
        ToolResult r = ToolResult.retry("ERR", "锁住", "解锁后重试", "");
        assertTrue(r.toString().contains("解锁后重试"));
    }

    @Test
    @DisplayName("ok(null) 应将 null text 转为空字符串")
    void ok_shouldHandleNullText() {
        ToolResult r = ToolResult.ok(null);
        assertTrue(r.success());
        assertEquals("", r.text());
    }

    @Test
    @DisplayName("fail(null errorCode) 应使用默认 ERROR 码")
    void fail_shouldDefaultNullErrorCode() {
        ToolResult r = ToolResult.fail(null, "something wrong");
        assertEquals("ERROR", r.errorCode());
    }

    @Test
    @DisplayName("fail(null text) 应将 null text 转为空字符串")
    void fail_shouldHandleNullText() {
        ToolResult r = ToolResult.fail("E", null);
        assertEquals("", r.text());
    }

    @Test
    @DisplayName("toMap() 应返回不可修改的 Map")
    void toMap_shouldReturnUnmodifiableMap() {
        ToolResult r = ToolResult.ok("test");
        Map<String, Object> map = r.toMap();
        assertThrows(UnsupportedOperationException.class, () -> map.put("newKey", "value"));
    }
}
