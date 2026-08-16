package site.sorghum.loopra.bin.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AiBrowserBridgeService} 的单元测试。
 */
class AiBrowserBridgeServiceTest {

    @Test
    void acceptsIpv6LoopbackAddress() {
        AiBrowserBridgeService service = new AiBrowserBridgeService();
        assertEquals("http://[::1]:8080", service.setAddress("http://[::1]:8080"));
    }

    @Test
    void rejectsNonLoopbackIpv6Address() {
        AiBrowserBridgeService service = new AiBrowserBridgeService();
        assertThrows(IllegalArgumentException.class,
                () -> service.setAddress("http://[2001:db8::1]:8080"));
    }
}
