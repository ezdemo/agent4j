package site.sorghum.agent4j.tool.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SmartDecoder} 单元测试——智能输出解码器。
 *
 * @author Sorghum
 */
@DisplayName("SmartDecoder 输出解码测试")
class SmartDecoderTest {

    @Test
    @DisplayName("UTF-8 解码")
    void utf8Decode() {
        String result = SmartDecoder.decode("hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("中文 UTF-8 解码")
    void chineseUtf8() {
        String chinese = "你好世界";
        String result = SmartDecoder.decode(chinese.getBytes(StandardCharsets.UTF_8));
        assertEquals(chinese, result);
    }

    @Test
    @DisplayName("GBK 编码自动检测 (模拟)")
    void gbkDetection() {
        // GBK 编码的 "中国"
        byte[] gbkBytes = new byte[]{(byte) 0xD6, (byte) 0xD0, (byte) 0xB9, (byte) 0xFA};
        String result = SmartDecoder.decode(gbkBytes);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("空字节数组")
    void emptyBytes() {
        String result = SmartDecoder.decode(new byte[0]);
        assertEquals("", result);
    }

    @Test
    @DisplayName("特殊字符")
    void specialChars() {
        String special = "tab:\t newline:\n backslash:\\";
        String result = SmartDecoder.decode(special.getBytes(StandardCharsets.UTF_8));
        assertTrue(result.contains("tab:"));
        assertTrue(result.contains("newline:"));
        assertTrue(result.contains("backslash:"));
    }
}
