package site.sorghum.agent4j.tool.solon.lsp;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LSP 客户端实现——通过 stdio 与 Language Server 进程通信。
 * <p>
 * 使用 LSP4J 的 {@link Launcher} 建立 JSON-RPC 连接，
 * 完成 LSP 协议的 initialize → initialized 握手，
 * 管理文件同步（didOpen/didClose/didChange），
 * 接收诊断信息（publishDiagnostics），
 * 并实现 {@link LspClient} 接口的所有 LSP 功能方法。
 * </p>
 *
 * <h3>生命周期</h3>
 * <pre>
 * new LspClientImpl(params)  → 创建实例
 *        ↓
 * start()                    → 启动 Language Server 进程 + 握手
 *        ↓
 * touchFile(path)            → 同步文件内容
 *        ↓
 * definition(...) / hover(...)  → 调用 LSP 功能
 *        ↓
 * shutdown()                 → 优雅关闭进程
 * </pre>
 *
 * @author Sorghum
 */
@Slf4j
public class LspClientImpl implements LspClient {

    // ---- 配置 ----
    @Getter
    private final LspServerParameters serverParams;

    // ---- 进程 ----
    private Process process;
    private Launcher<LanguageServer> launcher;
    private LanguageServer serverProxy;

    // ---- 文件版本追踪 ----
    private final Map<String, Integer> fileVersions = new ConcurrentHashMap<>();
    private final AtomicInteger versionCounter = new AtomicInteger(1);

    // ---- 状态 ----
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // ---- 最新诊断缓存 ----
    private final Map<String, List<Diagnostic>> diagnosticsCache = new ConcurrentHashMap<>();

    // ---- 默认超时 ----
    private static final long INIT_TIMEOUT_SECONDS = 30;
    private static final long REQUEST_TIMEOUT_SECONDS = 15;

    public LspClientImpl(LspServerParameters serverParams) {
        this.serverParams = serverParams;
    }

    // ======================================================================
    //  启动 & 握手
    // ======================================================================

    /**
     * 启动 Language Server 进程并完成 LSP 握手。
     *
     * @throws IOException          如果进程启动失败
     * @throws TimeoutException     如果握手超时
     * @throws ExecutionException   如果初始化请求返回错误
     * @throws InterruptedException 如果等待被中断
     */
    public void start() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        if (running.get()) {
            log.warn("[LSP:{}] Language Server 已运行，跳过重复启动", serverParams.getName());
            return;
        }

        String[] cmd = serverParams.getCommandArray();
        if (cmd.length == 0) {
            throw new IOException("Language Server 命令为空: " + serverParams.getName());
        }

        log.info("[LSP:{}] 启动 Language Server: {}", serverParams.getName(), String.join(" ", cmd));

        // 1. 创建进程
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false); // 保持 stderr 独立，避免干扰 JSON-RPC

        // 设置环境变量
        Map<String, String> env = pb.environment();
        Map<String, String> configEnv = serverParams.getEnv();
        if (!configEnv.isEmpty()) {
            env.putAll(configEnv);
        }

        process = pb.start();

        // 2. 启动 stderr 读取线程（调试用）
        Thread stderrReader = new Thread(() -> {
            try (InputStream errStream = process.getErrorStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = errStream.read(buf)) != -1) {
                    String msg = new String(buf, 0, n, StandardCharsets.UTF_8);
                    log.debug("[LSP:{}:stderr] {}", serverParams.getName(), msg.trim());
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.debug("[LSP:{}] stderr 读取结束: {}", serverParams.getName(), e.getMessage());
                }
            }
        }, "lsp-stderr-" + serverParams.getName());
        stderrReader.setDaemon(true);
        stderrReader.start();

        // 3. 创建 LSP4J Launcher
        launcher = Launcher.createLauncher(
                this,
                LanguageServer.class,
                process.getInputStream(),
                process.getOutputStream()
        );

        // 4. 开始监听
        launcher.startListening();
        serverProxy = launcher.getRemoteProxy();
        running.set(true);

        // 5. initialize 握手
        InitializeParams initParams = new InitializeParams();
        initParams.setProcessId((int) ProcessHandle.current().pid());

        // 设置客户端能力
        ClientCapabilities capabilities = new ClientCapabilities();
        initParams.setCapabilities(capabilities);

        // 传递 initializationOptions
        Map<String, Object> initOpts = serverParams.getInitializationOptions();
        if (!initOpts.isEmpty()) {
            initParams.setInitializationOptions(initOpts);
        }

        CompletableFuture<InitializeResult> initFuture = serverProxy.initialize(initParams);
        InitializeResult initResult = initFuture.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("[LSP:{}] 初始化完成，server capabilities: {}",
                serverParams.getName(),
                initResult.getCapabilities() != null ? "OK" : "NONE");

        // 6. initialized 通知
        serverProxy.initialized(new InitializedParams());
        initialized.set(true);

        log.info("[LSP:{}] Language Server 启动成功", serverParams.getName());
    }

    // ======================================================================
    //  文件同步
    // ======================================================================

    @Override
    public void touchFile(String filePath) {
        if (!isRunning()) {
            log.warn("[LSP:{}] Language Server 未运行，无法同步文件: {}", serverParams.getName(), filePath);
            return;
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                log.warn("[LSP:{}] 文件不存在或非普通文件: {}", serverParams.getName(), filePath);
                return;
            }

            String uri = path.toUri().toString();
            Integer existingVersion = fileVersions.get(uri);

            if (existingVersion == null) {
                // 新文件：didOpen
                String content = Files.readString(path, StandardCharsets.UTF_8);
                int version = versionCounter.getAndIncrement();

                TextDocumentItem item = new TextDocumentItem();
                item.setUri(uri);
                item.setLanguageId(detectLanguageId(filePath));
                item.setVersion(version);
                item.setText(content);

                DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(item);
                serverProxy.getTextDocumentService().didOpen(params);

                fileVersions.put(uri, version);
                log.debug("[LSP:{}] didOpen: {} (v{})", serverParams.getName(), filePath, version);
            } else {
                // 已有文件：didChange（全量替换）
                String content = Files.readString(path, StandardCharsets.UTF_8);
                int newVersion = versionCounter.getAndIncrement();

                VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier();
                docId.setUri(uri);
                docId.setVersion(newVersion);

                TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
                change.setText(content);

                DidChangeTextDocumentParams params = new DidChangeTextDocumentParams(docId,
                        Collections.singletonList(change));
                serverProxy.getTextDocumentService().didChange(params);

                fileVersions.put(uri, newVersion);
                log.debug("[LSP:{}] didChange: {} (v{})", serverParams.getName(), filePath, newVersion);
            }
        } catch (IOException e) {
            log.error("[LSP:{}] 文件同步失败: {} - {}", serverParams.getName(), filePath, e.getMessage());
        }
    }

    private void closeFile(String uri) {
        if (!isRunning() || uri == null) return;

        try {
            TextDocumentIdentifier docId = new TextDocumentIdentifier(uri);
            DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(docId);
            serverProxy.getTextDocumentService().didClose(params);
            fileVersions.remove(uri);
            log.debug("[LSP:{}] didClose: {}", serverParams.getName(), uri);
        } catch (Exception e) {
            log.debug("[LSP:{}] didClose 失败: {} - {}", serverParams.getName(), uri, e.getMessage());
        }
    }

    // ======================================================================
    //  LSP 功能方法实现
    // ======================================================================

    @Override
    public CompletableFuture<List<? extends Location>> definition(
            TextDocumentIdentifier document, Position position) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        DefinitionParams params = new DefinitionParams();
        params.setTextDocument(document);
        params.setPosition(position);
        return serverProxy.getTextDocumentService().definition(params)
                .thenApply(this::extractLocations);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        return serverProxy.getTextDocumentService().references(params)
                .thenApply(list -> list != null ? list : Collections.emptyList());
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        HoverParams hoverParams = new HoverParams();
        hoverParams.setTextDocument(params.getTextDocument());
        hoverParams.setPosition(params.getPosition());
        return serverProxy.getTextDocumentService().hover(hoverParams);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        return serverProxy.getTextDocumentService().documentSymbol(params);
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> workspaceSymbol(
            WorkspaceSymbolParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        return serverProxy.getWorkspaceService().symbol(params)
                .thenApply(result -> {
                    if (result == null) return Collections.emptyList();
                    if (result.isLeft()) {
                        List<? extends SymbolInformation> left = result.getLeft();
                        return left != null ? left : Collections.emptyList();
                    }
                    // Right side: List<? extends WorkspaceSymbol>
                    List<? extends WorkspaceSymbol> wsSymbols = result.getRight();
                    if (wsSymbols == null) return Collections.emptyList();
                    List<SymbolInformation> symbols = new ArrayList<>();
                    for (WorkspaceSymbol ws : wsSymbols) {
                        SymbolInformation si = new SymbolInformation();
                        si.setName(ws.getName());
                        si.setKind(ws.getKind());
                        // WorkspaceSymbol.location is Either<Location, WorkspaceSymbolLocation>
                        Either<Location, org.eclipse.lsp4j.WorkspaceSymbolLocation> locEither = ws.getLocation();
                        if (locEither != null) {
                            if (locEither.isLeft()) {
                                si.setLocation(locEither.getLeft());
                            } else {
                                // Convert WorkspaceSymbolLocation to Location
                                org.eclipse.lsp4j.WorkspaceSymbolLocation wsl = locEither.getRight();
                                Location loc = new Location();
                                loc.setUri(wsl.getUri());
                                si.setLocation(loc);
                            }
                        }
                        si.setContainerName(ws.getContainerName());
                        symbols.add(si);
                    }
                    return symbols;
                });
    }

    @Override
    public CompletableFuture<List<? extends Location>> implementation(
            TextDocumentPositionParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        ImplementationParams implParams = new ImplementationParams();
        implParams.setTextDocument(params.getTextDocument());
        implParams.setPosition(params.getPosition());
        return serverProxy.getTextDocumentService().implementation(implParams)
                .thenApply(this::extractLocations);
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(
            CallHierarchyPrepareParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        return serverProxy.getTextDocumentService().prepareCallHierarchy(params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyIncomingCall>> incomingCalls(
            CallHierarchyIncomingCallsParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        return serverProxy.getTextDocumentService().callHierarchyIncomingCalls(params);
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> outgoingCalls(
            CallHierarchyOutgoingCallsParams params) {
        if (!isRunning()) return failedFuture("Language Server 未运行");
        return serverProxy.getTextDocumentService().callHierarchyOutgoingCalls(params);
    }

    // ======================================================================
    //  语言
    // ======================================================================

    @Override
    public void shutdown() {
        if (!running.get()) return;

        log.info("[LSP:{}] 关闭 Language Server...", serverParams.getName());
        running.set(false);
        initialized.set(false);

        try {
            // 关闭已打开的文件
            for (String uri : fileVersions.keySet()) {
                closeFile(uri);
            }

            // 发送 shutdown 请求
            if (serverProxy != null) {
                try {
                    serverProxy.shutdown().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.debug("[LSP:{}] shutdown 请求异常: {}", serverParams.getName(), e.getMessage());
                }
                try {
                    serverProxy.exit();
                } catch (Exception e) {
                    log.debug("[LSP:{}] exit 通知异常: {}", serverParams.getName(), e.getMessage());
                }
            }
        } finally {
            // 强制终止进程
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
            fileVersions.clear();
            diagnosticsCache.clear();
            log.info("[LSP:{}] Language Server 已关闭", serverParams.getName());
        }
    }

    @Override
    public boolean isRunning() {
        return running.get()
                && process != null
                && process.isAlive()
                && initialized.get();
    }

    // ======================================================================
    //  LanguageClient 回调实现（接收 Language Server 通知）
    // ======================================================================

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
        String uri = params.getUri();
        List<Diagnostic> diagnostics = params.getDiagnostics();
        if (diagnostics == null) {
            diagnosticsCache.remove(uri);
        } else {
            diagnosticsCache.put(uri, new ArrayList<>(diagnostics));
        }
        log.debug("[LSP:{}] 收到 {} 条诊断: {}", serverParams.getName(),
                diagnostics != null ? diagnostics.size() : 0, uri);
    }

    @Override
    public void showMessage(MessageParams params) {
        String type = params.getType() == MessageType.Error ? "ERROR"
                : params.getType() == MessageType.Warning ? "WARN"
                : "INFO";
        log.info("[LSP:{}] Server消息 [{}]: {}", serverParams.getName(), type, params.getMessage());
    }

    @Override
    public CompletableFuture<org.eclipse.lsp4j.MessageActionItem> showMessageRequest(
            org.eclipse.lsp4j.ShowMessageRequestParams params) {
        log.info("[LSP:{}] Server请求: {}", serverParams.getName(), params.getMessage());
        // 默认实现：返回第一个可用操作
        List<org.eclipse.lsp4j.MessageActionItem> actions = params.getActions();
        if (actions != null && !actions.isEmpty()) {
            return CompletableFuture.completedFuture(actions.get(0));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams params) {
        String type = params.getType() == MessageType.Error ? "ERROR"
                : params.getType() == MessageType.Warning ? "WARN"
                : "INFO";
        log.debug("[LSP:{}] Server日志 [{}]: {}", serverParams.getName(), type, params.getMessage());
    }

    @Override
    public void telemetryEvent(Object object) {
        log.trace("[LSP:{}] telemetry: {}", serverParams.getName(), object);
    }

    // ======================================================================
    //  公开的辅助方法
    // ======================================================================

    /**
     * 获取指定文件的最新诊断信息。
     */
    public List<Diagnostic> getDiagnostics(String filePath) {
        if (filePath == null) return Collections.emptyList();
        String uri = Paths.get(filePath).toUri().toString();
        List<Diagnostic> diags = diagnosticsCache.get(uri);
        return diags != null ? Collections.unmodifiableList(diags) : Collections.emptyList();
    }

    /**
     * 获取所有已缓存诊断的 URI 列表。
     */
    public List<String> getDiagnosticUris() {
        return new ArrayList<>(diagnosticsCache.keySet());
    }

    // ======================================================================
    //  内部方法
    // ======================================================================

    /**
     * 安全提取 Location 列表（处理 {@code Either<Location, LocationLink>}）。
     */
    @SuppressWarnings("unchecked")
    private List<? extends Location> extractLocations(
            Either<List<? extends Location>, List<? extends org.eclipse.lsp4j.LocationLink>> result) {
        if (result == null) return Collections.emptyList();
        if (result.isLeft()) {
            List<? extends Location> locs = result.getLeft();
            return locs != null ? locs : Collections.emptyList();
        }
        // LocationLink → Location 转换
        if (result.isRight()) {
            List<? extends org.eclipse.lsp4j.LocationLink> links = result.getRight();
            if (links == null) return Collections.emptyList();
            List<Location> locations = new ArrayList<>();
            for (org.eclipse.lsp4j.LocationLink link : links) {
                Location loc = new Location();
                loc.setUri(link.getTargetUri());
                loc.setRange(link.getTargetSelectionRange() != null
                        ? link.getTargetSelectionRange()
                        : link.getTargetRange());
                locations.add(loc);
            }
            return locations;
        }
        return Collections.emptyList();
    }

    /**
     * 根据文件扩展名推测 Language ID。
     */
    private String detectLanguageId(String filePath) {
        if (filePath == null) return "plaintext";
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return "kotlin";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".jsx")) return "javascriptreact";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".tsx")) return "typescriptreact";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return "cpp";
        if (lower.endsWith(".c")) return "c";
        if (lower.endsWith(".h") || lower.endsWith(".hpp")) return "cpp";
        if (lower.endsWith(".cs")) return "csharp";
        if (lower.endsWith(".rb")) return "ruby";
        if (lower.endsWith(".php")) return "php";
        if (lower.endsWith(".swift")) return "swift";
        if (lower.endsWith(".scala")) return "scala";
        if (lower.endsWith(".lua")) return "lua";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "shellscript";
        if (lower.endsWith(".toml")) return "toml";
        return "plaintext";
    }

    /**
     * 创建立即失败的 CompletableFuture。
     */
    private <T> CompletableFuture<T> failedFuture(String message) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("[LSP:" + serverParams.getName() + "] " + message));
        return future;
    }
}
