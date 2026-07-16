/**
 * Markdown → Ink 渲染组件
 *
 * 解析 GFM（marked），输出 Ink React 元素。
 * 支持：标题、段落、列表、代码块、引用、表格、行内格式、链接。
 */

import {Box, Text, Transform, useStdout} from "ink";
import {marked, type Token, type Tokens} from "marked";
import React from "react";
import stringWidthLib from "string-width";
import {wrapToCells} from "../frame/width.js";
import {decodeHtmlEntities} from "./html-entities.js";
import {FG, SURFACE, TONE} from "../theme/tokens.js";

// ── 宽度上下文 ──────────────────────────────────────────────

/** Left margin consumed by card paddingLeft + safety. */
const BODY_LEFT_CELLS = 5;

interface MarkdownCtxValue {
    availWidth: number;
    dimmed: boolean;
}

const MarkdownCtx = React.createContext<MarkdownCtxValue>({availWidth: 80, dimmed: false});

function useWidth(): number {
    return React.useContext(MarkdownCtx).availWidth;
}

function useDimmed(): boolean {
    return React.useContext(MarkdownCtx).dimmed;
}

// ── 主入口 ──────────────────────────────────────────────────

marked.setOptions({gfm: true, breaks: false});

export interface MarkdownProps {
    text: string;
    width?: number;
    /** 弱化渲染：正文用 sub 色，用于思考/推理内容 */
    dimmed?: boolean;
}

function MarkdownInner({text, width, dimmed = false}: MarkdownProps): React.ReactElement {
    const tokens = React.useMemo(() => marked.lexer(text), [text]);
    const columns = (useStdout()?.stdout?.columns ?? process.stdout.columns ?? 80) - BODY_LEFT_CELLS;
    const ctxWidth = width !== undefined ? Math.max(1, width) : columns;
    const ctx: MarkdownCtxValue = {availWidth: ctxWidth, dimmed};

    return (
        <MarkdownCtx.Provider value={ctx}>
            <Box flexDirection="column">
                {tokens.map((token, i) => (
                    <BlockToken key={`${i}-${token.type}`} token={token}/>
                ))}
            </Box>
        </MarkdownCtx.Provider>
    );
}

/** Memoised: only re-render when text or dimmed actually changes */
export const Markdown = React.memo(MarkdownInner, (prev, next) => {
    return prev.text === next.text && prev.dimmed === next.dimmed && prev.width === next.width;
});

// ── 块级元素 ────────────────────────────────────────────────

function BlockToken({token}: { token: Token }): React.ReactElement | null {
    switch (token.type) {
        case "heading":
            return <Heading token={token as Tokens.Heading}/>;
        case "paragraph":
            return <Paragraph token={token as Tokens.Paragraph}/>;
        case "list":
            return <List token={token as Tokens.List} depth={0}/>;
        case "code":
            return <CodeBlock token={token as Tokens.Code}/>;
        case "blockquote":
            return <Blockquote token={token as Tokens.Blockquote}/>;
        case "hr":
            return <HorizontalRule/>;
        case "table":
            return <Table token={token as Tokens.Table}/>;
        case "html":
            return <Text color={FG.body}>{(token as Tokens.HTML).text}</Text>;
        case "space":
            return null;
        default:
            return <Text color={FG.body}>{(token as { raw?: string }).raw ?? ""}</Text>;
    }
}

function Heading({token}: { token: Tokens.Heading }): React.ReactElement {
    return (
        <Box>
            <Text bold color={FG.strong} backgroundColor={SURFACE.bgCode}>
                {` ${plainText(token.tokens)} `}
            </Text>
        </Box>
    );
}

function Paragraph({token}: { token: Tokens.Paragraph }): React.ReactElement {
    const dimmed = useDimmed();
    return (
        <Text color={dimmed ? FG.sub : FG.body}>
            <Inline tokens={token.tokens ?? []}/>
        </Text>
    );
}

// ── 列表 ───────────────────────────────────────────────────

function List({token, depth}: { token: Tokens.List; depth: number }): React.ReactElement {
    return (
        <Box flexDirection="column">
            {token.items.map((item, i) => (
                <ListItem
                    key={`${i}-${item.text.slice(0, 24)}`}
                    item={item}
                    ordered={token.ordered}
                    index={i + (Number(token.start) || 1)}
                    depth={depth}
                />
            ))}
        </Box>
    );
}

function ListItem({
                      item,
                      ordered,
                      index,
                      depth,
                  }: {
    item: Tokens.ListItem;
    ordered: boolean;
    index: number;
    depth: number;
}): React.ReactElement {
    const marker = item.task ? (item.checked ? "✓" : "○") : ordered ? `${index}.` : "·";
    const markerColor = item.task ? (item.checked ? TONE.ok : FG.faint) : FG.meta;
    const dim = item.task && item.checked === true;
    const dimmed = useDimmed();
    const indent = " ".repeat(depth + 1);
    return (
        <Box>
            <Text color={markerColor}>{`${indent}${marker} `}</Text>
            <Box flexDirection="column">
                {item.tokens.map((tok, i) => {
                    if (tok.type === "text") {
                        const inner = (tok as Tokens.Text).tokens;
                        const textColor = dim ? FG.faint : dimmed ? FG.sub : FG.body;
                        return (
                            <Text key={`t-${i}`} color={textColor} strikethrough={dim}>
                                {inner ? <Inline tokens={inner}/> : (tok as Tokens.Text).text}
                            </Text>
                        );
                    }
                    if (tok.type === "list") {
                        return <List key={`l-${i}`} token={tok as Tokens.List} depth={depth + 1}/>;
                    }
                    return <BlockToken key={`b-${i}-${tok.type}`} token={tok}/>;
                })}
            </Box>
        </Box>
    );
}

// ── 代码块 ─────────────────────────────────────────────────

function CodeBlock({token}: { token: Tokens.Code }): React.ReactElement {
    const lang = token.lang?.split(/\s+/)[0] ?? "";
    const text = decodeHtmlEntities(token.text);
    const lines = text.split("\n");
    return (
        <Box flexDirection="column">
            {lang ? (
                <Box>
                    <Text color={FG.meta}>{` ${lang}`}</Text>
                </Box>
            ) : null}
            <Box flexDirection="column">
                {lines.map((line, i) => (
                    <Text key={`code-${i}`} backgroundColor={SURFACE.bgCode}>
                        {` ${line} `}
                    </Text>
                ))}
            </Box>
        </Box>
    );
}

// ── 引用 ──────────────────────────────────────────────────

function Blockquote({token}: { token: Tokens.Blockquote }): React.ReactElement {
    return (
        <Box flexDirection="column">
            {(token.tokens ?? []).map((child, i) => (
                <Box key={`${i}-${child.type}`} flexDirection="row">
                    <Text color={TONE.brand}>{" ▎ "}</Text>
                    <Box flexDirection="column" flexGrow={1}>
                        {child.type === "paragraph" ? (
                            <Text italic color={FG.sub}>
                                <Inline tokens={(child as Tokens.Paragraph).tokens ?? []}/>
                            </Text>
                        ) : (
                            <BlockToken token={child}/>
                        )}
                    </Box>
                </Box>
            ))}
        </Box>
    );
}

// ── 水平线 ─────────────────────────────────────────────────

function HorizontalRule(): React.ReactElement {
    const width = useWidth();
    const rule = "─".repeat(Math.min(width, 60));
    return <Text color={FG.faint}>{` ${rule}`}</Text>;
}

// ── 表格 ──────────────────────────────────────────────────

function padToCells(text: string, cells: number): string {
    const w = stringWidthLib(text);
    if (w >= cells) return text;
    return text + " ".repeat(cells - w);
}

interface ColumnarLayout {
    fallback: false;
    widths: number[];
    colCount: number;
    gap: string
}

interface FallbackLayout {
    fallback: true;
    labelPad: number;
    valueCells: number
}

function tableLayout(
    headerCells: string[],
    bodyCells: string[][],
    availableWidth: number,
): ColumnarLayout | FallbackLayout {
    const colCount = headerCells.length;
    const GAP = " ";
    const GAP_W = stringWidthLib(GAP);
    const widths = new Array<number>(colCount).fill(0);
    for (let c = 0; c < colCount; c++) {
        widths[c] = Math.max(
            stringWidthLib(headerCells[c] ?? ""),
            ...bodyCells.map((r) => stringWidthLib(r[c] ?? "")),
        );
    }
    const totalWidth = widths.reduce((s, w) => s + w, 0) + GAP_W * (colCount - 1);
    if (totalWidth <= availableWidth) {
        return {fallback: false, widths, colCount, gap: GAP};
    }
    const rawLabel = Math.max(...headerCells.map((h) => stringWidthLib(h))) + 2;
    const labelPad = Math.min(rawLabel, availableWidth - 1);
    const valueCells = availableWidth - labelPad;
    return {fallback: true, labelPad, valueCells};
}

function Table({token}: { token: Tokens.Table }): React.ReactElement {
    const width = useWidth();
    const headerCells = token.header.map((c) => plainText(c.tokens));
    const bodyCells = token.rows.map((row) => row.map((c) => plainText(c.tokens)));
    const layout = tableLayout(headerCells, bodyCells, width);
    if (!layout.fallback)
        return <ColumnarTable headerCells={headerCells} bodyCells={bodyCells} widths={layout.widths}
                              colCount={headerCells.length} gap={layout.gap}/>;
    return <FallbackTable headerCells={headerCells} bodyCells={bodyCells} labelPad={layout.labelPad}
                          valueCells={layout.valueCells}/>;
}

function ColumnarTable({
                           headerCells, bodyCells, widths, colCount, gap,
                       }: {
    headerCells: string[]; bodyCells: string[][]; widths: number[]; colCount: number; gap: string;
}): React.ReactElement {
    const ruleRow = widths.map((w) => "─".repeat(w)).join(gap);
    return (
        <Box flexDirection="column">
            <Box>
                <Text> </Text>
                {headerCells.map((cell, i) => (
                    <React.Fragment key={`h-${i}`}>
                        <Text bold color={FG.sub}>{padToCells(cell, widths[i]!)}</Text>
                        {i < colCount - 1 ? <Text>{gap}</Text> : null}
                    </React.Fragment>
                ))}
            </Box>
            <Box>
                <Text> </Text>
                <Text color={FG.faint}>{ruleRow}</Text>
            </Box>
            {bodyCells.map((row, ri) => (
                <Box key={`tr-${ri}`}>
                    <Text> </Text>
                    {row.map((cell, i) => (
                        <React.Fragment key={`c-${ri}-${i}`}>
                            <Text color={FG.body}>{padToCells(cell ?? "", widths[i]!)}</Text>
                            {i < colCount - 1 ? <Text>{gap}</Text> : null}
                        </React.Fragment>
                    ))}
                </Box>
            ))}
        </Box>
    );
}

function FallbackTable({
                           headerCells, bodyCells, labelPad, valueCells,
                       }: {
    headerCells: string[]; bodyCells: string[][]; labelPad: number; valueCells: number;
}): React.ReactElement {
    return (
        <Box flexDirection="column">
            {bodyCells.map((row, ri) => (
                <Box key={`fr-${ri}`} flexDirection="column">
                    {ri > 0 ? <Text> </Text> : null}
                    {headerCells.map((h, ci) => {
                        const label = `${padToCells(h, labelPad - 2)}: `;
                        const lines = wrapToCells(row[ci] ?? "", valueCells);
                        return lines.map((line, li) => (
                            <Box key={`fc-${ri}-${ci}-${li}`}>
                                {li === 0 ? (
                                    <Text bold color={FG.sub}>{label}</Text>
                                ) : (
                                    <Text>{padToCells("", labelPad)}</Text>
                                )}
                                <Text color={FG.body}>{line}</Text>
                            </Box>
                        ));
                    })}
                </Box>
            ))}
        </Box>
    );
}

// ── 行内元素 ────────────────────────────────────────────────

function Inline({tokens}: { tokens: Token[] }): React.ReactElement {
    return (
        <>
            {tokens.map((tok, i) => (
                <InlineToken key={`${i}-${tok.type}`} token={tok}/>
            ))}
        </>
    );
}

function osc8(children: React.ReactNode, target: string, color: string): React.ReactElement {
    return (
        <Transform transform={(text) => `\x1b]8;;${target}\x1b\\${text}\x1b]8;;\x1b\\`}>
            <Text color={color} underline>
                {children}
            </Text>
        </Transform>
    );
}

function InlineToken({token}: { token: Token }): React.ReactElement {
    switch (token.type) {
        case "text": {
            const t = token as Tokens.Text;
            return t.tokens ? <Inline tokens={t.tokens}/> : <Text>{t.text}</Text>;
        }
        case "strong":
            return (
                <Text bold color={FG.strong}>
                    <Inline tokens={(token as Tokens.Strong).tokens}/>
                </Text>
            );
        case "em":
            return (
                <Text italic>
                    <Inline tokens={(token as Tokens.Em).tokens}/>
                </Text>
            );
        case "codespan":
            return (
                <Text color={FG.strong} backgroundColor={SURFACE.bgCode}>
                    {` ${decodeHtmlEntities((token as Tokens.Codespan).text)} `}
                </Text>
            );
        case "del":
            return (
                <Text color={TONE.err} strikethrough>
                    <Inline tokens={(token as Tokens.Del).tokens}/>
                </Text>
            );
        case "link": {
            const l = token as Tokens.Link;
            return osc8(<Inline tokens={l.tokens}/>, l.href, TONE.brand);
        }
        case "image": {
            const im = token as Tokens.Image;
            return <Text color={TONE.brand}>{`[image: ${im.text || im.href}]`}</Text>;
        }
        case "br":
            return <Text>{"\n"}</Text>;
        case "escape":
            return <Text>{(token as Tokens.Escape).text}</Text>;
        case "html":
            return <Text>{(token as Tokens.HTML).text}</Text>;
        default:
            return <Text>{(token as { raw?: string }).raw ?? ""}</Text>;
    }
}

// ── 工具 ──────────────────────────────────────────────────

export function plainText(tokens: Token[] | undefined): string {
    if (!tokens) return "";
    let out = "";
    for (const t of tokens) {
        switch (t.type) {
            case "text":
                out += (t as Tokens.Text).text;
                break;
            case "strong":
            case "em":
            case "del":
            case "link":
                out += plainText((t as { tokens?: Token[] }).tokens ?? []);
                break;
            case "codespan":
                out += decodeHtmlEntities((t as Tokens.Codespan).text);
                break;
            case "br":
                out += "\n";
                break;
            case "escape":
                out += (t as Tokens.Escape).text;
                break;
            default:
                out += (t as { raw?: string }).raw ?? "";
        }
    }
    return out;
}
