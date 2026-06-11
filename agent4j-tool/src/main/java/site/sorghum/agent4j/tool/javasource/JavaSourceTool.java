package site.sorghum.agent4j.tool.javasource;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ErrorCodes;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.nio.file.Path;
import java.util.List;

/**
 * Java 源码查找工具 —— 通过全限定类名查找并返回 Java 源代码。
 * <p>
 * 搜索模式：先遍历项目树查找 {@code .java} 文件，
 * 再扫描 {@code ~/.m2/repository} 和 {@code ~/.gradle/caches} 中
 * 路径包含 {@code jarKeyword} 的 jar 包。
 * </p>
 * <p>
 * 移植自 DeepSeek-Reasonix TypeScript 实现。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class JavaSourceTool extends AgentTool {

    @Override
    public String getName() {
        return "java_source";
    }

    @Override
    public String getDescription() {
        return """
                Find and return Java source code by fully-qualified class name.

                Search mode: walk the project tree for a `.java` file, then scan `~/.m2/repository` jars \
                whose filename or path contains `jarKeyword`.

                Returns the source text (or decompiled bytecode) on success, or a clear 'not found' message.
                Only call this tool once per class name — it's I/O heavy.
                """;
    }

    @Override
    public String toToolSpec() {
        return """
                ### java_source

                描述：通过全限定类名查找并返回 Java 源代码。
                搜索模式：先遍历项目树查找 `.java` 文件，再扫描 `~/.m2/repository` 中路径包含 `jarKeyword` 的 jar 包。
                成功时返回源码文本（或反编译字节码），失败时返回清晰的 'not found' 消息。
                注意：每个类名只调用一次此工具——它是 I/O 密集型操作。
                参数: className(必填), jarKeyword(必填)。只读。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return List.of(
                new ToolParameter("className", "string", true,
                        "Fully qualified Java class name, e.g. \"com.google.common.collect.Lists\" or \"org.springframework.web.servlet.DispatcherServlet\"."),
                new ToolParameter("jarKeyword", "string", true,
                        "Only search jars whose filename or path contains this keyword (case-insensitive). Keep it short — a narrow substring like \"spring-core\", \"guava\", or \"mycompany-utils\" scans faster and matches more precisely than a long fragment.")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        // ── 校验 className ──
        String className = ctx.getString("className");
        if (className == null || className.isBlank()) {
            return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                    "java_source: `className` is required");
        }
        className = className.trim();

        // 校验全限定名格式：字母.$_ 分段
        if (!className.matches("^[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)*$")) {
            return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                    "java_source: \"" + className + "\" is not a valid fully qualified Java class name. " +
                            "Expected format: `com.example.MyClass`");
        }

        // ── 校验 jarKeyword ──
        String jarKeyword = ctx.getString("jarKeyword");
        if (jarKeyword == null || jarKeyword.isBlank()) {
            return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                    "java_source: `jarKeyword` must not be empty");
        }
        jarKeyword = jarKeyword.trim();

        // ── 解析项目根目录 ──
        Path projectRoot = ctx.getRootDir();
        if (projectRoot == null) {
            projectRoot = Path.of(".").toAbsolutePath().normalize();
        }

        // ── 执行搜索 ──
        try {
            ClassSourceFinder finder = new ClassSourceFinder(projectRoot);
            ClassSourceFinder.FindResult result = finder.findSource(className, jarKeyword);

            if (!result.found()) {
                String message = String.format(
                        "No source found for \"%s\". Searched:\n" +
                                "  • %s/ for matching .java files\n" +
                                "  • Maven .m2 / Gradle cache for jars containing keyword \"%s\"\n\n" +
                                "Try a different keyword, or check if the class is in a different library.",
                        className, projectRoot, jarKeyword
                );

                ONode json = new ONode()
                        .set("status", "not-found")
                        .set("className", className)
                        .set("message", message);
                return ToolResult.ok(json.toJson());
            }

            ONode json = new ONode()
                    .set("status", "found")
                    .set("className", result.className())
                    .set("method", result.method().getLabel())
                    .set("sourcePath", result.sourcePath())
                    .set("source", result.source());
            return ToolResult.ok(json.toJson());
        } catch (Exception e) {
            log.warn("java_source 执行失败，类 '{}'，关键字 '{}': {}",
                    className, jarKeyword, e.getMessage(), e);
            return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                    "java_source execution error: " + e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}
