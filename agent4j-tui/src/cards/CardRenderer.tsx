/**
 * 卡片渲染器 —— 根据卡片类型分发到对应的渲染组件
 * 视觉风格：图标 + 标题 + 元信息，标签为中文
 * Reasoning / Tool 卡片支持点击展开/收起
 */

import {Box, Text} from "ink";
import React from "react";
import {Card, CardHeader} from "../primitives/index.js";
import {Markdown} from "../markdown/index.js";
import type {Card as CardType} from "../store/cards.js";
import {FG, ROLE, TONE} from "../theme/tokens.js";
import {TRACE_RE} from "./trace.js";

/* ── 工具函数 ─────────────────────────────────────────────── */

/** 去除 <think> 和 </think> 标签，保留内部内容 */
function stripThinkTags(text: string): string {
    return text.replace(/<\/?think>/gi, "").trim();
}

/** 截取前 N 行 */
function firstLines(text: string, n: number): string {
    const lines = text.split("\n");
    if (lines.length <= n) return text;
    return lines.slice(0, n).join("\n") + `\n… 共 ${lines.length} 行`;
}

/** 提取末尾的 trace 信息：`(model, Xtk, Xs)` → { cleanText, trace } */
function extractTraceInfo(text: string): { cleanText: string; trace: string | null } {
    const m = text.match(TRACE_RE);
    if (m) {
        return {cleanText: text.slice(0, m.index).trimEnd(), trace: m[1]!};
    }
    return {cleanText: text, trace: null};
}

/* ── 用户卡片 ─────────────────────────────────────────────── */

function UserCard({card}: { card: CardType & { kind: "user" } }): React.ReactElement {
    return (
        <Card>
            <CardHeader glyph={ROLE.user.glyph} tone={ROLE.user.color} title="用户"/>
            <Box flexDirection="row" gap={1} paddingLeft={2}>
                <Text color={FG.sub}>↳</Text>
                <Text color={FG.body}>{card.text}</Text>
            </Box>
        </Card>
    );
}

/* ── 思考中卡片 ───────────────────────────────────────────── */

function ReasoningCard({
                           card,
                           expanded,
                           onToggle,
                       }: {
    card: CardType & { kind: "reasoning" };
    expanded?: boolean;
    onToggle?: () => void;
}): React.ReactElement {
    const tone = card.done ? TONE.accent : TONE.info;
    const cleanText = card.text ? stripThinkTags(card.text) : "";
    const lineCount = cleanText ? cleanText.split("\n").length : 0;

    const meta: string[] = [];
    if (!expanded && lineCount > 2) meta.push(`─ ${lineCount} 行 ─`);
    if (card.done) meta.push("✓ 完成");

    // 折叠时只显示前 2 行
    const displayText = expanded ? cleanText : firstLines(cleanText, 2);

    return (
        <Card>
            <CardHeader
                glyph={expanded ? "▼" : "◆"}
                tone={tone}
                title="思考中"
                subtitle={!card.done ? "..." : undefined}
                meta={meta}
            />
            <Box paddingLeft={2} flexDirection="column">
                {displayText ? (
                    card.done ? (
                        <Markdown text={displayText} dimmed/>
                    ) : (
                        <Text color={FG.sub}>{displayText}</Text>
                    )
                ) : (
                    <Text color={FG.faint} italic>思考中...</Text>
                )}
            </Box>
        </Card>
    );
}

/* ── 助手卡片 ─────────────────────────────────────────────── */

function AssistantCard({card}: { card: CardType & { kind: "assistant" } }): React.ReactElement {
    const cleanText = card.text ? stripThinkTags(card.text) : "";
    const {cleanText: bodyText, trace} = extractTraceInfo(cleanText);

    const meta: string[] = [];
    if (trace) meta.push(trace);

    return (
        <Card>
            <CardHeader
                glyph={ROLE.assistant.glyph}
                tone={ROLE.assistant.color}
                title="助手"
                subtitle={card.streaming ? "生成中..." : undefined}
                meta={meta}
            />
            <Box paddingLeft={2}>
                {bodyText ? (
                    card.streaming ? (
                        <Text color={FG.body}>{bodyText}</Text>
                    ) : (
                        <Markdown text={bodyText}/>
                    )
                ) : (
                    <Text color={FG.faint} italic>生成中...</Text>
                )}
            </Box>
        </Card>
    );
}

/* ── 工具卡片 ─────────────────────────────────────────────── */

function ToolCard({
                      card,
                      expanded,
                      onToggle,
                  }: {
    card: CardType & { kind: "tool" };
    expanded?: boolean;
    onToggle?: () => void;
}): React.ReactElement {
    const elapsed = card.done ? `${card.elapsedMs}ms` : "…";

    return (
        <Card>
            <CardHeader
                glyph={expanded ? "▼" : "▣"}
                tone={ROLE.tool.color}
                title={`工具: ${card.name}`}
                meta={[elapsed]}
            />
            {expanded && card.output ? (
                <Box paddingLeft={2} flexDirection="column">
                    <Markdown text={card.output}/>
                </Box>
            ) : null}
        </Card>
    );
}

/* ── 系统卡片 ─────────────────────────────────────────────── */

const TONE_MAP: Record<string, string> = {
    info: FG.meta,
    ok: TONE.ok,
    warn: TONE.warn,
    err: TONE.err,
};

function SystemCard({card}: { card: CardType & { kind: "system" } }): React.ReactElement {
    return (
        <Card>
            <CardHeader glyph={ROLE.system.glyph} tone={ROLE.system.color} title="系统"/>
            <Box paddingLeft={2}>
                <Text color={TONE_MAP[card.tone] ?? FG.body}>{card.text}</Text>
            </Box>
        </Card>
    );
}

/* ── HITL 卡片 ────────────────────────────────────────────── */

function HitlCardView({card}: { card: CardType & { kind: "hitl" } }): React.ReactElement {
    if (card.resolved) {
        const decision = card.response === "approve" ? "已批准" : "已拒绝";
        return (
            <Card>
                <CardHeader
                    glyph={card.response === "approve" ? "✓" : "✗"}
                    tone={card.response === "approve" ? TONE.ok : TONE.warn}
                    title={`HITL: ${card.toolName}`}
                    meta={[decision]}
                />
            </Card>
        );
    }

    return (
        <Card>
            <CardHeader
                glyph="⏳"
                tone={TONE.warn}
                title={`等待确认: ${card.toolName}`}
                meta={["HITL"]}
            />
            <Box paddingLeft={2} flexDirection="column">
                {card.command ? (
                    <Box flexDirection="column" borderStyle="round" borderColor={TONE.warn}>
                        <Text color={FG.sub}>{card.command}</Text>
                    </Box>
                ) : null}
                <Box marginTop={1}>
                    <Text color={TONE.info}>输入 </Text>
                    <Text bold color={TONE.ok}>/approve</Text>
                    <Text color={TONE.info}> 批准或 </Text>
                    <Text bold color={TONE.err}>/deny</Text>
                    <Text color={TONE.info}> 拒绝</Text>
                </Box>
            </Box>
        </Card>
    );
}

/* ── 主分发器 ─────────────────────────────────────────────── */

export interface CardRendererProps {
    card: CardType;
    expanded?: boolean;
    onToggle?: () => void;
}

export function CardRenderer({card, expanded, onToggle}: CardRendererProps): React.ReactElement {
    switch (card.kind) {
        case "user":
            return <UserCard card={card}/>;
        case "reasoning":
            return <ReasoningCard card={card} expanded={expanded} onToggle={onToggle}/>;
        case "assistant":
            return <AssistantCard card={card}/>;
        case "tool":
            return <ToolCard card={card} expanded={expanded} onToggle={onToggle}/>;
        case "system":
            return <SystemCard card={card}/>;
        case "hitl":
            return <HitlCardView card={card}/>;
        default:
            return <Box><Text color={FG.faint}>未知卡片</Text></Box>;
    }
}
