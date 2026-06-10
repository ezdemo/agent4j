package site.sorghum.agent4j.web.common;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.FilterChain;
import site.sorghum.agent4j.web.model.ApiResponse;

/**
 * 全局异常拦截 Filter —— 所有未捕获异常统一返回 ApiResponse JSON 格式。
 * <p>
 * {@code index = 0} 确保最先执行，兜住所有下游抛出的异常。
 * </p>
 *
 * <pre>{@code
 * // Controller 中不再需要 try-catch，直接：
 * if (xxx) throw new ServiceException("message 不能为空");
 * // 或让业务异常自然向上传播
 * }</pre>
 *
 * @author Sorghum
 */
@Component(index = 0)
@Slf4j
public class GlobalExceptionFilter implements Filter {

    @Override
    public void doFilter(Context ctx, FilterChain chain) {
        // CORS 头：所有响应都加上
        ctx.headerSet("Access-Control-Allow-Origin", "*");
        ctx.headerSet("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        ctx.headerSet("Access-Control-Allow-Headers", "*");
        ctx.headerSet("Access-Control-Max-Age", "3600");

        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equals(ctx.method())) {
            ctx.output("");
            return;
        }

        try {
            chain.doFilter(ctx);
        } catch (ServiceException e) {
            ctx.outputAsJson(ApiResponse.fail(e.getMessage()).toString());
        } catch (Throwable e) {
            // 未预期的系统异常 → 500（避免泄露内部细节）
            log.error("GlobalExceptionFilter error", e);
            ctx.outputAsJson(ApiResponse.fail(e.getMessage()).toString());
        }
    }
}
