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
    public String getName() { return "multi_edit"; }

    @Override
    public String getDescription() {
        return "Apply multiple SEARCH/REPLACE edits across one or more files atomically.\n"
                + "Each edit has path, search, replace.\n"
                + "Validates ALL edits first; writes ALL only if all pass.\n"
                + "On write failure, rolls back files that may have been modified.";
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
