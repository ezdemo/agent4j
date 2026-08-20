package site.sorghum.cutin.core.json;

import org.noear.snack4.Feature;
import org.noear.snack4.ONode;
import org.noear.snack4.Options;
import org.noear.snack4.codec.TypeRef;

import java.util.List;
import java.util.Map;

/**
 * Snack4 的轻量封装，收敛树访问与序列化的公共写法。
 */
public final class JsonSupport {

    private JsonSupport() {
    }

    /** 创建一个 JSON 对象节点。 */
    public static ONode object() {
        return new ONode().asObject();
    }

    /** 创建一个 JSON 数组节点。 */
    public static ONode array() {
        return new ONode().asArray();
    }

    /** 解析 JSON 字符串。 */
    public static ONode read(String json) {
        return ONode.ofJson(json);
    }

    /** 序列化普通对象或集合。 */
    public static String write(Object value) {
        return ONode.serialize(value);
    }

    /** 序列化为美化后的 JSON。 */
    public static String writePretty(Object value) {
        return ONode.serialize(value, Options.of(Feature.Write_PrettyFormat));
    }

    /** 把普通对象转换为 Snack4 节点。 */
    public static ONode bean(Object value) {
        return ONode.ofBean(value);
    }

    /** 把 JSON 字符串解析为 Map。 */
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return ONode.ofJson(json).toBean(new TypeRef<Map<String, Object>>() {
        });
    }

    /** 把节点转换为 Map。 */
    public static Map<String, Object> toMap(ONode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return node.toBean(new TypeRef<Map<String, Object>>() {
        });
    }

    /** 按 key/index 逐级访问子节点，缺失时返回 null。 */
    public static ONode child(ONode node, Object... path) {
        ONode current = node;
        for (Object part : path) {
            if (current == null) {
                return null;
            }
            if (part instanceof Integer index) {
                current = indexChild(current, index);
            } else {
                current = keyChild(current, String.valueOf(part));
            }
        }
        return current;
    }

    /** 读取字符串字段，缺失或 null 时返回默认值。 */
    public static String text(ONode node, String fallback, Object... path) {
        ONode child = child(node, path);
        return child == null || child.isNull() ? fallback : child.getString();
    }

    /** 读取 long 字段，缺失或 null 时返回默认值。 */
    public static long longValue(ONode node, long fallback, Object... path) {
        ONode child = child(node, path);
        return child == null ? fallback : child.getLong(fallback);
    }

    /** 读取 int 字段，缺失或 null 时返回默认值。 */
    public static int intValue(ONode node, int fallback, Object... path) {
        ONode child = child(node, path);
        return child == null ? fallback : child.getInt(fallback);
    }

    /** 读取 boolean 字段，缺失或 null 时返回默认值。 */
    public static boolean boolValue(ONode node, boolean fallback, Object... path) {
        ONode child = child(node, path);
        return child == null ? fallback : child.getBoolean(fallback);
    }

    private static ONode keyChild(ONode node, String key) {
        if (!node.isObject()) {
            return null;
        }
        return node.getOrNull(key);
    }

    private static ONode indexChild(ONode node, int index) {
        if (!node.isArray()) {
            return null;
        }
        List<ONode> values = node.getArrayUnsafe();
        return index >= 0 && index < values.size() ? values.get(index) : null;
    }
}
