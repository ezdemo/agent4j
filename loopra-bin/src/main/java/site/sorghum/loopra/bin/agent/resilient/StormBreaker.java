package site.sorghum.loopra.bin.agent.resilient;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.config.LoopraConfig;

import java.util.LinkedList;

/**
 * Sliding-window circuit breaker for repeated tool calls.
 */
@Slf4j
public class StormBreaker {

    private static final int DEFAULT_WINDOW_SIZE = 6;
    private static final int DEFAULT_THRESHOLD = 3;

    private final int windowSize;
    private final int threshold;
    private final LinkedList<Entry> recent = new LinkedList<>();

    public StormBreaker() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_THRESHOLD);
    }

    public StormBreaker(int windowSize, int threshold) {
        this.windowSize = Math.max(1, windowSize);
        this.threshold = Math.max(1, threshold);
    }

    public static StormBreaker fromConfig(LoopraConfig config) {
        if (config == null) {
            return new StormBreaker();
        }
        return new StormBreaker(config.stormWindowSize(), config.stormThreshold());
    }

    public synchronized void reset() {
        recent.clear();
    }

    /**
     * Checks whether a tool call should be suppressed. Calls that cannot be
     * fingerprinted are allowed and do not pollute the window.
     */
    public synchronized SuppressResult inspect(String name, String argumentsJson, boolean readOnly) {
        String fingerprint = fingerprint(name, argumentsJson);
        if (fingerprint == null) {
            return SuppressResult.ALLOWED;
        }

        int count = 0;
        for (Entry entry : recent) {
            if (entry.fingerprint.equals(fingerprint)) {
                count++;
            }
        }

        if (count >= threshold - 1) {
            return new SuppressResult(true,
                    "风暴断路器: " + name + " 被相同参数调用了 " + (count + 1)
                            + " 次——已抑制。请勿使用相同参数重试。"
                            + "如果意图仍然有效，请换用不同参数或改用其他工具。");
        }

        // A mutating operation changes workspace state, so older reads no
        // longer describe the same state and should not count as a storm.
        if (!readOnly) {
            recent.removeIf(Entry::readOnly);
        }

        recent.addLast(new Entry(fingerprint, readOnly));
        while (recent.size() > windowSize) {
            recent.removeFirst();
        }
        return SuppressResult.ALLOWED;
    }

    private static String fingerprint(String name, String argumentsJson) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String args = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        try {
            return name + "|" + ONode.ofJson(args).toJson();
        } catch (Exception firstFailure) {
            String repaired = repairJson(args);
            if (repaired == null) {
                return null;
            }
            try {
                return name + "|" + ONode.ofJson(repaired).toJson();
            } catch (Exception secondFailure) {
                log.debug("JSON repair could not produce a tool fingerprint: {}", secondFailure.getMessage());
                return null;
            }
        }
    }

    private static String repairJson(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        StringBuilder repaired = new StringBuilder(input);
        int quotes = 0;
        int braces = 0;
        int brackets = 0;
        boolean escaped = false;
        for (int i = 0; i < repaired.length(); i++) {
            char c = repaired.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quotes++;
            } else if (c == '{') {
                braces++;
            } else if (c == '}') {
                braces--;
            } else if (c == '[') {
                brackets++;
            } else if (c == ']') {
                brackets--;
            }
        }
        if ((quotes & 1) == 1 && !escaped) {
            repaired.append('"');
        }
        while (braces-- > 0) {
            repaired.append('}');
        }
        while (brackets-- > 0) {
            repaired.append(']');
        }
        return repaired.toString();
    }

    private record Entry(String fingerprint, boolean readOnly) {
    }

    public record SuppressResult(boolean suppressed, String reason) {
        private static final SuppressResult ALLOWED = new SuppressResult(false, null);
    }
}
