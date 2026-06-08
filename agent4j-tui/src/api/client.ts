/**
 * agent4j HTTP REST 客户端
 * 基于 Node.js 原生 fetch，无外部依赖
 */

import type {
    AgentInfo,
    AgentStatus,
    ApiConfig,
    ApiResponse,
    CommandItem,
    GitDiffEntry,
    GitStatus,
    HealthStatus,
    MessageItem,
    ModelItem,
    SessionItem,
    ToolInfo,
    VersionInfo,
    WorkspaceInfo,
} from "./types.js";

// ---------------------------------------------------------------------------
// ApiError
// ---------------------------------------------------------------------------

export class ApiError extends Error {
    public readonly code: number | string;
    public readonly data: unknown;

    constructor(code: number | string, message: string, data?: unknown) {
        super(message);
        this.name = "ApiError";
        this.code = code;
        this.data = data;
    }

    get isTimeout(): boolean {
        return this.code === "TIMEOUT";
    }

    get isOffline(): boolean {
        return this.code === "OFFLINE";
    }

    get isUnauthorized(): boolean {
        return this.code === 401;
    }

    get isForbidden(): boolean {
        return this.code === 403;
    }

    get isNotFound(): boolean {
        return this.code === 404;
    }

    get isServerError(): boolean {
        return typeof this.code === "number" && this.code >= 500;
    }
}

// ---------------------------------------------------------------------------
// 内部工具
// ---------------------------------------------------------------------------

function generateRequestId(): string {
    return (
        "req_" +
        Math.random().toString(36).substring(2, 11) +
        "_" +
        Date.now().toString(36)
    );
}

function buildQueryString(
    params: Record<string, string | number | boolean | undefined | null>,
): string {
    const parts: string[] = [];
    for (const [key, val] of Object.entries(params)) {
        if (val !== undefined && val !== null && val !== "") {
            parts.push(
                `${encodeURIComponent(key)}=${encodeURIComponent(String(val))}`,
            );
        }
    }
    return parts.length > 0 ? "?" + parts.join("&") : "";
}

// ---------------------------------------------------------------------------
// ApiClient
// ---------------------------------------------------------------------------

export class ApiClient {
    private baseURL: string;
    private defaultTimeout: number;
    private token: string | null;

    constructor(config: ApiConfig) {
        this.baseURL = config.baseURL.replace(/\/+$/, "");
        this.defaultTimeout = config.timeout ?? 30_000;
        this.token = config.token ?? null;
    }

    /** 更新 baseURL（用户切换服务器地址时使用） */
    setBaseURL(url: string): void {
        this.baseURL = url.replace(/\/+$/, "");
    }

    /** 更新认证令牌 */
    setToken(token: string | null): void {
        this.token = token;
    }

    /** 获取当前 baseURL */
    getBaseURL(): string {
        return this.baseURL;
    }

    // -----------------------------------------------------------------------
    // 核心请求方法
    // -----------------------------------------------------------------------

    /**
     * 发送消息
     * POST /api/chat
     */
    sendMessage(
        text: string,
        workspaceHash?: string,
        sessionName?: string,
        images?: string[],
    ): Promise<ApiResponse<MessageItem>> {
        const body: Record<string, unknown> = {message: text};
        if (workspaceHash !== undefined) body.workspaceHash = workspaceHash;
        if (sessionName !== undefined) body.sessionName = sessionName;
        if (images !== undefined && images.length > 0) body.images = images;
        return this.post<MessageItem>("/chat", {body});
    }

    /**
     * 中断当前聊天
     * POST /api/chat/abort
     */
    abort(workspaceHash?: string, sessionName?: string): Promise<ApiResponse<void>> {
        const body: Record<string, unknown> = {};
        if (workspaceHash !== undefined) body.workspaceHash = workspaceHash;
        if (sessionName !== undefined) body.sessionName = sessionName;
        return this.post<void>("/chat/abort", {body});
    }

    /**
     * 列出所有会话
     * GET /api/sessions?workspaceHash=xxx
     */
    listSessions(workspaceHash?: string): Promise<ApiResponse<SessionItem[]>> {
        return this.get<SessionItem[]>("/sessions", {
            params: {workspaceHash},
        });
    }

    /**
     * 创建新会话
     * POST /api/sessions/new?workspaceHash=xxx&sessionName=xxx
     */
    createSession(
        workspaceHash?: string,
        sessionName?: string,
    ): Promise<ApiResponse<SessionItem>> {
        return this.post<SessionItem>("/sessions/new", {
            params: {workspaceHash, sessionName},
        });
    }

    /**
     * 切换会话
     * POST /api/sessions/{name}
     */
    switchSession(name: string): Promise<ApiResponse<SessionItem>> {
        return this.post<SessionItem>(`/sessions/${encodeURIComponent(name)}`);
    }

    // =====================================================================
    // 聊天 API
    // =====================================================================

    /**
     * 删除会话
     * DELETE /api/sessions/{name}
     */
    deleteSession(name: string): Promise<ApiResponse<void>> {
        return this.delete_<void>(`/sessions/${encodeURIComponent(name)}`);
    }

    /**
     * 获取会话详情
     * GET /api/sessions/{name}
     */
    getSessionDetails(name: string): Promise<ApiResponse<SessionItem>> {
        return this.get<SessionItem>(`/sessions/${encodeURIComponent(name)}`);
    }

    // =====================================================================
    // 会话 API
    // =====================================================================

    /**
     * 重命名会话
     * PUT /api/sessions/{name}
     */
    renameSession(
        name: string,
        newName: string,
    ): Promise<ApiResponse<SessionItem>> {
        return this.put<SessionItem>(`/sessions/${encodeURIComponent(name)}`, {
            body: {name: newName},
        });
    }

    /**
     * 清空所有会话
     * DELETE /api/sessions?workspaceHash=xxx
     */
    clearAllSessions(workspaceHash?: string): Promise<ApiResponse<void>> {
        return this.delete_<void>("/sessions", {
            params: {workspaceHash},
        });
    }

    /**
     * 获取 Agent 信息
     * GET /api/agent/info
     */
    getAgentInfo(): Promise<ApiResponse<AgentInfo>> {
        return this.get<AgentInfo>("/agent/info");
    }

    /**
     * 获取 Agent 状态
     * GET /api/agent/status
     */
    getAgentStatus(): Promise<ApiResponse<AgentStatus>> {
        return this.get<AgentStatus>("/agent/status");
    }

    /**
     * 获取 Agent 历史消息
     * GET /api/agent/history?workspaceHash=xxx&sessionName=xxx
     */
    getAgentHistory(
        workspaceHash?: string,
        sessionName?: string,
    ): Promise<ApiResponse<MessageItem[]>> {
        return this.get<MessageItem[]>("/agent/history", {
            params: {workspaceHash, sessionName},
        });
    }

    /**
     * 获取可用命令列表
     * GET /api/agent/commands
     */
    getCommands(): Promise<ApiResponse<CommandItem[]>> {
        return this.get<CommandItem[]>("/agent/commands");
    }

    /**
     * 获取可用技能列表
     * GET /api/agent/skills
     */
    getSkills(): Promise<ApiResponse<string[]>> {
        return this.get<string[]>("/agent/skills");
    }

    // =====================================================================
    // 代理 API
    // =====================================================================

    /**
     * 获取系统提示词
     * GET /api/agent/prompt?workspaceHash=xxx&sessionName=xxx
     */
    getSystemPrompt(
        params?: Record<string, string | undefined>,
    ): Promise<ApiResponse<string>> {
        return this.get<string>("/agent/prompt", {
            params: params as Record<string, string | number | boolean | undefined | null>,
        });
    }

    /**
     * 列出可用模型
     * GET /api/models
     */
    listModels(): Promise<ApiResponse<ModelItem[]>> {
        return this.get<ModelItem[]>("/models");
    }

    /**
     * 健康检查
     * GET /api/system/health
     */
    healthCheck(): Promise<ApiResponse<HealthStatus>> {
        return this.get<HealthStatus>("/system/health");
    }

    /**
     * 获取系统版本
     * GET /api/system/version
     */
    getVersion(): Promise<ApiResponse<VersionInfo>> {
        return this.get<VersionInfo>("/system/version");
    }

    /**
     * 获取系统配置
     * GET /api/config
     */
    getConfig(): Promise<ApiResponse<Record<string, unknown>>> {
        return this.get<Record<string, unknown>>("/config");
    }

    /**
     * 更新系统配置
     * PUT /api/config
     */
    updateConfig(
        config: Record<string, unknown>,
    ): Promise<ApiResponse<Record<string, unknown>>> {
        return this.put<Record<string, unknown>>("/config", {body: config});
    }

    // =====================================================================
    // 模型 API
    // =====================================================================

    /**
     * 获取当前工作区
     * GET /api/workspace
     */
    getWorkspace(): Promise<ApiResponse<WorkspaceInfo>> {
        return this.get<WorkspaceInfo>("/workspace");
    }

    // =====================================================================
    // 系统 API
    // =====================================================================

    /**
     * 切换工作区
     * POST /api/workspace
     */
    switchWorkspace(path: string): Promise<ApiResponse<WorkspaceInfo>> {
        return this.post<WorkspaceInfo>("/workspace", {body: {path}});
    }

    /**
     * 列出所有已注册工具
     * GET /api/tools
     */
    listTools(): Promise<ApiResponse<ToolInfo[]>> {
        return this.get<ToolInfo[]>("/tools");
    }

    /**
     * 执行工具
     * POST /api/tools/{name}/execute
     */
    executeTool(
        name: string,
        args: Record<string, unknown>,
    ): Promise<ApiResponse<unknown>> {
        return this.post<unknown>(`/tools/${encodeURIComponent(name)}/execute`, {
            body: {arguments: args},
        });
    }

    /**
     * 获取 Git 状态
     * GET /api/git/status?workspaceHash=xxx
     */
    getGitStatus(workspaceHash?: string): Promise<ApiResponse<GitStatus>> {
        return this.get<GitStatus>("/git/status", {
            params: {workspaceHash},
        });
    }

    // =====================================================================
    // 工作区 API
    // =====================================================================

    /**
     * 获取 Git Diff
     * GET /api/git/diff?workspaceHash=xxx
     */
    getGitDiff(workspaceHash?: string): Promise<ApiResponse<GitDiffEntry[]>> {
        return this.get<GitDiffEntry[]>("/git/diff", {
            params: {workspaceHash},
        });
    }

    /**
     * Git 提交
     * POST /api/git/commit
     */
    commit(
        workspaceHash: string,
        message: string,
        files?: string[],
    ): Promise<ApiResponse<void>> {
        const body: Record<string, unknown> = {message};
        if (files !== undefined && files.length > 0) body.files = files;
        return this.post<void>("/git/commit", {
            params: {workspaceHash},
            body,
        });
    }

    // =====================================================================
    // 工具 API
    // =====================================================================

    private async request<T>(
        method: string,
        path: string,
        options?: {
            body?: unknown;
            params?: Record<string, string | number | boolean | undefined | null>;
            timeout?: number;
            signal?: AbortSignal;
        },
    ): Promise<ApiResponse<T>> {
        const url = this.baseURL + "/api" + path + buildQueryString(options?.params ?? {});

        const headers: Record<string, string> = {
            "Content-Type": "application/json",
            "X-Request-ID": generateRequestId(),
            "X-Timestamp": String(Date.now()),
        };

        if (this.token) {
            headers["Authorization"] = `Bearer ${this.token}`;
        }

        const timeout = options?.timeout ?? this.defaultTimeout;
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeout);

        // 合并外部 signal
        const combinedSignal = options?.signal
            ? combineAbortSignals(controller.signal, options.signal)
            : controller.signal;

        try {
            const fetchInit: RequestInit = {
                method,
                headers,
                signal: combinedSignal,
            };

            if (options?.body !== undefined && options?.body !== null) {
                fetchInit.body = JSON.stringify(options.body);
            }

            const response = await fetch(url, fetchInit);

            clearTimeout(timeoutId);

            // 尝试解析 JSON
            let responseData: ApiResponse<T> | null = null;
            const contentType = response.headers.get("content-type") ?? "";
            if (contentType.includes("application/json")) {
                responseData = (await response.json()) as ApiResponse<T>;
            }

            if (!response.ok) {
                const code = response.status;
                const message =
                    responseData?.message ?? response.statusText ?? "请求失败";
                throw new ApiError(code, message, responseData?.data);
            }

            // 如果后端返回了标准格式就直接返回，否则包装
            if (responseData !== null) {
                return responseData;
            }

            // 非 JSON 响应（极少情况），包装为成功
            return {
                success: true,
                code: response.status,
                message: "OK",
                data: undefined as unknown as T,
            };
        } catch (err: unknown) {
            clearTimeout(timeoutId);

            if (err instanceof ApiError) {
                throw err;
            }

            // AbortError => 超时或手动取消
            if (err instanceof DOMException && err.name === "AbortError") {
                // 区分超时还是外部取消
                if (options?.signal?.aborted) {
                    throw new ApiError("CANCELED", "请求已取消");
                }
                throw new ApiError("TIMEOUT", `请求超时 (${timeout}ms)`);
            }

            // 网络错误
            if (err instanceof TypeError && err.message.includes("fetch")) {
                throw new ApiError("NETWORK", "网络连接失败，请检查服务器地址", err.message);
            }

            // 其他未知错误
            throw new ApiError(
                "UNKNOWN",
                err instanceof Error ? err.message : "未知错误",
            );
        }
    }

    // 快捷方法
    private get<T>(
        path: string,
        options?: {
            params?: Record<string, string | number | boolean | undefined | null>;
            timeout?: number;
            signal?: AbortSignal;
        },
    ): Promise<ApiResponse<T>> {
        return this.request<T>("GET", path, options);
    }

    // =====================================================================
    // Git API
    // =====================================================================

    private post<T>(
        path: string,
        options?: {
            body?: unknown;
            params?: Record<string, string | number | boolean | undefined | null>;
            timeout?: number;
            signal?: AbortSignal;
        },
    ): Promise<ApiResponse<T>> {
        return this.request<T>("POST", path, options);
    }

    private put<T>(
        path: string,
        options?: {
            body?: unknown;
            params?: Record<string, string | number | boolean | undefined | null>;
            timeout?: number;
            signal?: AbortSignal;
        },
    ): Promise<ApiResponse<T>> {
        return this.request<T>("PUT", path, options);
    }

    private delete_<T>(
        path: string,
        options?: {
            body?: unknown;
            params?: Record<string, string | number | boolean | undefined | null>;
            timeout?: number;
            signal?: AbortSignal;
        },
    ): Promise<ApiResponse<T>> {
        return this.request<T>("DELETE", path, options);
    }
}

// ---------------------------------------------------------------------------
// AbortSignal 合并工具（多个 AbortSignal 合成一个）
// ---------------------------------------------------------------------------

function combineAbortSignals(
    ...signals: AbortSignal[]
): AbortSignal {
    const controller = new AbortController();

    for (const signal of signals) {
        if (signal.aborted) {
            controller.abort(signal.reason);
            return controller.signal;
        }
        signal.addEventListener(
            "abort",
            () => {
                controller.abort(signal.reason);
            },
            {once: true},
        );
    }

    return controller.signal;
}
