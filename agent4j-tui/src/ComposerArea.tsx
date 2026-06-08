/**
 * ComposerArea —— 底部输入区
 * 集成斜杠命令提示 + 全宽输入框 + 状态栏
 */

import {Box, Text} from "ink";
import React from "react";
import {SlashSuggestions} from "./SlashSuggestions.js";
import {PromptInput} from "./PromptInput.js";
import {useAgentState} from "./store/provider.js";
import {FG, TONE} from "./theme/tokens.js";
import type {SlashCommandSpec} from "./slash/types.js";

export interface PickerItem {
    label: string;
    selected: boolean;
}

export interface StatusPart {
    text: string;
    color?: string;
}

export interface ComposerAreaProps {
    input: string;
    setInput: (v: string) => void;
    onSubmit: (text: string) => void;
    onHistoryPrev: () => void;
    onHistoryNext: () => void;
    /** 斜杠命令匹配列表（由父组件计算并控制键盘导航） */
    slashMatches?: readonly SlashCommandSpec[] | null;
    slashSelected?: number;
    /** 交互选择器 */
    pickerTitle?: string;
    pickerItems?: readonly PickerItem[];
    /** 状态栏右侧信息（按部分着色） */
    statusRight?: readonly StatusPart[];
}

function ComposerAreaInner({
                               input,
                               setInput,
                               onSubmit,
                               onHistoryPrev,
                               onHistoryNext,
                               slashMatches,
                               slashSelected,
                               pickerTitle,
                               pickerItems,
                               statusRight,
                           }: ComposerAreaProps): React.ReactElement {
    const turnInProgress = useAgentState((s: { turnInProgress: boolean }) => s.turnInProgress);
    const cardCount = useAgentState((s: { cards: unknown[] }) => s.cards.length);

    return (
        <Box flexDirection="column" flexShrink={0}>
            {/* 斜杠命令提示 */}
            {slashMatches && input.startsWith("/") && !input.includes(" ") ? (
                <SlashSuggestions matches={slashMatches} selectedIndex={slashSelected ?? 0}/>
            ) : null}

            {/* 交互选择器 */}
            {pickerItems && pickerItems.length > 0 ? (
                <Box flexDirection="column" paddingX={1} marginTop={1} flexShrink={0}>
                    {pickerTitle ? (
                        <Text color={FG.meta}>{pickerTitle}</Text>
                    ) : null}
                    {pickerItems.map((item, i) => (
                        <Box key={i} flexDirection="row">
                            <Box width={2}>
                                {item.selected ? <Text color={TONE.accent}>▸</Text> : null}
                            </Box>
                            <Text color={item.selected ? TONE.accent : FG.body}>
                                {item.label}
                            </Text>
                        </Box>
                    ))}
                </Box>
            ) : null}

            {/* 输入框 */}
            <PromptInput
                value={input}
                onChange={setInput}
                onSubmit={onSubmit}
                disabled={turnInProgress}
                placeholder="输入 /help 查看命令，或直接输入消息..."
                onHistoryPrev={onHistoryPrev}
                onHistoryNext={onHistoryNext}
            />

            {/* 状态栏 */}
            <Box height={1} marginTop={1}>
                <Text color={FG.meta}>
                    {cardCount > 0 ? `${cardCount} 条消息` : "新会话"}
                    {turnInProgress ? ` · ${TONE.warn}处理中` : ""}
                    <Text color={FG.faint}> · / 命令 · ↑↓ 历史 · Esc 清空</Text>
                </Text>
                {statusRight && statusRight.length > 0 ? (
                    <>
                        <Text> </Text>
                        {statusRight.map((p, i) => (
                            <Text key={i}
                                  color={p.color ?? FG.faint}>{p.text}{i < statusRight.length - 1 ? "  " : ""}</Text>
                        ))}
                    </>
                ) : null}
            </Box>
        </Box>
    );
}

/** Memoised ComposerArea — avoids re-rendering when only card stream changes */
export const ComposerArea = React.memo(ComposerAreaInner, (prev, next) => {
    if (prev.input !== next.input) return false;
    if (prev.setInput !== next.setInput) return false;
    if (prev.onSubmit !== next.onSubmit) return false;
    if (prev.onHistoryPrev !== next.onHistoryPrev) return false;
    if (prev.onHistoryNext !== next.onHistoryNext) return false;
    if (prev.slashMatches !== next.slashMatches) return false;
    if (prev.slashSelected !== next.slashSelected) return false;
    if (prev.pickerTitle !== next.pickerTitle) return false;
    if (prev.pickerItems !== next.pickerItems) return false;
    if (prev.statusRight !== next.statusRight) return false;
    return true;
}) as typeof ComposerAreaInner;
