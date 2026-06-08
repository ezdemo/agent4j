/** 斜杠命令处理器上下文 —— 处理器的操作接口 */

import type {SlashResult} from "../types.js";

export interface SlashContext {
    /** 清空所有对话卡片 */
    clearAll: () => void;
    /** 获取最后一条用户消息文本 */
    getLastUserMessage: () => string | null;
    /** 切换主题 */
    setTheme: (name: import("../../theme/tokens.js").ThemeName) => void;
    /** 提交消息到输入框 */
    resubmitText?: (text: string) => void;
    /** 是否有等待中的 HITL 请求 */
    hasHitlPending?: () => boolean;
    /** 批准当前 HITL */
    approveHitl?: () => void;
    /** 拒绝当前 HITL */
    denyHitl?: () => void;
}

export type SlashHandler = (args: string[], ctx: SlashContext) => SlashResult;
