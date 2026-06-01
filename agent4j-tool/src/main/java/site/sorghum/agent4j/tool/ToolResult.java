package site.sorghum.agent4j.tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行结果——统一的返回封装。
 * <p>
 * 包含成功/失败状态、文本输出、结构化数据，以及可选的
 * 后续操作建议（如"请重新读取后重试"）。
 * </p>
 *
 * @param success     执行是否成功
 * @param text        人类可读的结果文本
 * @param data        结构化数据（可选）
 * @param errorCode   错误码（失败时非空）
 * @param suggestion  给模型的后续操作建议
 * @param shouldRetry 是否需要模型重新读取文件后重试
 * @param retryView   重试时需要的新鲜视图（如 hashline 重新读取结果）
 * @author Sorghum
 */
public record ToolResult(boolean success, String text, Object data,
                         String errorCode, String suggestion,
                         boolean shouldRetry, String retryView) {

    public ToolResult(boolean success, String text, Object data,
                      String errorCode, String suggestion,
                      boolean shouldRetry, String retryView) {
        this.success = success;
        this.text = text;
        this.data = data;
        this.errorCode = errorCode;
        this.suggestion = suggestion;
        this.shouldRetry = shouldRetry;
        this.retryView = retryView;
    }

    // ---- 工厂方法 ----

    /**
     * 纯文本成功结果。
     */
    public static ToolResult ok(String text) {
        return new ToolResult(true, text, null, null, null, false, null);
    }

    /**
     * 带结构化数据的成功结果。
     */
    public static ToolResult ok(String text, Object data) {
        return new ToolResult(true, text, data, null, null, false, null);
    }

    /**
     * 失败结果。
     */
    public static ToolResult fail(String errorCode, String text) {
        return new ToolResult(false, text, null, errorCode, null, false, null);
    }

    /**
     * 失败但可重试的结果。
     */
    public static ToolResult retry(String errorCode, String text,
                                   String suggestion, String retryView) {
        return new ToolResult(false, text, null, errorCode, suggestion, true, retryView);
    }

    /**
     * 转为 Map 便于 JSON 序列化。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("text", text);
        if (data != null) map.put("data", data);
        if (errorCode != null) map.put("errorCode", errorCode);
        if (suggestion != null) map.put("suggestion", suggestion);
        if (shouldRetry) {
            map.put("shouldRetry", true);
            if (retryView != null) map.put("retryView", retryView);
        }
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String toString() {
        if (success) {
            return "[OK] " + text;
        }
        return "[FAIL:" + errorCode + "] " + text
                + (suggestion != null ? "\n建议: " + suggestion : "");
    }
}
