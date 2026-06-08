/**
 * PromptInput —— 全宽圆角输入框
 * 支持多行编辑、光标管理、粘贴识别
 * 视觉风格：全宽圆角边框 + 多行编辑
 */

import {Box, Text, useStdout} from "ink";
import React, {useEffect, useRef, useState} from "react";
import {useKeystroke} from "./input/keystroke-context.js";
import {FG, TONE} from "./theme/tokens.js";

/** 粘贴阈值 —— 超过此长度的粘贴内容折叠为占位符 */
const PASTE_THRESHOLD = 200;

export interface PromptInputProps {
    value: string;
    onChange: (v: string) => void;
    onSubmit: (v: string) => void;
    disabled?: boolean;
    placeholder?: string;
    onHistoryPrev?: () => void;
    onHistoryNext?: () => void;
    onCursorChange?: (cursor: number) => void;
}

/** 检测是否为 CJK IME 提交的 Enter（50ms 内的非 ASCII 输入后触发的 Enter） */
const IME_GUARD_MS = 50;

function hasNonAscii(s: string): boolean {
    for (let i = 0; i < s.length; i++) {
        if (s.charCodeAt(i) > 0x7f) return true;
    }
    return false;
}

function PromptInputInner({
                              value,
                              onChange,
                              onSubmit,
                              disabled,
                              placeholder = "输入消息后按 Enter 发送...",
                              onHistoryPrev,
                              onHistoryNext,
                              onCursorChange,
                          }: PromptInputProps): React.ReactElement {
    const {stdout} = useStdout();
    const cols = stdout?.columns ?? 80;
    const visibleCells = Math.max(20, cols - 6); // 2 border + 2 padding + 2 prefix

    const [cursor, setCursor] = useState(value.length);
    const lastNonAsciiAtRef = useRef(0);

    useEffect(() => {
        onCursorChange?.(cursor);
    }, [cursor, onCursorChange]);

    // pendingChangeRef: 为 true 表示当前 value 变更来自本组件的 onChange
    const pendingChangeRef = useRef(false);

    // 同步外部值变化——仅当值由外部改变时才重置光标到末尾
    const localValueRef = useRef(value);
    const cursorRef = useRef(cursor);
    cursorRef.current = cursor;
    if (value !== localValueRef.current) {
        const fromInternal = pendingChangeRef.current;
        pendingChangeRef.current = false;
        localValueRef.current = value;
        if (!fromInternal) {
            cursorRef.current = value.length;
            setCursor(value.length);
        }
    }

    useKeystroke(
        (ev) => {
            if (disabled) return;

            const curVal = localValueRef.current;
            const curCursor = cursorRef.current;

            // Enter —— 提交（IME 保护：非 ASCII 后 50ms 内的 Enter 视为 IME 确认）
            if (ev.return) {
                const now = Date.now();
                if (now - lastNonAsciiAtRef.current < IME_GUARD_MS) {
                    pendingChangeRef.current = true;
                    onChange(curVal + "\n");
                    setCursor(curVal.length + 1);
                    return;
                }
                if (curVal.trim()) {
                    onSubmit(curVal);
                }
                return;
            }

            // Ctrl+C —— 退出
            if (ev.ctrl && ev.input === "c") {
                process.exit(0);
                return;
            }

            // Ctrl+U —— 清空行
            if (ev.ctrl && ev.input === "u") {
                pendingChangeRef.current = true;
                onChange("");
                setCursor(0);
                return;
            }

            // Ctrl+P —— 上一条历史
            if (ev.ctrl && ev.input === "p") {
                onHistoryPrev?.();
                return;
            }

            // Ctrl+N —— 下一条历史
            if (ev.ctrl && ev.input === "n") {
                onHistoryNext?.();
                return;
            }

            // Backspace
            if (ev.backspace) {
                if (curCursor <= 0) return;
                const next = curVal.slice(0, curCursor - 1) + curVal.slice(curCursor);
                pendingChangeRef.current = true;
                onChange(next);
                setCursor((c) => c - 1);
                return;
            }

            // Delete
            if (ev.delete) {
                if (curCursor >= curVal.length) return;
                const next = curVal.slice(0, curCursor) + curVal.slice(curCursor + 1);
                pendingChangeRef.current = true;
                onChange(next);
                return;
            }

            // 左右移动光标
            if (ev.leftArrow && curCursor > 0) {
                setCursor((c) => c - 1);
                return;
            }
            if (ev.rightArrow && curCursor < curVal.length) {
                setCursor((c) => c + 1);
                return;
            }

            // Home / End
            if (ev.home) {
                setCursor(0);
                return;
            }
            if (ev.end) {
                setCursor(curVal.length);
                return;
            }

            // 输入字符
            if (ev.input && !ev.ctrl && !ev.meta) {
                if (hasNonAscii(ev.input)) {
                    lastNonAsciiAtRef.current = Date.now();
                }
                const next = curVal.slice(0, curCursor) + ev.input + curVal.slice(curCursor);
                pendingChangeRef.current = true;
                onChange(next);
                setCursor((c) => c + ev.input.length);
                return;
            }
        },
        true,
    );

    // 显示逻辑：折叠过长行
    const lines = value.split("\n");
    const collapsed =
        lines.length > 20
            ? [...lines.slice(0, 19), `… ${lines.length - 19} 行已折叠 …`]
            : lines;

    return (
        <Box
            flexDirection="column"
            borderStyle="round"
            borderColor={disabled ? FG.faint : TONE.brand}
            paddingX={1}
        >
            {collapsed.length === 0 || (collapsed.length === 1 && collapsed[0] === "") ? (
                <Text color={FG.faint} italic>
                    {placeholder}
                </Text>
            ) : (
                collapsed.map((line, i) => (
                    <Box key={i} height={1}>
                        {i === 0 ? (
                            <Text bold color={TONE.brand}>❯ </Text>
                        ) : (
                            <Box width={2}/>
                        )}
                        <Text color={FG.body} wrap="truncate">
                            {clipToVisible(line, visibleCells)}
                        </Text>
                    </Box>
                ))
            )}
        </Box>
    );
}

/** Memoised PromptInput — avoids re-rendering when only card stream changes */
export const PromptInput = React.memo(PromptInputInner, (prev, next) => {
    if (prev.value !== next.value) return false;
    if (prev.onChange !== next.onChange) return false;
    if (prev.onSubmit !== next.onSubmit) return false;
    if (prev.disabled !== next.disabled) return false;
    if (prev.placeholder !== next.placeholder) return false;
    if (prev.onHistoryPrev !== next.onHistoryPrev) return false;
    if (prev.onHistoryNext !== next.onHistoryNext) return false;
    if (prev.onCursorChange !== next.onCursorChange) return false;
    return true;
}) as typeof PromptInputInner;

/** 截断超长行并在末端显示 ‹/› 指示符 */
function clipToVisible(text: string, maxCells: number): string {
    if (text.length <= maxCells) return text;
    return text.slice(0, maxCells - 1) + "›";
}
