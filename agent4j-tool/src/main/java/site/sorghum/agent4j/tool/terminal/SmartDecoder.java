package site.sorghum.agent4j.tool.terminal;

import java.nio.ByteBuffer;
import java.nio.charset.*;

/**
 * 智能输出解码 —— UTF-8 优先，失败后按平台回退。
 * <p>
 * 参考 Reasonix TS smartDecodeOutput：
 * UTF-8 → Windows GBK/GB18030 fallback → lossy UTF-8。
 * </p>
 */
public class SmartDecoder {

    /**
     * 将原始字节 buffer 解码为字符串。
     * 1. UTF-8 严格模式
     * 2. Windows 平台尝试 GB18030
     * 3. 最后手段：lossy UTF-8
     */
    public static String decode(byte[] bytes) {
        if (bytes.length == 0) return "";

        // 1. UTF-8 strict
        try {
            CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder();
            utf8.onMalformedInput(CodingErrorAction.REPORT);
            utf8.onUnmappableCharacter(CodingErrorAction.REPORT);
            return utf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ignored) {
            // UTF-8 解码失败，尝试其他编码
        }

        // 2. Windows → GB18030 (GBK 的超集)
        if (isWindows()) {
            try {
                return new String(bytes, Charset.forName("GB18030"));
            } catch (Exception ignored) {
                // GB18030 解码失败，回退到 lossy UTF-8
            }
        }

        // 3. Lossy UTF-8
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
