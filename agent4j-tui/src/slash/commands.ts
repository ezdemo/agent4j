/** 斜杠命令注册表 —— 所有 / 命令的规格定义 */

import type {SlashCommandSpec} from "./types.js";

export const SLASH_GROUP_ORDER = ["setup", "info", "chat", "session", "advanced"] as const;

export const SLASH_GROUP_LABEL: Record<string, string> = {
    setup: "设置",
    info: "信息",
    chat: "对话",
    session: "会话",
    advanced: "高级",
};

export const SLASH_COMMANDS: readonly SlashCommandSpec[] = [
    // ── 对话 ──
    {cmd: "help", group: "chat", summary: "显示所有可用命令", aliases: ["?"]},
    {cmd: "new", group: "chat", summary: "开始新对话（清除上下文）", aliases: ["reset", "clear"]},
    {cmd: "retry", group: "chat", summary: "重试上一条消息"},
    {cmd: "interrupt", group: "chat", summary: "中断当前生成", aliases: ["stop"]},

    // ── 设置 ──
    {
        cmd: "theme",
        group: "setup",
        argsHint: "<default|dark|light|tokyo-night|github-dark|github-light|high-contrast>",
        summary: "切换主题"
    },
    {cmd: "preset", group: "setup", argsHint: "<auto|flash|pro>", summary: "模型预设"},
    {cmd: "model", group: "setup", argsHint: "<id>", summary: "切换模型"},

    // ── 会话 ──
    {cmd: "compact", group: "session", summary: "折叠历史消息节省空间"},
    {cmd: "session", group: "session", argsHint: "<sessionId>", summary: "切换会话 (空格后 ↑↓ 选择)"},
    {cmd: "approve", group: "chat", summary: "批准当前等待确认的操作"},
    {cmd: "deny", group: "chat", summary: "拒绝当前等待确认的操作"},

    // ── 信息 ──
    {cmd: "context", group: "info", summary: "显示上下文使用统计"},
    {cmd: "cost", group: "info", summary: "显示会话费用"},

    // ── 高级 ──
    {cmd: "exit", group: "advanced", summary: "退出应用", aliases: ["quit"]},
];

/** 解析命令别名 */
export function resolveSlashAlias(cmd: string): string {
    for (const spec of SLASH_COMMANDS) {
        if (spec.cmd === cmd) return cmd;
        if (spec.aliases?.includes(cmd)) return spec.cmd;
    }
    return cmd;
}

/** 按前缀筛选命令 */
export function suggestSlashCommands(prefix: string): SlashCommandSpec[] {
    const lower = prefix.toLowerCase();
    return SLASH_COMMANDS.filter(
        (s) => s.cmd.startsWith(lower) || s.aliases?.some((a) => a.startsWith(lower)),
    ).slice(0, 24);
}
