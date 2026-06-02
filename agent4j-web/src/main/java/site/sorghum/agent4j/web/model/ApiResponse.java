package site.sorghum.agent4j.web.model;

import org.noear.snack4.ONode;

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

    public static <T> ApiResponse<T> fail(String error) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error = error;
        return r;
    }

    @Override
    public String toString() {
        return ONode.ofBean(this).toString();
    }
}
