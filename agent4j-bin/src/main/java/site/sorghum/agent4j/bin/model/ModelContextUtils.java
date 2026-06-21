package site.sorghum.agent4j.bin.model;

/**
 * 模型上下文大小相关的工具方法。
 * <p>
 * 提供模型名称中上下文大小后缀的解析和剥离功能。
 * 后缀格式：[数字k] 或 [数字m] 或 [数字g]（不区分大小写）
 * </p>
 *
 * @author Sorghum
 */
public final class ModelContextUtils {

    private ModelContextUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 解析结果：上下文大小后缀的解析结果。
     */
    static class ParsedContextSuffix {
        final int start;
        final int end;
        final long number;
        final String unit;

        ParsedContextSuffix(int start, int end, long number, String unit) {
            this.start = start;
            this.end = end;
            this.number = number;
            this.unit = unit;
        }

        /**
         * 转换为 token 数。
         */
        int toTokens() {
            switch (unit) {
                case "m": return (int) (number * 1_000_000);
                case "g": return (int) (number * 1_000_000_000);
                default: return (int) (number * 1_000); // "k" 或空
            }
        }
    }

    /**
     * 解析模型名称中的上下文大小后缀。
     * 格式：[数字k] 或 [数字m] 或 [数字g]（不区分大小写）
     *
     * @param modelName 模型名称，例如 "mimo-v2.5[512k]"
     * @return 解析出的 token 数，如果解析失败返回 -1
     */
    static int parseContextSizeSuffix(String modelName) {
        ParsedContextSuffix parsed = parseContextSuffix(modelName);
        return parsed != null ? parsed.toTokens() : -1;
    }

    /**
     * 剥离模型名称中的上下文大小后缀。
     * 例如："mimo-v2.5[512k]" → "mimo-v2.5"
     *
     * @param modelName 模型名称
     * @return 剥离后缀后的模型名称，如果没有后缀则返回原名称
     */
    public static String stripContextSizeSuffix(String modelName) {
        ParsedContextSuffix parsed = parseContextSuffix(modelName);
        return parsed != null ? modelName.substring(0, parsed.start).trim() : modelName;
    }

    /**
     * 解析模型名称中的上下文大小后缀（公共逻辑）。
     *
     * @param modelName 模型名称
     * @return 解析结果，如果解析失败返回 null
     */
    static ParsedContextSuffix parseContextSuffix(String modelName) {
        if (modelName == null || !modelName.contains("[") || !modelName.contains("]")) {
            return null;
        }

        int start = modelName.lastIndexOf('[');
        int end = modelName.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }

        String suffix = modelName.substring(start + 1, end).trim().toLowerCase();
        if (suffix.isEmpty()) {
            return null;
        }

        // 解析数字部分
        int numberEnd = suffix.length();
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c < '0' || c > '9') {
                numberEnd = i;
                break;
            }
        }

        if (numberEnd == 0) {
            return null; // 没有数字部分
        }

        try {
            long number = Long.parseLong(suffix.substring(0, numberEnd));
            String unit = suffix.substring(numberEnd).trim();

            // 验证单位
            if (!unit.isEmpty() && !unit.equals("k") && !unit.equals("m") && !unit.equals("g")) {
                return null; // 未知单位
            }

            return new ParsedContextSuffix(start, end, number, unit);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
