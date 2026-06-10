package site.sorghum.agent4j.web.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceException 单元测试。
 */
class ServiceExceptionTest {

    @Test
    void defaultCodeIs400() {
        ServiceException e = new ServiceException("bad request");
        assertEquals(400, e.getCode());
        assertEquals("bad request", e.getMessage());
    }

    @Test
    void customCode() {
        ServiceException e = new ServiceException(500, "server error");
        assertEquals(500, e.getCode());
        assertEquals("server error", e.getMessage());
    }

    @Test
    void isRuntimeException() {
        ServiceException e = new ServiceException("test");
        assertInstanceOf(RuntimeException.class, e);
    }
}
