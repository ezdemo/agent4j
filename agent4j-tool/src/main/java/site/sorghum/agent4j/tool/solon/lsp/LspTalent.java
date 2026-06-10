package site.sorghum.agent4j.tool.solon.lsp;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ErrorCodes;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LSP 智能体工具——将 Language Server Protocol 功能暴露为 AI 可调用的工具。
 * <p>
 * 注册一个名为 "lsp" 的 {@link AgentTool}，通过 {@code operation} 参数分发到不同的 LSP 操作：
 * <ul>
 *   <li><b>definition</b> —— 跳转到定义</li>
 *   <li><b>references</b> —— 查找所有引用</li>
 *   <li><b>hover</b> —— 悬停类型/文档提示</li>
 *   <li><b>documentSymbol</b> —— 文档符号列表</li>
 *   <li><b>workspaceSymbol</b> —— 工作区符号搜索</li>
 *   <li><b>implementation</b> —— 跳转到实现</li>
 *   <li><b>callHierarchy</b> —— 调用层级（先 prepare 再 incoming/outgoing）</li>
 * </ul>
 * </p>
 *
 * <h3>配置方式</h3>
 * <p>Language Server 配置通过配置文件（如 app.yml）注入，在应用启动时调用
 * {@link LspClientManager#registerServer(LspServerParameters)} 注册。
 * 本 Talent 在 {@link #getTools()} 中返回工具列表供 LLM 调用。</p>
 *
 * <h3>使用示例（LLM 视角）</h3>
 * <pre>
 * lsp(filePath="/path/to/main.go", operation="definition", line=42, character=10)
 * lsp(filePath="/path/to/main.go", operation="references", line=42, character=10)
 * lsp(filePath="/path/to/main.go", operation="hover", line=42, character=10)
 * lsp(filePath="/path/to/main.go", operation="documentSymbol")
 * lsp(operation="workspaceSymbol", query="MyFunction")
 * lsp(filePath="/path/to/main.go", operation="callHierarchy", line=42, character=10)
 * </pre>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class LspTalent implements SolonToTools {


    /**
     * LSP 客户端管理器（单例，跨请求共享）
     */
    private final LspClientManager clientManager = new LspClientManager();

    /**
     * LSP 请求默认超时（秒）
     */
    private static final long DEFAULT_TIMEOUT_SECONDS = 12;

    // ======================================================================
    //  SolonToTools 接口
    // ======================================================================

    @Override
    public List<AgentTool> getTools() {
        return List.of(new LspAgentTool());
    }

    // ======================================================================
    //  公开方法：供外部注册 Language Server 配置
    // ======================================================================

    /**
     * 注册一个 Language Server 配置。
     * <p>调用时机：应用启动时，读取配置后调用。</p>
     */
    public void registerServer(LspServerParameters params) {
        clientManager.registerServer(params);
    }

    /**
     * 批量注册 Language Server 配置。
     */
    public void registerServers(List<LspServerParameters> paramsList) {
        clientManager.registerServers(paramsList);
    }

    /**
     * 获取 LSP 客户端管理器（供外部监控/调试使用）。
     */
    public LspClientManager getClientManager() {
        return clientManager;
    }

    // ======================================================================
    //  AgentTool 实现
    // ======================================================================

    /**
     * LSP AgentTool——将 LSP 操作封装为 AI 可调用的工具。
     */
    private class LspAgentTool extends AgentTool {

        @Override
        public String getName() {
            return "lsp";
        }

        @Override
        public String getDescription() {
            return """
                    Language Server Protocol 工具——通过 Language Server 对代码进行语义分析。
                    支持的操作（operation）：
                    - definition:     跳转到定义（需要 filePath、line、character）
                    - references:     查找所有引用（需要 filePath、line、character）
                    - hover:          获取悬停提示信息（需要 filePath、line、character）
                    - documentSymbol: 获取文档符号列表（需要 filePath）
                    - workspaceSymbol:在工作区中搜索符号（需要 query）
                    - implementation: 跳转到接口/方法的实现（需要 filePath、line、character）
                    - callHierarchy:  获取函数调用层级关系（需要 filePath、line、character）

                    使用前请确保相关 Language Server 已安装并正确配置。
                    line 参数为 1-based 行号（即编辑器中显示的行号）。
                    character 参数为 1-based 列号。
                    """;
        }

        @Override
        public List<ToolParameter> getParameters() {
            return List.of(
                    new ToolParameter("filePath", "string", false,
                            "源代码文件的绝对路径（workspaceSymbol 操作不需要此参数）"),
                    new ToolParameter("operation", "string", true,
                            "LSP 操作类型：definition, references, hover, documentSymbol, workspaceSymbol, implementation, callHierarchy"),
                    new ToolParameter("line", "int", false,
                            "光标行号（1-based，即编辑器显示的行号）。definition/references/hover/implementation/callHierarchy 需要"),
                    new ToolParameter("character", "int", false,
                            "光标列号（1-based，即编辑器显示的列号）。definition/references/hover/implementation/callHierarchy 需要"),
                    new ToolParameter("query", "string", false,
                            "搜索查询字符串（仅 workspaceSymbol 操作需要）")
            );
        }

        @Override
        public ToolResult execute(ToolContext ctx) {
            String filePath = ctx.getString("filePath");
            String operation = ctx.getString("operation");
            int line = ctx.getInt("line", 0);
            int character = ctx.getInt("character", 0);

            if (operation == null || operation.isBlank()) {
                return ToolResult.fail(ErrorCodes.ERROR, "缺少 operation 参数");
            }

            log.info("[LSP] 执行操作: operation={}, filePath={}, line={}, character={}",
                    operation, filePath, line, character);

            try {
                return switch (operation.toLowerCase()) {
                    case "definition" -> handleDefinition(filePath, line, character);
                    case "references" -> handleReferences(filePath, line, character);
                    case "hover" -> handleHover(filePath, line, character);
                    case "documentsymbol" -> handleDocumentSymbol(filePath);
                    case "workspacesymbol" -> handleWorkspaceSymbol(ctx.getString("query"));
                    case "implementation" -> handleImplementation(filePath, line, character);
                    case "callhierarchy" -> handleCallHierarchy(filePath, line, character);
                    default -> ToolResult.fail(ErrorCodes.ERROR,
                            "未知 LSP 操作: " + operation
                                    + "。支持的操作: definition, references, hover, documentSymbol, workspaceSymbol, implementation, callHierarchy");
                };
            } catch (Exception e) {
                log.error("[LSP] 操作失败: {} - {}", operation, e.getMessage(), e);
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "LSP " + operation + " 执行失败: " + e.getMessage());
            }
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        // ==================================================================
        //  操作处理器
        // ==================================================================

        private ToolResult handleDefinition(String filePath, int line, int character) {
            LspClient client = requireClient(filePath);
            if (client == null) return noServerResult(filePath);

            client.touchFile(filePath);
            Position pos = toPosition(line, character);
            TextDocumentIdentifier doc = toDocId(filePath);

            try {
                List<? extends Location> locations = client.definition(doc, pos)
                        .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (locations == null || locations.isEmpty()) {
                    return ToolResult.ok("未找到定义");
                }
                return ToolResult.ok(formatLocations("定义", locations));
            } catch (Exception e) {
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "查找定义失败: " + e.getMessage());
            }
        }

        private ToolResult handleReferences(String filePath, int line, int character) {
            LspClient client = requireClient(filePath);
            if (client == null) return noServerResult(filePath);

            client.touchFile(filePath);
            Position pos = toPosition(line, character);
            TextDocumentIdentifier doc = toDocId(filePath);

            ReferenceParams params = new ReferenceParams();
            params.setTextDocument(doc);
            params.setPosition(pos);
            ReferenceContext context = new ReferenceContext();
            context.setIncludeDeclaration(true);
            params.setContext(context);

            try {
                List<? extends Location> locations = client.references(params)
                        .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (locations == null || locations.isEmpty()) {
                    return ToolResult.ok("未找到引用");
                }
                return ToolResult.ok(formatLocations("引用", locations));
            } catch (Exception e) {
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "查找引用失败: " + e.getMessage());
            }
        }

        private ToolResult handleHover(String filePath, int line, int character) {
            LspClient client = requireClient(filePath);
            if (client == null) return noServerResult(filePath);

            client.touchFile(filePath);
            Position pos = toPosition(line, character);
            TextDocumentIdentifier doc = toDocId(filePath);
            TextDocumentPositionParams params = new TextDocumentPositionParams(doc, pos);

            try {
                Hover hover = client.hover(params)
                        .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (hover == null) {
                    return ToolResult.ok("无悬停信息");
                }
                String content = hover.getContents().isLeft()
                        ? hover.getContents().getLeft().stream()
                        .map(me -> me.isLeft() ? me.getLeft() : me.getRight().getValue())
                        .collect(Collectors.joining("\n"))
                        : hover.getContents().getRight().getValue();
                StringBuilder sb = new StringBuilder();
                sb.append("=== 悬停信息 ===\n");
                sb.append(content);
                if (hover.getRange() != null) {
                    sb.append("\n范围: ").append(formatRange(hover.getRange()));
                }
                return ToolResult.ok(sb.toString());
            } catch (Exception e) {
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "获取悬停信息失败: " + e.getMessage());
            }
        }

        private ToolResult handleDocumentSymbol(String filePath) {
            LspClient client = requireClient(filePath);
            if (client == null) return noServerResult(filePath);

            client.touchFile(filePath);
            TextDocumentIdentifier doc = toDocId(filePath);
            DocumentSymbolParams params = new DocumentSymbolParams(doc);

            try {
                List<Either<SymbolInformation, DocumentSymbol>> symbols = client.documentSymbol(params)
                        .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (symbols == null || symbols.isEmpty()) {
                    return ToolResult.ok("文档中无符号");
                }
                return ToolResult.ok(formatDocumentSymbols(symbols));
            } catch (Exception e) {
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "获取文档符号失败: " + e.getMessage());
            }
        }

        private ToolResult handleWorkspaceSymbol(String query) {
            if (query == null || query.isBlank()) {
                return ToolResult.fail(ErrorCodes.ERROR, "workspaceSymbol 操作需要 query 参数");
            }

            // workspaceSymbol 不绑定特定文件，尝试所有活跃的客户端
            WorkspaceSymbolParams params = new WorkspaceSymbolParams(query);
            StringBuilder allResults = new StringBuilder();
            boolean anySuccess = false;

            for (String serverName : clientManager.getActiveServerNames()) {
                LspClient client = clientManager.getRunningClient(serverName);
                if (client == null) continue;

                try {
                    List<? extends SymbolInformation> symbols = client.workspaceSymbol(params)
                            .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (symbols != null && !symbols.isEmpty()) {
                        allResults.append("--- ").append(serverName).append(" ---\n");
                        allResults.append(formatSymbolInformations(symbols)).append("\n");
                        anySuccess = true;
                    }
                } catch (Exception e) {
                    log.debug("[LSP] workspaceSymbol 在 {} 上失败: {}", serverName, e.getMessage());
                }
            }

            if (!anySuccess) {
                return ToolResult.ok("未找到匹配的符号: " + query);
            }
            return ToolResult.ok("=== 工作区符号搜索结果: \"" + query + "\" ===\n" + allResults);
        }

        private ToolResult handleImplementation(String filePath, int line, int character) {
            LspClient client = requireClient(filePath);
            if (client == null) return noServerResult(filePath);

            client.touchFile(filePath);
            Position pos = toPosition(line, character);
            TextDocumentIdentifier doc = toDocId(filePath);
            TextDocumentPositionParams params = new TextDocumentPositionParams(doc, pos);

            try {
                List<? extends Location> locations = client.implementation(params)
                        .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (locations == null || locations.isEmpty()) {
                    return ToolResult.ok("未找到实现");
                }
                return ToolResult.ok(formatLocations("实现", locations));
            } catch (Exception e) {
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "查找实现失败: " + e.getMessage());
            }
        }

        private ToolResult handleCallHierarchy(String filePath, int line, int character) {
            LspClient client = requireClient(filePath);
            if (client == null) return noServerResult(filePath);

            client.touchFile(filePath);
            Position pos = toPosition(line, character);
            TextDocumentIdentifier doc = toDocId(filePath);
            CallHierarchyPrepareParams prepParams = new CallHierarchyPrepareParams(doc, pos);

            try {
                // Step 1: prepareCallHierarchy
                List<CallHierarchyItem> items = client.prepareCallHierarchy(prepParams)
                        .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (items == null || items.isEmpty()) {
                    return ToolResult.ok("当前光标位置无可用的调用层级信息");
                }

                StringBuilder sb = new StringBuilder();
                sb.append("=== 调用层级 ===\n");

                for (CallHierarchyItem item : items) {
                    sb.append("目标: ").append(item.getName())
                            .append(" (").append(item.getKind()).append(")")
                            .append(" @ ").append(formatRange(item.getRange()))
                            .append("\n");

                    // Step 2: incomingCalls（谁调用了这个函数）
                    CallHierarchyIncomingCallsParams incomingParams =
                            new CallHierarchyIncomingCallsParams(item);
                    try {
                        List<CallHierarchyIncomingCall> incoming = client.incomingCalls(incomingParams)
                                .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        if (incoming != null && !incoming.isEmpty()) {
                            sb.append("  传入调用 (调用者):\n");
                            for (CallHierarchyIncomingCall call : incoming) {
                                sb.append("    - ").append(call.getFrom().getName())
                                        .append(" @ ").append(formatRange(call.getFrom().getRange()));
                                if (call.getFromRanges() != null && !call.getFromRanges().isEmpty()) {
                                    sb.append(" (来自 ").append(call.getFromRanges().size()).append(" 处)");
                                }
                                sb.append("\n");
                            }
                        }
                    } catch (Exception e) {
                        sb.append("  传入调用: 获取失败 (").append(e.getMessage()).append(")\n");
                    }

                    // Step 3: outgoingCalls（这个函数调用了谁）
                    CallHierarchyOutgoingCallsParams outgoingParams =
                            new CallHierarchyOutgoingCallsParams(item);
                    try {
                        List<CallHierarchyOutgoingCall> outgoing = client.outgoingCalls(outgoingParams)
                                .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        if (outgoing != null && !outgoing.isEmpty()) {
                            sb.append("  传出调用 (被调用者):\n");
                            for (CallHierarchyOutgoingCall call : outgoing) {
                                sb.append("    - ").append(call.getTo().getName())
                                        .append(" @ ").append(formatRange(call.getTo().getRange()));
                                if (call.getFromRanges() != null && !call.getFromRanges().isEmpty()) {
                                    sb.append(" (来自 ").append(call.getFromRanges().size()).append(" 处)");
                                }
                                sb.append("\n");
                            }
                        }
                    } catch (Exception e) {
                        sb.append("  传出调用: 获取失败 (").append(e.getMessage()).append(")\n");
                    }
                }

                return ToolResult.ok(sb.toString());
            } catch (Exception e) {
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "获取调用层级失败: " + e.getMessage());
            }
        }

        // ==================================================================
        //  辅助方法
        // ==================================================================

        /**
         * 根据文件路径获取 LSP 客户端，如果无匹配则返回 null。
         */
        private LspClient requireClient(String filePath) {
            if (filePath == null || filePath.isBlank()) {
                return null;
            }
            return clientManager.getClientForFile(filePath);
        }

        /**
         * 返回"无匹配 Language Server"的错误结果。
         */
        private ToolResult noServerResult(String filePath) {
            return ToolResult.fail(ErrorCodes.FILE_NOT_FOUND,
                    "未找到匹配文件 " + filePath + " 的 Language Server。"
                            + "请确认已安装并配置对应的 Language Server（已注册: "
                            + clientManager.getRegisteredServerNames() + "）");
        }

        /**
         * 将 1-based 行/列号转换为 LSP 0-based Position。
         */
        private Position toPosition(int line, int character) {
            // LSP Position 是 0-based；LLM 输入是 1-based
            int l = Math.max(0, line - 1);
            int c = Math.max(0, character - 1);
            return new Position(l, c);
        }

        /**
         * 将文件路径转换为 TextDocumentIdentifier。
         */
        private TextDocumentIdentifier toDocId(String filePath) {
            String uri = Paths.get(filePath).toUri().toString();
            return new TextDocumentIdentifier(uri);
        }

        /**
         * 格式化 Location 列表。
         */
        private String formatLocations(String label, List<? extends Location> locations) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(label).append(" (").append(locations.size()).append(" 处) ===\n");
            int count = 0;
            for (Location loc : locations) {
                if (count >= 50) {
                    sb.append("... 还有 ").append(locations.size() - 50).append(" 处（已截断）\n");
                    break;
                }
                sb.append(count + 1).append(". ");
                sb.append(formatLocation(loc)).append("\n");
                count++;
            }
            return sb.toString();
        }

        /**
         * 格式化单个 Location。
         */
        private String formatLocation(Location loc) {
            StringBuilder sb = new StringBuilder();
            sb.append(loc.getUri());
            if (loc.getRange() != null) {
                sb.append(":").append(formatRange(loc.getRange()));
            }
            return sb.toString();
        }

        /**
         * 格式化 Range 为 "L{startLine}:C{startChar}-L{endLine}:C{endChar}"。
         */
        private String formatRange(Range range) {
            if (range == null) return "";
            return String.format("L%d:%d-L%d:%d",
                    range.getStart().getLine() + 1,
                    range.getStart().getCharacter() + 1,
                    range.getEnd().getLine() + 1,
                    range.getEnd().getCharacter() + 1);
        }

        /**
         * 格式化文档符号列表（递归处理层级）。
         */
        private String formatDocumentSymbols(List<Either<SymbolInformation, DocumentSymbol>> symbols) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== 文档符号 (").append(symbols.size()).append(" 个) ===\n");
            for (Either<SymbolInformation, DocumentSymbol> either : symbols) {
                if (either.isRight()) {
                    formatDocumentSymbol(either.getRight(), sb, 0);
                } else if (either.isLeft()) {
                    SymbolInformation si = either.getLeft();
                    sb.append("  ").append(si.getName())
                            .append(" (").append(si.getKind()).append(")")
                            .append(" @ ").append(formatLocation(si.getLocation()))
                            .append("\n");
                }
            }
            return sb.toString();
        }

        /**
         * 递归格式化单个 DocumentSymbol。
         */
        private void formatDocumentSymbol(DocumentSymbol sym, StringBuilder sb, int depth) {
            String indent = "  ".repeat(depth);
            sb.append(indent).append(sym.getName())
                    .append(" (").append(sym.getKind()).append(")");
            if (sym.getRange() != null) {
                sb.append(" @ ").append(formatRange(sym.getRange()));
            }
            if (sym.getDetail() != null && !sym.getDetail().isBlank()) {
                sb.append(" — ").append(sym.getDetail());
            }
            sb.append("\n");

            if (sym.getChildren() != null) {
                for (DocumentSymbol child : sym.getChildren()) {
                    formatDocumentSymbol(child, sb, depth + 1);
                }
            }
        }

        /**
         * 格式化 SymbolInformation 列表。
         */
        private String formatSymbolInformations(List<? extends SymbolInformation> symbols) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < symbols.size(); i++) {
                SymbolInformation si = symbols.get(i);
                sb.append(i + 1).append(". ").append(si.getName())
                        .append(" (").append(si.getKind()).append(")")
                        .append(" @ ").append(si.getLocation().getUri());
                if (si.getLocation().getRange() != null) {
                    sb.append(":").append(formatRange(si.getLocation().getRange()));
                }
                if (si.getContainerName() != null && !si.getContainerName().isBlank()) {
                    sb.append(" [in ").append(si.getContainerName()).append("]");
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }
}
