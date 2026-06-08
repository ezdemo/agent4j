import type {ApiConfig, SseEvent} from "./types.js";

export type {SseEvent};

// ── 连接状态 ──────────────────────────────────────────────

export type ConnectionState = "idle" | "connecting" | "connected" | "disconnected";

// ── 流参数 ────────────────────────────────────────────────

export interface StreamParams {
    /** 用户消息 */
    message: string;
    /** 工作区哈希（可选） */
    workspaceHash?: string;
    /** 会话名称（可选） */
    sessionName?: string;
    /** 图片（base64 Data URI 列表，可选） */
    images?: string[];
}

// ── 事件处理器 ────────────────────────────────────────────

export type SseEventHandler = (event: SseEvent) => void;

// ── 客户端选项 ────────────────────────────────────────────

export interface SseClientOptions {
    /** 连接状态变化回调 */
    onStateChange?: (state: ConnectionState) => void;
    /** 全局错误回调 */
    onError?: (error: Error) => void;
}

// ── SseClient ─────────────────────────────────────────────

/**
 * SSE 流式客户端。
 *
 * 通过 POST /api/chat/stream 发起流式聊天请求，
 * 使用 fetch + ReadableStream 读取标准 SSE 协议数据，
 * 按会话 ID 分发给已注册的事件处理器。
 */
export class SseClient {
    /** 默认 sessionId（当未指定 sessionName 时使用） */
    private static readonly DEFAULT_SESSION = "__default__";
    private baseUrl: string;
    private config: ApiConfig;
    private options: SseClientOptions;
    /** 当前连接的 AbortController */
    private abortController: AbortController | null = null;
    /** 按 sessionId 注册的事件处理器 */
    private handlers = new Map<string, SseEventHandler[]>();

    constructor(baseUrl: string, config: ApiConfig = {baseURL: baseUrl}, options: SseClientOptions = {}) {
        this.baseUrl = baseUrl.replace(/\/+$/, "");
        this.config = config;
        this.options = options;
    }

    // ── 构造 ──────────────────────────────────────────────

    /** 当前连接状态 */
    private _state: ConnectionState = "idle";

    // ── 状态管理 ──────────────────────────────────────────

    get state(): ConnectionState {
        return this._state;
    }

    /**
     * 注册事件处理器。
     * @param sessionId  会话标识（未指定则接收所有流的事件）
     * @param handler    事件回调
     * @returns          取消订阅函数
     */
    subscribe(sessionId: string, handler: SseEventHandler): () => void {
        const id = sessionId || SseClient.DEFAULT_SESSION;
        const list = this.handlers.get(id) || [];
        list.push(handler);
        this.handlers.set(id, list);

        return () => {
            const arr = this.handlers.get(id);
            if (arr) {
                const idx = arr.indexOf(handler);
                if (idx !== -1) arr.splice(idx, 1);
                if (arr.length === 0) this.handlers.delete(id);
            }
        };
    }

    // ── 事件订阅 ──────────────────────────────────────────

    /**
     * 建立连接（由 startStream 自动触发）。
     * 当前实现沿用 POST 流式请求模式，每次 startStream 都会创建新请求。
     */
    connect(): void {
        // SSE over POST 没有持久连接的概念，
        // connect() 在此作为状态标记保留，实际连接由 startStream 触发。
        this.setState("connecting");
    }

    /**
     * 发起 POST /api/chat/stream 请求，开始读取 SSE 流。
     *
     * @param params  请求参数
     * @param sessionId  可选，指定事件分发目标 session
     */
    async startStream(params: StreamParams, sessionId?: string): Promise<void> {
        // 中止上一次流
        this.abort();

        const controller = new AbortController();
        this.abortController = controller;
        this.setState("connecting");

        const sid = sessionId || params.sessionName || SseClient.DEFAULT_SESSION;

        try {
            // 构造 URL
            const url = this.baseUrl
                ? `${this.baseUrl}/api/chat/stream`
                : "/api/chat/stream";

            // 构造请求头
            const headers: Record<string, string> = {
                "Content-Type": "application/json",
                ...this.config.headers,
            };

            // 构造请求体
            const body: Record<string, unknown> = {message: params.message};
            if (params.workspaceHash) body.workspaceHash = params.workspaceHash;
            if (params.sessionName) body.sessionName = params.sessionName;
            if (params.images && params.images.length > 0) body.images = params.images;

            const res = await fetch(url, {
                method: "POST",
                headers,
                body: JSON.stringify(body),
                signal: controller.signal,
            });

            if (!res.ok) {
                const text = await res.text();
                const err = new Error(`HTTP ${res.status}: ${text}`);
                this.options.onError?.(err);
                this.dispatch(sid, {type: "error", message: `HTTP ${res.status}`, content: text});
                this.setState("disconnected");
                return;
            }

            this.setState("connected");

            const reader = res.body!.getReader();
            const decoder = new TextDecoder();
            let buffer = "";
            let currentEventType: string | null = null;
            let streamEnded = false;

            while (!streamEnded) {
                const {done, value} = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, {stream: true});
                const lines = buffer.split("\n");
                // 保留最后一个可能不完整的行
                buffer = lines.pop() || "";

                for (const rawLine of lines) {
                    const line = rawLine.trim();

                    // 空行 → 重置当前事件类型
                    if (line === "") {
                        currentEventType = null;
                        continue;
                    }

                    // 注释行（SSE 规范中以 : 开头）
                    if (line.startsWith(":")) continue;

                    // event: xxx
                    if (line.startsWith("event:")) {
                        currentEventType = line.slice(6).trim();
                        continue;
                    }

                    // data: xxx
                    if (line.startsWith("data:")) {
                        const payload = line.slice(5).trim();

                        // [DONE] → 流结束
                        if (payload === "[DONE]") {
                            streamEnded = true;
                            this.dispatch(sid, {type: "done"});
                            break;
                        }

                        // 解析 payload
                        const event = this.parsePayload(currentEventType, payload);
                        if (event) {
                            this.dispatch(sid, event);
                        }

                        currentEventType = null;
                        continue;
                    }

                    // 其他字段（SSE 规范允许扩展），忽略
                }
            }

            // 正常结束（未收到 [DONE] 但流结束）
            if (!streamEnded) {
                this.dispatch(sid, {type: "done"});
            }
        } catch (err: unknown) {
            // AbortError 是由 abort() 主动触发，不视为错误
            if (err instanceof DOMException && err.name === "AbortError") {
                this.dispatch(sid, {type: "done", aborted: true});
                return;
            }

            const error = err instanceof Error ? err : new Error(String(err));
            this.options.onError?.(error);
            this.dispatch(sid, {type: "error", message: error.message});
        } finally {
            if (this.abortController === controller) {
                this.abortController = null;
            }
            this.setState("disconnected");
        }
    }

    // ── 连接管理 ──────────────────────────────────────────

    /**
     * 中断当前流（如有）。
     */
    abort(): void {
        if (this.abortController) {
            this.abortController.abort();
            this.abortController = null;
        }
        this.setState("disconnected");
    }

    /**
     * 释放所有资源。
     */
    dispose(): void {
        this.abort();
        this.handlers.clear();
    }

    private setState(state: ConnectionState) {
        this._state = state;
        this.options.onStateChange?.(state);
    }

    // ── 中断 ──────────────────────────────────────────────

    /**
     * 向指定 session 的所有处理器分发事件。
     */
    private dispatch(sessionId: string | undefined, event: SseEvent) {
        // 优先向指定 session 分发
        if (sessionId) {
            const list = this.handlers.get(sessionId);
            if (list) {
                for (const h of list) h(event);
                return;
            }
        }
        // 回退到默认处理器
        const fallback = this.handlers.get(SseClient.DEFAULT_SESSION);
        if (fallback) {
            for (const h of fallback) h(event);
        }
    }

    // ── 清理 ──────────────────────────────────────────────

    /**
     * 解析 SSE data 行的负载。
     *
     * 规则（参考 agent4j-front）：
     * 1. 尝试 JSON.parse
     *    - 解析结果为字符串 → { type, content: parsed }
     *    - 解析结果为对象 → { type, ...parsed }
     *    - 解析失败 → { type, content: payload }
     */
    private parsePayload(
        eventType: string | null,
        payload: string,
    ): SseEvent | null {
        if (eventType === null) return null;

        // 尝试 JSON 解析
        try {
            const parsed = JSON.parse(payload);
            if (typeof parsed === "string") {
                return {type: eventType, content: parsed};
            }
            if (parsed !== null && typeof parsed === "object") {
                return {type: eventType, ...(parsed as Record<string, unknown>)};
            }
            // number / boolean / 其他原始类型
            return {type: eventType, content: String(parsed)};
        } catch {
            // 非 JSON 格式，原样返回
            return {type: eventType, content: payload};
        }
    }
}
