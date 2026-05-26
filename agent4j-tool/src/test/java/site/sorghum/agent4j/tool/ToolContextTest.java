package site.sorghum.agent4j.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolContext} 单元测试。
 *
 * @author Sorghum
 */
@DisplayName("ToolContext 工具上下文测试")
class ToolContextTest {

    @Nested
    @DisplayName("参数访问")
    class ParamAccess {

        @Test
        @DisplayName("getString 应返回字符串参数")
        void getString_shouldReturnString() {
            Map<String, Object> params = new HashMap<>();
            params.put("name", "test");
            ToolContext ctx = new ToolContext(params);
            assertEquals("test", ctx.getString("name"));
        }

        @Test
        @DisplayName("getString 不存在的键应返回 null")
        void getString_missingKey_shouldReturnNull() {
            ToolContext ctx = new ToolContext(new HashMap<>());
            assertNull(ctx.getString("nonexistent"));
        }

        @Test
        @DisplayName("getString 带默认值应回退")
        void getString_withDefault_shouldFallback() {
            ToolContext ctx = new ToolContext(new HashMap<>());
            assertEquals("default", ctx.getString("missing", "default"));
        }

        @Test
        @DisplayName("getInt 应返回整数参数")
        void getInt_shouldReturnInt() {
            Map<String, Object> params = new HashMap<>();
            params.put("count", 42);
            ToolContext ctx = new ToolContext(params);
            assertEquals(42, ctx.getInt("count", 0));
        }

        @Test
        @DisplayName("getInt 字符串数字应被解析")
        void getInt_stringNumber_shouldParse() {
            Map<String, Object> params = new HashMap<>();
            params.put("count", "42");
            ToolContext ctx = new ToolContext(params);
            assertEquals(42, ctx.getInt("count", 0));
        }

        @Test
        @DisplayName("getInt 不存在的键应返回默认值")
        void getInt_missingKey_shouldReturnDefault() {
            ToolContext ctx = new ToolContext(new HashMap<>());
            assertEquals(99, ctx.getInt("missing", 99));
        }

        @Test
        @DisplayName("getBool 应返回布尔参数")
        void getBool_shouldReturnBoolean() {
            Map<String, Object> params = new HashMap<>();
            params.put("flag", true);
            ToolContext ctx = new ToolContext(params);
            assertTrue(ctx.getBool("flag", false));
        }

        @Test
        @DisplayName("getBool 字符串 true 应被解析")
        void getBool_stringTrue_shouldParse() {
            Map<String, Object> params = new HashMap<>();
            params.put("flag", "true");
            ToolContext ctx = new ToolContext(params);
            assertTrue(ctx.getBool("flag", false));
        }

        @Test
        @DisplayName("has 应检测参数存在")
        void has_shouldDetectExistence() {
            Map<String, Object> params = new HashMap<>();
            params.put("exists", "value");
            ToolContext ctx = new ToolContext(params);
            assertTrue(ctx.has("exists"));
            assertFalse(ctx.has("missing"));
        }

        @Test
        @DisplayName("paramCount 应返回参数数量")
        void paramCount_shouldReturnCount() {
            Map<String, Object> params = new HashMap<>();
            params.put("a", 1);
            params.put("b", 2);
            ToolContext ctx = new ToolContext(params);
            assertEquals(2, ctx.paramCount());
        }
    }

    @Nested
    @DisplayName("路径屏蔽")
    class PathBlocking {

        @Test
        @DisplayName("isPathBlocked 应检测屏蔽路径")
        void isPathBlocked_shouldDetectBlockedPath(@TempDir Path tempDir) {
            List<String> blockedPaths = Collections.singletonList("secret");
            Map<String, Object> params = new HashMap<>();
            ToolContext ctx = new ToolContext(params, tempDir, null, null, null, blockedPaths);

            Path blocked = tempDir.resolve("secret/data.txt");
            assertTrue(ctx.isPathBlocked(blocked));
        }

        @Test
        @DisplayName("isPathBlocked 非屏蔽路径应返回 false")
        void isPathBlocked_otherPath_shouldReturnFalse(@TempDir Path tempDir) {
            List<String> blockedPaths = Collections.singletonList("secret");
            ToolContext ctx = new ToolContext(new HashMap<>(), tempDir, null, null, null, blockedPaths);

            Path allowed = tempDir.resolve("public/data.txt");
            assertFalse(ctx.isPathBlocked(allowed));
        }

        @Test
        @DisplayName("isPathBlocked 空屏蔽列表应返回 false")
        void isPathBlocked_emptyList_shouldReturnFalse(@TempDir Path tempDir) {
            ToolContext ctx = new ToolContext(new HashMap<>(), tempDir, null, null, null, Collections.emptyList());
            Path p = tempDir.resolve("any.txt");
            assertFalse(ctx.isPathBlocked(p));
        }
    }

    @Nested
    @DisplayName("构造与不可变性")
    class Construction {

        @Test
        @DisplayName("空参数构造应不抛异常")
        void emptyConstructor_shouldNotThrow() {
            ToolContext ctx = new ToolContext(new HashMap<>());
            assertEquals(0, ctx.paramCount());
            assertNull(ctx.getRootDir());
        }

        @Test
        @DisplayName("参数 Map 应被防御性复制")
        void params_shouldBeDefensivelyCopied() {
            Map<String, Object> original = new HashMap<>();
            original.put("key", "value");
            ToolContext ctx = new ToolContext(original);
            original.put("key", "modified");
            assertEquals("value", ctx.getString("key"));
        }

        @Test
        @DisplayName("blockedPaths 应保持引用一致性（非防御性复制）")
        void blockedPaths_shouldMaintainReference() {
            List<String> original = new ArrayList<>();
            original.add("secret");
            ToolContext ctx = new ToolContext(new HashMap<>(), null, null, null, null, original);
            // 当前实现不进行防御性复制，修改原列表会反映到 ctx
            original.add("more");
            assertEquals(2, ctx.getBlockedPaths().size());
        }

        @Test
        @DisplayName("null params 应处理")
        void nullParams_shouldBeHandled() {
            ToolContext ctx = new ToolContext(null);
            assertEquals(0, ctx.paramCount());
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("路径越界应返回 false（由 resolveSafe 处理）")
        void isPathBlocked_outsideRoot_shouldReturnFalse(@TempDir Path tempDir) {
            ToolContext ctx = new ToolContext(new HashMap<>(), tempDir);
            Path outside = tempDir.getParent().resolve("outside.txt");
            assertFalse(ctx.isPathBlocked(outside));
        }

        @Test
        @DisplayName("null 路径应返回 false")
        void isPathBlocked_nullPath_shouldReturnFalse(@TempDir Path tempDir) {
            ToolContext ctx = new ToolContext(new HashMap<>(), tempDir);
            assertFalse(ctx.isPathBlocked(null));
        }
    }
}
