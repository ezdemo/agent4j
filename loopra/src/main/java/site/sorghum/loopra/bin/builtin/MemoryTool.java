package site.sorghum.loopra.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.agent.memory.ProjectMemoryStore;
import site.sorghum.loopra.tool.SolonToTools;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Memory 工具 —— 跨会话项目记忆的主动读写。
 * <p>
 * 记忆存储在项目内 {@code .loopra/loopra-memory.md}，记录值得跨会话长期保留的
 * 项目级事实（架构决策、约定、踩坑、用户偏好、高频复用事实等）。
 * </p>
 * <p>
 * 设计取舍：记忆不自动注入系统提示词，而由 AI 按需检索，避免每轮占用上下文。
 * {@code DEFAULT_SYSTEM_PROMPT} 中引导 AI 首次接入项目时主动调用本工具搜索已有记忆。
 * compact 折叠时也会自动沉淀记忆，本工具补充「主动记录」能力。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class MemoryTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "memory", description = """
            读写跨会话的项目记忆（存储在项目 .loopra/loopra-memory.md）。
            记录值得长期保留的项目级事实：架构决策、约定、踩坑教训、用户偏好、高频复用事实。

            - action=search：检索已有记忆。可选 keyword 做关键词过滤，无关键词返回全部。
              首次接入一个项目时，应主动调用 search 检索已有记忆，避免重复探索。
            - action=add：追加一条记忆。content 用简洁中文，每条聚焦一个事实。
              仅记录确定且可复用的事实，不要记录临时状态或单次任务细节。
            - action=list：列出所有记忆条目并带编号，用于查看全量和定位删除。
            - action=delete：删除指定编号的记忆条目。需提供 index（1-based，来自 list 的编号）。
              当发现某条记忆过期、错误或冗余时使用，避免记忆无限增长。

            注意：记忆跨会话持久保留，请谨慎写入与删除。
            """)
    public String memory(
            @Param(name = "action", description = "操作类型：search（检索）| add（追加）| list（列出带编号）| delete（按编号删除）") String action,
            @Param(name = "content", description = "add 时必填：要记录的事实，简洁中文；其他 action 可留空",
                    required = false) String content,
            @Param(name = "keyword", description = "search 时可选：关键词过滤，只返回包含该词的条目",
                    required = false) String keyword,
            @Param(name = "index", description = "delete 时必填：要删除的条目编号（1-based，先用 action=list 查看）",
                    required = false) String index,
            ToolContext ctx) {

        if (action == null || action.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'action'（search 或 add）";
        }
        Path workspace = ctx.getRootDir();
        if (workspace == null) {
            return "WORKSPACE_MISSING: 无法获取项目路径，记忆工具不可用";
        }

        return switch (action.trim().toLowerCase()) {
            case "search" -> doSearch(workspace, keyword);
            case "add" -> doAdd(workspace, content);
            case "list" -> doList(workspace);
            case "delete" -> doDelete(workspace, index);
            default -> "INVALID_ACTION: action 只能是 search、add、list 或 delete，收到: " + action;
        };
    }

    /** 检索记忆：无关键词返回全部，有关键词只返回含该词的条目。 */
    private String doSearch(Path workspace, String keyword) {
        String memory = ProjectMemoryStore.load(workspace);
        if (memory == null || memory.isEmpty()) {
            return "记忆为空：本项目尚无已沉淀的记忆。你可以用 action=add 记录第一条。";
        }
        if (keyword == null || keyword.isBlank()) {
            return memory;
        }
        // 按关键词过滤条目（以 "## " 分隔的块）
        String kw = keyword.trim();
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String entry : memory.split("(?m)(?=^## )", 0)) {
            if (entry.contains(kw)) {
                sb.append(entry.trim()).append("\n\n");
                found = true;
            }
        }
        if (!found) {
            return "未找到包含「" + kw + "」的记忆条目。";
        }
        return sb.toString().trim();
    }

    /** 追加一条记忆。 */
    private String doAdd(Path workspace, String content) {
        if (content == null || content.isBlank()) {
            return "PARAM_MISSING: add 操作需要提供 content";
        }
        String trimmed = content.trim();
        if ("无".equals(trimmed) || "无。".equals(trimmed)) {
            return "SKIP: 内容为「无」，未记录";
        }
        ProjectMemoryStore.append(workspace, trimmed);
        log.info("[memory] AI 主动记录记忆: {}", trimmed.replaceAll("\\s+", " "));
        return "已记录记忆：" + trimmed;
    }

    /** 列出所有记忆条目并带编号，用于查看全量和定位删除。 */
    private String doList(Path workspace) {
        List<String> entries = ProjectMemoryStore.listEntries(workspace);
        if (entries.isEmpty()) {
            return "记忆为空：本项目尚无已沉淀的记忆。你可以用 action=add 记录第一条。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(entries.size()).append(" 条记忆：\n\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
              .append(entries.get(i).trim()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 按编号删除一条记忆。 */
    private String doDelete(Path workspace, String index) {
        if (index == null || index.isBlank()) {
            return "PARAM_MISSING: delete 操作需要提供 index（1-based 编号，先用 action=list 查看）";
        }
        int idx;
        try {
            idx = Integer.parseInt(index.trim());
        } catch (NumberFormatException e) {
            return "PARAM_INVALID: index 必须是正整数，收到: " + index;
        }
        List<String> before = ProjectMemoryStore.listEntries(workspace);
        if (before.isEmpty()) {
            return "记忆为空，无可删除条目";
        }
        if (idx < 1 || idx > before.size()) {
            return "INDEX_OUT_OF_RANGE: 编号越界，当前共 " + before.size() + " 条，有效范围 1-" + before.size() + "，收到: " + idx;
        }
        String removed = before.get(idx - 1).trim();
        boolean ok = ProjectMemoryStore.deleteByIndex(workspace, idx);
        if (ok) {
            log.info("[memory] AI 删除第 {} 条记忆: {}", idx, removed.replaceAll("\\s+", " "));
            return "已删除第 " + idx + " 条记忆：" + removed;
        }
        return "DELETE_FAILED: 删除失败，请用 action=list 确认编号后重试";
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
