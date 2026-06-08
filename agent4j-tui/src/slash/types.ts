/** 斜杠命令类型定义 */

export type SlashGroup = "setup" | "info" | "chat" | "session" | "advanced";

export interface SlashCommandSpec {
    /** 命令名称（不包含 /） */
    cmd: string;
    group: SlashGroup;
    summary: string;
    argsHint?: string;
    aliases?: string[];

}

export interface SlashResult {
    /** 显示给用户的提示信息 */
    info?: string;
    /** 退出应用 */
    exit?: boolean;
    /** 清空会话 */
    clear?: boolean;
    /** 重发文本 */
    resubmit?: string;
    /** 切换主题 */
    theme?: string;
    /** 未知命令 */
    unknown?: boolean;
}
