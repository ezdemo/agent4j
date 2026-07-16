export type CardId = string;

export interface CardBase {
    readonly id: CardId;
    readonly ts: number;
}

export interface UserCard extends CardBase {
    readonly kind: "user";
    text: string;
}

export interface AssistantCard extends CardBase {
    readonly kind: "assistant";
    text: string;
    done: boolean;
    streaming: boolean;
    aborted?: boolean;
}

export interface ToolCard extends CardBase {
    readonly kind: "tool";
    name: string;
    args: unknown;
    output: string;
    done: boolean;
    elapsedMs: number;
}

export interface ReasoningCard extends CardBase {
    readonly kind: "reasoning";
    text: string;
    done: boolean;
    aborted?: boolean;
}

export interface SystemCard extends CardBase {
    readonly kind: "system";
    text: string;
    tone: "info" | "ok" | "warn" | "err";
}

/** Human-in-the-loop 交互卡片 —— 等待用户确认/拒绝工具调用 */
export interface HitlCard extends CardBase {
    readonly kind: "hitl";
    toolName: string;
    /** bash 命令文本（仅 bash 工具） */
    command?: string;
    /** 是否已处理 */
    resolved: boolean;
    /** 用户响应：approve | deny */
    response?: "approve" | "deny";
}

export type Card = UserCard | AssistantCard | ToolCard | ReasoningCard | SystemCard | HitlCard;
