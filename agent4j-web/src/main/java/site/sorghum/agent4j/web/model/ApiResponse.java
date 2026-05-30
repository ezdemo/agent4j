package site.sorghum.agent4j.web.model;

import org.noear.snack4.ONode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 API 响应封装。
 *
 * @author Sorghum
 */
public class ApiResponse<T> {

    public boolean success;
    public String error;
    public T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(String error) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error = error;
        return r;
    }

    /** 转为 Map（方便 Solon JSON 序列化） */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", success);
        if (error != null) m.put("error", error);
        if (data != null) m.put("data", data);
        return m;
    }

    @Override
    public String toString() {
        return ONode.ofBean(this).toString();
    }
}
