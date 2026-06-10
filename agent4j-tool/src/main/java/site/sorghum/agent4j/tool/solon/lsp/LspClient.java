package site.sorghum.agent4j.tool.solon.lsp;

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
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP 客户端接口——继承 {@link LanguageClient}，并扩展 AI 工具所需的 LSP 功能方法。
 * <p>
 * LanguageClient 部分：接收 Language Server 推送的通知（如 publishDiagnostics、logMessage）。
 * 扩展方法部分：封装对 Language Server 的请求调用（如 definition、references、hover 等）。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>所有 LSP 请求方法均返回 {@link CompletableFuture}，调用方自行决定同步/异步等待。</p>
 *
 * @author Sorghum
 */
public interface LspClient extends LanguageClient {

    // ==================== 基础导航 ====================

    /**
     * 跳转到定义（Go to Definition）。
     *
     * @param document 文本文件标识
     * @param position 光标位置
     * @return 定义位置列表
     */
    CompletableFuture<List<? extends Location>> definition(TextDocumentIdentifier document,
                                                           Position position);

    /**
     * 查找所有引用（Find All References）。
     *
     * @param params 引用查询参数（包含文件、位置、是否包含声明等）
     * @return 引用位置列表
     */
    CompletableFuture<List<? extends Location>> references(ReferenceParams params);

    /**
     * 悬停提示（Hover）。
     *
     * @param params 文本文件位置参数
     * @return 悬停信息
     */
    CompletableFuture<Hover> hover(TextDocumentPositionParams params);

    // ==================== 符号查询 ====================

    /**
     * 文档符号（Document Symbols）。
     *
     * @param params 文档符号查询参数
     * @return 符号信息或文档符号列表
     */
    CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params);

    /**
     * 工作区符号搜索（Workspace Symbols）。
     *
     * @param params 工作区符号搜索参数（含 query）
     * @return 符号信息列表
     */
    CompletableFuture<List<? extends SymbolInformation>> workspaceSymbol(
            WorkspaceSymbolParams params);

    // ==================== 实现/类型层级 ====================

    /**
     * 跳转到实现（Go to Implementation）。
     *
     * @param params 文本文件位置参数
     * @return 实现位置列表
     */
    CompletableFuture<List<? extends Location>> implementation(TextDocumentPositionParams params);

    // ==================== 调用层级 ====================

    /**
     * 准备调用层级（Prepare Call Hierarchy）。
     *
     * @param params 调用层级准备参数
     * @return 调用层级项列表
     */
    CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(
            CallHierarchyPrepareParams params);

    /**
     * 获取传入调用（Incoming Calls）。
     *
     * @param params 传入调用查询参数
     * @return 传入调用列表
     */
    CompletableFuture<List<CallHierarchyIncomingCall>> incomingCalls(
            CallHierarchyIncomingCallsParams params);

    /**
     * 获取传出调用（Outgoing Calls）。
     *
     * @param params 传出调用查询参数
     * @return 传出调用列表
     */
    CompletableFuture<List<CallHierarchyOutgoingCall>> outgoingCalls(
            CallHierarchyOutgoingCallsParams params);

    // ==================== 生命周期 ====================

    /**
     * 关闭 LSP 连接并终止 Language Server 进程。
     */
    void shutdown();

    /**
     * 通知 Language Server 打开/同步文件（确保文件内容已同步）。
     *
     * @param filePath 文件的绝对路径
     */
    void touchFile(String filePath);

    /**
     * Language Server 进程是否正在运行。
     *
     * @return true 如果进程存活且连接正常
     */
    boolean isRunning();
}
