/**
 * 斜杠命令处理器 —— basic 组
 * 处理：/help, /clear, /new, /retry, /exit, /theme, /compact
 */

import {SLASH_COMMANDS} from "../commands.js";
import type {ThemeName} from "../../theme/tokens.js";
import type {SlashHandler} from "./types.js";

/** /help —— 显示所有可用命令 */
const help: SlashHandler = (_args, _ctx) => {
    const lines = SLASH_COMMANDS.map(
        (s) => `  /${s.cmd}${s.argsHint ? " " + s.argsHint : ""}  ${s.summary}`,
    );
    return {info: "可用命令：\n" + lines.join("\n")};
};

/** /clear —— 清空所有消息 */
const clear: SlashHandler = (_args, ctx) => {
    ctx.clearAll();
    return {info: "已清空对话历史"};
};

/** /new —— 开始新对话 */
const newChat: SlashHandler = (_args, ctx) => {
    ctx.clearAll();
    return {info: "已开始新对话"};
};

/** /retry —— 重试最后一条消息 */
const retry: SlashHandler = (_args, ctx) => {
    const last = ctx.getLastUserMessage();
    if (last === null) return {info: "没有可重试的消息"};
    ctx.clearAll();
    return {resubmit: last};
};

/** /exit —— 退出应用 */
const exit: SlashHandler = (_args, _ctx) => {
    return {exit: true};
};

/** /theme —— 切换主题 */
const theme: SlashHandler = (args, ctx) => {
    const name = args[0];
    if (!name || !["default", "dark", "light", "tokyo-night", "github-dark", "github-light", "high-contrast"].includes(name)) {
        return {info: "用法：/theme <default|dark|light|tokyo-night|github-dark|github-light|high-contrast>"};
    }
    ctx.setTheme(name as ThemeName);
    return {info: `已切换主题为 ${name}`};
};

/** /compact —— 折叠历史 */
const compact: SlashHandler = (_args, _ctx) => {
    return {info: "已折叠历史消息（演示模式）"};
};

/** /approve —— 批准 HITL 请求 */
const approve: SlashHandler = (_args, ctx) => {
    if (!ctx.hasHitlPending?.()) {
        return {info: "当前没有等待确认的操作"};
    }
    ctx.approveHitl?.();
    return {info: "已批准操作"};
};

/** /deny —— 拒绝 HITL 请求 */
const deny: SlashHandler = (_args, ctx) => {
    if (!ctx.hasHitlPending?.()) {
        return {info: "当前没有等待确认的操作"};
    }
    ctx.denyHitl?.();
    return {info: "已拒绝操作"};
};

export const handlers: Record<string, SlashHandler> = {
    help,
    "?": help,
    clear,
    new: newChat,
    reset: newChat,
    retry,
    exit,
    quit: exit,
    theme,
    compact,
    approve,
    deny,
};
