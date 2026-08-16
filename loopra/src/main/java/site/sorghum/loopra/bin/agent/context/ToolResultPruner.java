package site.sorghum.loopra.bin.agent.context;

import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.spi.AgentConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 无模型的 tool result 裁剪器：在摘要前把超预算的工具结果改写为
 * head + marker + tail，保留完整字段，只替换 content。
 */
public final class ToolResultPruner {

    public static final String PRUNE_MARKER = "\n\n[... tool result middle pruned ...]\n\n";

    private ToolResultPruner() {
    }

    /** 裁剪参数：超过 thresholdChars 的 tool result 内容保留 head + marker + tail。 */
    public record Config(int thresholdChars, int headChars, int tailChars) {
        public static final int DEFAULT_THRESHOLD_CHARS = 8192;
        public static final int DEFAULT_HEAD_CHARS = 4096;
        public static final int DEFAULT_TAIL_CHARS = 1024;

        public Config {
            if (thresholdChars <= 0) {
                throw new IllegalArgumentException("thresholdChars must be positive");
            }
            if (headChars < 0 || tailChars < 0) {
                throw new IllegalArgumentException("headChars and tailChars must be non-negative");
            }
            if ((long) headChars + PRUNE_MARKER.length() + tailChars > thresholdChars) {
                throw new IllegalArgumentException(
                        "headChars + marker + tailChars must fit within thresholdChars");
            }
        }

        public static Config defaults() {
            return new Config(DEFAULT_THRESHOLD_CHARS, DEFAULT_HEAD_CHARS, DEFAULT_TAIL_CHARS);
        }

        public static Config from(AgentConfig config) {
            if (config == null) return defaults();
            return new Config(
                    config.toolResultPruneThresholdChars(),
                    config.toolResultPruneHeadChars(),
                    config.toolResultPruneTailChars()
            );
        }
    }

    /** 裁剪结果：包含裁剪后的消息列表、被裁剪消息数和移除的字符数。 */
    public record PruneResult(List<ChatMessage> messages, int prunedCount, long charsRemoved) {
        public boolean changed() {
            return prunedCount > 0;
        }
    }

    public static PruneResult prune(List<ChatMessage> messages, Config config) {
        if (messages == null || messages.isEmpty()) {
            return new PruneResult(new ArrayList<>(), 0, 0);
        }

        List<ChatMessage> result = new ArrayList<>(messages.size());
        int prunedCount = 0;
        long charsRemoved = 0;
        for (ChatMessage message : messages) {
            if (message == null || !message.isTool() || message.getContent() == null) {
                result.add(message);
                continue;
            }
            String pruned = pruneContent(message.getContent(), config);
            if (pruned == null) {
                result.add(message);
                continue;
            }
            ChatMessage copy = message.copy();
            copy.setContent(pruned);
            result.add(copy);
            prunedCount++;
            charsRemoved += codePointCount(message.getContent()) - codePointCount(pruned);
        }
        return new PruneResult(result, prunedCount, charsRemoved);
    }

    public static String pruneContent(String content, Config config) {
        if (content == null) return null;
        int points = codePointCount(content);
        if (points <= config.thresholdChars()) return null;

        int headPoints = Math.min(config.headChars(), points);
        int tailStartPoint = Math.max(headPoints, points - config.tailChars());
        String head = content.substring(0, content.offsetByCodePoints(0, headPoints));
        String tail = content.substring(content.offsetByCodePoints(0, tailStartPoint));
        return head + PRUNE_MARKER + tail;
    }

    private static int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }
}
