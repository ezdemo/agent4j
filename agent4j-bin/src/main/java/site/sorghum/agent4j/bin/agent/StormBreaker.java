package site.sorghum.agent4j.bin.agent;

import java.util.LinkedList;

/**
 * 风暴断路器 —— 滑动窗口检测重复工具调用。
 * <p>
 * 当模型在循环中反复调用同一工具且参数完全相同时，
 * 抑制后续调用并返回提示，避免死循环耗尽 token 预算。
 * </p>
 *
 * @author Sorghum
 */
public class StormBreaker {

    private static final int WINDOW_SIZE = 6;
    private static final int THRESHOLD = 3;
    private final LinkedList<Entry> recent = new LinkedList<>();

    /**
     * 计算工具调用的指纹，用于检测重复调用。
     * 使用 ONode 解析参数 JSON 后重新序列化，消除键顺序和空格差异。
     * 解析失败时尝试补全 JSON；仍失败则返回 null（表示"无法判断"）。
     */
    private static String fingerprint(String name, String args) {
        try {
            org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(args);
            return name + "|" + node.toJson();
        } catch (Exception e) {
            // JSON 损坏 → 尝试补全
            String repaired = tryRepairJson(args);
            if (repaired != null) {
                try {
                    org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(repaired);
                    return name + "|" + node.toJson();
                } catch (Exception ignored) {
                }
            }
            // 修不了 → 返回 null，inspect() 会放行
            return null;
        }
    }

    /**
     * 尝试补全被截断的 JSON：补上缺失的 }、]、" 等
     */
    private static String tryRepairJson(String s) {
        if (s == null || s.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(s);
        // 补未闭合的字符串（奇数个引号 → 补一个）
        int quoteCount = 0;
        boolean escaped = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') quoteCount++;
        }
        if (quoteCount % 2 != 0 && !escaped) sb.append('"');
        // 补缺失的 }
        int braceDepth = 0;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
        }
        while (braceDepth > 0) {
            sb.append('}');
            braceDepth--;
        }
        // 补缺失的 ]
        int bracketDepth = 0;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
        }
        while (bracketDepth > 0) {
            sb.append(']');
            bracketDepth--;
        }
        return sb.toString();
    }

    /**
     * 重置滑动窗口，每回合开始时调用。
     * 清除历史记录，使 Storm Breaker 在新回合从头计数。
     * 使用 synchronized 保证线程安全。
     */
    public synchronized void reset() {
        recent.clear();
    }

    /**
     * 检查是否应抑制此调用。
     *
     * @param name          工具名
     * @param argumentsJson 参数 JSON 字符串
     * @param readOnly      工具是否为只读
     * @return 抑制信息
     */
    public synchronized SuppressResult inspect(String name, String argumentsJson, boolean readOnly) {
        int rawLen = argumentsJson != null ? argumentsJson.length() : 0;
        String fp = fingerprint(name, argumentsJson);

        // 无法计算指纹（JSON 损坏）→ 不抑制，不污染历史
        if (fp == null) {
            return new SuppressResult(false, null);
        }

        int count = 0;
        for (Entry e : recent) {
            if (e.name.equals(name) && e.argsFingerprint.equals(fp)) count++;
        }

        // 额外防线：指纹相同但 raw 长度差异 > 10% → 参数大概率不同，不抑制
        if (count >= THRESHOLD - 1) {
            int prevLen = 0;
            for (Entry e : recent) {
                if (e.name.equals(name) && e.argsFingerprint.equals(fp) && e.rawLength > prevLen) {
                    prevLen = e.rawLength;
                }
            }
            int diff = Math.abs(rawLen - prevLen);
            if (prevLen > 0 && diff > prevLen / 10) {
                // 长度差异过大，可能是截断碰撞，放行
                recent.addLast(new Entry(name, fp, readOnly, rawLen));
                while (recent.size() > WINDOW_SIZE) recent.removeFirst();
                return new SuppressResult(false, null);
            }

            return new SuppressResult(true,
                    "风暴断路器: " + name + " 被相同参数调用了 " + (count + 1)
                            + " 次——已抑制。请勿使用相同参数重试。"
                            + "如果意图仍然有效，请换用不同参数或改用其他工具。");
        }

        if (!readOnly) {
            recent.removeIf(e -> e.readOnly);
        }

        recent.addLast(new Entry(name, fp, readOnly, rawLen));
        while (recent.size() > WINDOW_SIZE) {
            recent.removeFirst();
        }
        return new SuppressResult(false, null);
    }

    record Entry(String name, String argsFingerprint, boolean readOnly, int rawLength) {
    }

    public static class SuppressResult {
        public final boolean suppressed;
        public final String reason;

        SuppressResult(boolean suppressed, String reason) {
            this.suppressed = suppressed;
            this.reason = reason;
        }
    }
}
