package site.sorghum.loopra.bin.agent.resilient;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.spi.AgentConfig;

import java.util.LinkedList;

/**
  * 针对连续重复工具调用的熔断器。
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

    public static StormBreaker fromConfig(AgentConfig config) {
        if (config == null) {
            return new StormBreaker();
        }
        return new StormBreaker(config.stormWindowSize(), config.stormThreshold());
    }

    public synchronized void reset() {
        recent.clear();
    }

    /**
      * 检查工具调用是否应被抑制。无法指纹识别的调用会被放行，
      * 且不会污染窗口。
     */
    public synchronized SuppressResult inspect(String name, String argumentsJson, boolean readOnly) {
        String fingerprint = fingerprint(name, argumentsJson);
        if (fingerprint == null) {
            return SuppressResult.ALLOWED;
        }

        int consecutiveCount = 0;
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (!recent.get(i).fingerprint.equals(fingerprint)) {
                break;
            }
            consecutiveCount++;
        }

        if (consecutiveCount >= threshold - 1) {
            return new SuppressResult(true,
                    "风暴断路器: " + name + " 连续以相同参数调用了 " + (consecutiveCount + 1)
                            + " 次——已抑制。请勿连续使用相同参数重试。");
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

    /** 风暴抑制结果：suppressed 表示本次调用是否被抑制，reason 为抑制原因。 */
    public record SuppressResult(boolean suppressed, String reason) {
        private static final SuppressResult ALLOWED = new SuppressResult(false, null);
    }
}
