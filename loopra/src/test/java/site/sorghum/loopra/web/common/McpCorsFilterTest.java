package site.sorghum.loopra.web.common;

import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextEmpty;
import org.noear.solon.web.cors.CrossFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpCorsFilterTest {

    @Test
    void exposesMcpSessionIdToBrowserClients() throws Throwable {
        ContextEmpty context = new ContextEmpty();
        context.headerMap().put("Origin", "http://127.0.0.1:6274");

        new CrossFilter()
                .allowedOrigins("*")
                .allowCredentials(false)
                .exposedHeaders("Mcp-Session-Id")
                .doFilter(context, ignored -> {
        });

        assertEquals("http://127.0.0.1:6274",
                context.headerOfResponse("Access-Control-Allow-Origin"));
        assertEquals("Mcp-Session-Id",
                context.headerOfResponse("Access-Control-Expose-Headers"));
        assertNull(context.headerOfResponse("Access-Control-Allow-Credentials"));
    }

    @Test
    void globalExceptionFilterKeepsMcpCorsHeadersOnErrors() throws Throwable {
        ContextEmpty context = new ContextEmpty();
        context.headerMap().put("Origin", "https://chatgpt.com");

        new GlobalExceptionFilter().doFilter(context, ignored -> {
        });

        assertEquals("https://chatgpt.com",
                context.headerOfResponse("Access-Control-Allow-Origin"));
        assertEquals("Mcp-Session-Id",
                context.headerOfResponse("Access-Control-Expose-Headers"));
        assertNull(context.headerOfResponse("Access-Control-Allow-Credentials"));
    }
}
