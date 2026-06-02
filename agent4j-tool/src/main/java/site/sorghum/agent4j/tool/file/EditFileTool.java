package site.sorghum.agent4j.tool.file;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Component
public class EditFileTool extends AgentTool {

    @Override
    public String getName() {
        return "edit_file";
    }

    @Override
    public String getDescription() {
        return """
                Apply a SEARCH/REPLACE edit to an existing file.
                `search` must match exactly (whitespace sensitive, no regex).
                The match must be unique in the file — otherwise refused to avoid surprise rewrites.""";
    }

    @Override
    public String toToolSpec() {
        return """
                ### edit_file
                
                描述：对已有文件执行 SEARCH/REPLACE 编辑。这是修改代码的主要工具。
                
                ## 关键规则（必须遵守）
                
                1. **search 必须唯一**：要搜索的文本在文件中只能出现一次，否则工具拒绝执行。
                2. **精确匹配**：search 文本必须与文件中完全一致（包括空格和换行），不支持正则。
                3. **保留上下文**：search 应包含足够的前后文（3-5行），确保唯一匹配，同时避免不必要的
                   大段重复导致 token 浪费。
                4. **缩进敏感**：search/replace 中的缩进必须与源文件完全一致（推荐复制粘贴原文）。
                5. **一次性完成**：对于同一文件的多个修改，尽量一次提供完整的 replace 内容，
                   而不是分多次调用 edit_file。
                
                ## 正确示例
                
                假设文件 `src/Hello.java` 内容为：
                ```
                public class Hello {
                    public void greet() {
                        System.out.println("Hello!");
                    }
                }
                ```
                
                要修改 greet 方法的输出：
                ```
                search: |
                        System.out.println("Hello!");
                replace: |
                        System.out.println("Hello, World!");
                ```
                
                参数：
                  - path (string, 必填): 文件路径
                  - search (string, 必填): 要搜索替换的精确文本（必须唯一）
                  - replace (string, 必填): 替换后的文本
                
                只读：否
                风暴豁免：否""";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("path", "string", true, "文件路径"),
                new ToolParameter("search", "string", true, "要搜索替换的精确文本（必须唯一）"),
                new ToolParameter("replace", "string", true, "替换后的文本")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            String pathStr = ctx.getString("path");
            // 检查路径是否被屏蔽
            Path resolved = ctx.getRootDir().resolve(pathStr).toAbsolutePath().normalize();
            if (ctx.isPathBlocked(resolved)) {
                return ToolResult.fail("PATH_BLOCKED", "路径被屏蔽: " + pathStr);
            }
            String result = FileEdit.editFile(ctx.getRootDir(), pathStr,
                    ctx.getString("search"), ctx.getString("replace"));
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
