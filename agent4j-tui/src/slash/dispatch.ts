/** 斜杠命令调度 —— 将 / 命令路由到对应的处理器 */

import {resolveSlashAlias} from "./commands.js";
import {handlers as basicHandlers} from "./handlers/basic.js";
import type {SlashContext} from "./handlers/types.js";
import type {SlashResult} from "./types.js";

const HANDLERS: Record<string, (args: string[], ctx: SlashContext) => SlashResult> = {
    ...basicHandlers,
};

/** 解析并执行斜杠命令 */
export function handleSlash(
    cmd: string,
    args: string[],
    ctx: SlashContext,
): SlashResult {
    const resolved = resolveSlashAlias(cmd);
    const handler = HANDLERS[resolved];
    if (handler) return handler(args, ctx);
    return {unknown: true, info: `未知命令 /${cmd}。输入 /help 查看可用命令。`};
}

/** 判断文本是否为斜杠命令 */
export function parseSlash(text: string): { cmd: string; args: string[] } | null {
    if (!text.startsWith("/")) return null;
    const parts = text.slice(1).trim().split(/\s+/);
    if (parts.length === 0 || !parts[0]) return null;
    return {cmd: parts[0]!, args: parts.slice(1)};
}
