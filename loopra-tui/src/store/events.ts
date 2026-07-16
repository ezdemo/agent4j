export type AgentEvent =
    | { type: "user.submit"; text: string }
    | { type: "assistant.start" }
    | { type: "assistant.chunk"; text: string }
    | { type: "assistant.end"; aborted?: boolean }
    | { type: "tool.start"; name: string; args: unknown }
    | { type: "tool.end"; output: string; elapsedMs: number }
    | { type: "reasoning.start" }
    | { type: "reasoning.chunk"; text: string }
    | { type: "reasoning.end"; aborted?: boolean }
    | { type: "system.message"; text: string; tone: "info" | "ok" | "warn" | "err" }
    | { type: "clear" }
    // ── Web / HITL 扩展 ──
    | { type: "hitl.request"; toolName: string; command?: string }
    | { type: "hitl.resolve"; response: "approve" | "deny" }
    // ── 会话恢复 ──
    | { type: "session.restore"; cards: import("./cards.js").Card[] };
