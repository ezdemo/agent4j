package site.sorghum.agent4j.web.common;

import org.junit.jupiter.api.Test;
import site.sorghum.agent4j.web.model.ApiResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 单元测试。
 */
class ApiResponseTest {

    @Test
    void okReturnsSuccessResponse() {
        ApiResponse<String> resp = ApiResponse.ok("data");
        assertTrue(resp.isSuccess());
        assertEquals("data", resp.getData());
        assertNull(resp.getError());
    }

    @Test
    void okWithNullData() {
        ApiResponse<Object> resp = ApiResponse.ok(null);
        assertTrue(resp.isSuccess());
        assertNull(resp.getData());
    }

    @Test
    void failReturnsErrorResponse() {
        ApiResponse<Void> resp = ApiResponse.fail("something went wrong");
        assertFalse(resp.isSuccess());
        assertEquals("something went wrong", resp.getError());
        assertNull(resp.getData());
    }

    @Test
    void toStringContainsSuccessAndError() {
        String okStr = ApiResponse.ok("hello").toString();
        assertTrue(okStr.contains("true"));

        String failStr = ApiResponse.fail("oops").toString();
        assertTrue(failStr.contains("false"));
        assertTrue(failStr.contains("oops"));
    }
}
