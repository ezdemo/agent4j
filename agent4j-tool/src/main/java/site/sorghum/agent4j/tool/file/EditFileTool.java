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
        return "Apply a SEARCH/REPLACE edit to an existing file.\n"
                + "`search` must match exactly (whitespace sensitive, no regex).\n"
                + "The match must be unique in the file — otherwise refused to avoid surprise rewrites.";
    }

    @Override
    public String toToolSpec() {
        return "### edit_file\n\n"
                + "描述：对已有文件执行 SEARCH/REPLACE 编辑。这是修改代码的主要工具。\n\n"
                + "## 关键规则（必须遵守）\n\n"
                + "1. **search 必须唯一**：要搜索的文本在文件中只能出现一次，否则工具拒绝执行。\n"
                + "2. **精确匹配**：search 文本必须与文件中完全一致（包括空格和换行），不支持正则。\n"
                + "3. **保留上下文**：search 应包含足够的前后文（3-5行），确保唯一匹配，同时避免不必要的\n"
                + "   大段重复导致 token 浪费。\n"
                + "4. **缩进敏感**：search/replace 中的缩进必须与源文件完全一致（推荐复制粘贴原文）。\n"
                + "5. **一次性完成**：对于同一文件的多个修改，尽量一次提供完整的 replace 内容，\n"
                + "   而不是分多次调用 edit_file。\n\n"
                + "## 正确示例\n\n"
                + "假设文件 `src/Hello.java` 内容为：\n"
                + "```\n"
                + "public class Hello {\n"
                + "    public void greet() {\n"
                + "        System.out.println(\"Hello!\");\n"
                + "    }\n"
                + "}\n"
                + "```\n\n"
                + "要修改 greet 方法的输出：\n"
                + "```\n"
                + "search: |\n"
                + "        System.out.println(\"Hello!\");\n"
                + "replace: |\n"
                + "        System.out.println(\"Hello, World!\");\n"
                + "```\n\n"
                + "参数：\n"
                + "  - path (string, 必填): 文件路径\n"
                + "  - search (string, 必填): 要搜索替换的精确文本（必须唯一）\n"
                + "  - replace (string, 必填): 替换后的文本\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
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
