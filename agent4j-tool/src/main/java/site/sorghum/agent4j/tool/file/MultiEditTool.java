package site.sorghum.agent4j.tool.file;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class MultiEditTool extends AgentTool {

    @Override
    public String getName() {
        return "multi_edit";
    }

    @Override
    public String getDescription() {
        return """
                Apply multiple SEARCH/REPLACE edits across one or more files atomically.
                Each edit has path, search, replace.
                Validates ALL edits first; writes ALL only if all pass.
                On write failure, rolls back files that may have been modified.""";
    }

    @Override
    public String toToolSpec() {
        return """
                ### multi_edit
                
                描述：跨一个或多个文件原子性地执行批量 SEARCH/REPLACE 编辑。
                
                ## 关键规则
                
                1. **原子性**：先验证所有编辑项，全部通过验证后才执行写入。
                   如果某个编辑验证失败，所有编辑都不会执行。
                2. **回滚保护**：写入过程中如果某一步失败，已写入的文件会被回滚到原始状态。
                3. **每项的 search 必须唯一**：同 edit_file 规则。
                4. **适合场景**：需要同时修改多个文件（如重命名 API、重构），或者单个文件
                   多次编辑时可以用 multi_edit 替代多次 edit_file 调用。
                
                参数：
                  - edits (array, 必填): 编辑列表，每项包含:
                      - path (string): 文件路径
                      - search (string): 要搜索的精确文本
                      - replace (string): 替换后的文本
                
                只读：否
                风暴豁免：否""";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("edits", "array", false,
                        "编辑列表 [{path, search, replace}, ...]，不能为空。每项的 search 必须唯一")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolContext ctx) {
        try {
            List<Map<String, Object>> edits = (List<Map<String, Object>>) ctx.getParams().get("edits");
            if (edits != null) {
                // 检查每个编辑的路径是否被屏蔽
                for (int i = 0; i < edits.size(); i++) {
                    Map<String, Object> ed = edits.get(i);
                    String epath = ed != null ? String.valueOf(ed.get("path")) : null;
                    if (epath != null && !epath.isEmpty()) {
                        Path resolved = ctx.getRootDir().resolve(epath).toAbsolutePath().normalize();
                        if (ctx.isPathBlocked(resolved)) {
                            return ToolResult.fail("PATH_BLOCKED",
                                    "multi_edit #" + (i + 1) + " 路径被屏蔽: " + epath);
                        }
                    }
                }
            }
            String result = FileEdit.multiEdit(ctx.getRootDir(), edits);
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
