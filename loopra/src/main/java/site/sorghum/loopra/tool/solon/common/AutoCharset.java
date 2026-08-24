package site.sorghum.loopra.tool.solon.common;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 自适应输出字符集：同一字节流先按 UTF-8 严格解码，失败（含非法序列）时回退到指定代码页。
 *
 * <p>Windows 下命令输出的编码并不统一：git/node/python 等现代工具向管道输出 UTF-8 字节
 * （文件内容原样透传），而 cmd 内置命令（echo/type 等）与旧工具按活动 OEM 代码页输出 GBK 字节。
 * 固定用任一编码解码都会导致另一半场景乱码，故采用“UTF-8 优先、代码页兜底”的检测式解码：
 * GBK 双字节恰好构成合法 UTF-8 序列的概率约万分之一，误判率可忽略；纯 ASCII 两侧一致。
 *
 * <p>仅适用于一次性整块解码（如 {@code new String(bytes, charset)}）；流式分片场景下
 * 跨字符边界切分会走兜底编码，属极端情况。
 */
public final class AutoCharset extends Charset {
    private final Charset primary;
    private final Charset fallback;

    /**
     * @param primary  首选编码（一般为 UTF-8）
     * @param fallback UTF-8 解码失败时回退的编码（一般为系统活动代码页）
     */
    public AutoCharset(Charset primary, Charset fallback) {
        super("x-auto-" + primary.name() + "-" + fallback.name(), new String[0]);
        this.primary = primary;
        this.fallback = fallback;
    }

    /**
     * @param fallback UTF-8 解码失败时回退的编码（一般为系统活动代码页）
     */
    public AutoCharset(Charset fallback) {
        this(StandardCharsets.UTF_8, fallback);
    }

    @Override
    public boolean contains(Charset cs) {
        return false;
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new CharsetDecoder(this, 1.0f, 2.0f) {
            @Override
            protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
                if (!in.hasRemaining()) return CoderResult.UNDERFLOW;
                // 先尝试主编码（UTF-8）严格解码整段
                CharsetDecoder strict = primary.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                try {
                    CharBuffer decoded = strict.decode(in.duplicate());
                    return putAll(decoded, in, out);
                } catch (CharacterCodingException e) {
                    // 回退编码兜底（REPLACE 模式，理论上不抛异常）
                    CharsetDecoder fb = fallback.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPLACE)
                            .onUnmappableCharacter(CodingErrorAction.REPLACE);
                    CharBuffer decoded;
                    try {
                        decoded = fb.decode(in.duplicate());
                    } catch (CharacterCodingException e2) {
                        // fallback 也不可解码的极端情况：逐字节直通，保证不抛异常
                        decoded = StandardCharsets.ISO_8859_1.decode(in.duplicate());
                    }
                    return putAll(decoded, in, out);
                }
            }
        };
    }

    private static CoderResult putAll(CharBuffer decoded, ByteBuffer in, CharBuffer out) {
        int n = decoded.remaining();
        if (out.remaining() < n) return CoderResult.OVERFLOW;
        out.put(decoded);
        in.position(in.limit());
        return CoderResult.UNDERFLOW;
    }

    @Override
    public CharsetEncoder newEncoder() {
        return primary.newEncoder();
    }
}