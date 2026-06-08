/**
 * agent4j API 类型定义
 */

// ── API 基础类型 ───────────────────────────────────────────

export interface ApiConfig {
    /** API 基础地址，默认 http://localhost:8097 */
    baseURL: string;
    /** 自定义请求头 */
    headers?: Record<string, string>;
    /** 请求超时时间（毫秒） */
    timeout?: number;
    /** 认证令牌 */
    token?: string;
}

// ── 通用响应 ──────────────────────────────────────────────

export interface ApiResponse<T = unknown> {
    success: boolean;
    code: number;
    message: string;
    data: T;
}

// ── Agent ─────────────────────────────────────────────────

export interface AgentInfo {
    name: string;
    version: string;
    description: string;
    capabilities: string[];
    workspace?: string;
    appTitle?: string;
    appVersion?: string;
    workname?: string;
}

export interface AgentStatus {
    status: "idle" | "busy" | "error";
    turnInProgress: boolean;
    sessionName: string | null;
    workspaceHash: string | null;
}

// ── 会话 ──────────────────────────────────────────────────

export interface SessionItem {
    name: string;
    displayName?: string;
    workspaceHash: string;
    messageCount: number;
    createdAt: string;
    updatedAt: string;
}

// ── 模型 ──────────────────────────────────────────────────

export interface ModelItem {
    id: string;
    name: string;
    provider: string;
    description: string;
    capabilities: string[];
    maxTokens: number;
}

// ── 命令 ──────────────────────────────────────────────────

export interface CommandItem {
    name: string;
    description: string;
    usage?: string;
    category?: string;
}

// ── 消息 ──────────────────────────────────────────────────

export interface MessageItem {
    role: "user" | "assistant" | "system" | "tool";
    content: string;
    timestamp?: string;
    toolName?: string;
    toolArgs?: unknown;
    toolOutput?: string;
    sessionName?: string;
    id?: string;
}

// ── Git ───────────────────────────────────────────────────

export interface GitStatus {
    branch: string;
    changes: number;
    staged: number;
    unstaged: number;
    hasRemote: boolean;
}

export interface GitDiffEntry {
    path: string;
    status: "modified" | "added" | "deleted" | "renamed";
    oldPath?: string;
}

// ── 工作区 ────────────────────────────────────────────────

export interface WorkspaceInfo {
    path: string;
    hash: string;
    name: string;
}

// ── 工具 ──────────────────────────────────────────────────

export interface ToolInfo {
    name: string;
    description: string;
    parameters: Record<string, unknown>;
}

// ── 系统 ──────────────────────────────────────────────────

export interface HealthStatus {
    status: "UP" | "DOWN" | "DEGRADED";
    uptime: number;
    version: string;
}

export interface VersionInfo {
    version: string;
    buildTime: string;
    commit: string;
}

// ── SSE 流式事件 ─────────────────────────────────────────

/**
 * SSE 事件。
 *
 * 来自后端的原始事件经解析后统一为 SseEvent 结构。
 * - event: reason   → { type:"reason",   text:"思考中..." }
 * - event: text     → { type:"text",     content:"Hello" }
 * - event: action   → { type:"action",   toolName:"bash", command:"ls", args:{} }
 * - event: error    → { type:"error",    message:"出错" }
 * - event: done     → 流结束，不会构造此事件
 */
export interface SseEvent {
    type: string;
    content?: string;
    text?: string;
    toolName?: string;
    name?: string;
    command?: string;
    message?: string;
    output?: string;
    elapsedMs?: number;
    aborted?: boolean;
    args?: Record<string, unknown>;

    [key: string]: unknown;
}
