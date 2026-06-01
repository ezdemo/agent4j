package site.sorghum.agent4j.bin.util;

import org.noear.snack4.ONode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ONode 工具类 —— 提供 ONode ↔ Java Map/List 的转换。
 * <p>
 * 消除 ToolDispatcher / JsonlSessionStore 之间的重复代码。
 * </p>
 *
 * @author Sorghum
 */
public class ONodeUtil {

    /**
     * 将 ONode 对象转换为 Map<String, Object>（递归）
     */
    public static Map<String, Object> toMap(ONode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node.isObject()) {
            for (Map.Entry<String, ONode> e : node.getObject().entrySet()) {
                ONode val = e.getValue();
                if (val.isString()) result.put(e.getKey(), val.getString());
                else if (val.isNumber()) result.put(e.getKey(), val.getNumber());
                else if (val.isBoolean()) result.put(e.getKey(), val.getBoolean());
                else if (val.isArray()) result.put(e.getKey(), toList(val));
                else if (val.isObject()) result.put(e.getKey(), toMap(val));
                else result.put(e.getKey(), null);
            }
        }
        return result;
    }

    /**
     * 将 ONode 数组转换为 List<Object>（递归）
     */
    public static List<Object> toList(ONode node) {
        List<Object> list = new ArrayList<>();
        for (ONode item : node.getArray()) {
            if (item.isString()) list.add(item.getString());
            else if (item.isNumber()) list.add(item.getNumber());
            else if (item.isBoolean()) list.add(item.getBoolean());
            else if (item.isArray()) list.add(toList(item));
            else if (item.isObject()) list.add(toMap(item));
            else list.add(item.getString());
        }
        return list;
    }
}
