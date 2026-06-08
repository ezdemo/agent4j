/**
 * CardStream —— Ink 虚拟滚动卡片列表
 *
 * 只渲染视口窗口内（加缓冲区）的卡片，
 * 视口外的卡片替换为占位 spacer。使用 Yoga 测量跟踪卡片高度。
 */

import {Box, type DOMElement, measureElement, Text} from "ink";
import React, {useEffect, useMemo, useRef, useState} from "react";
import {useScrollActions, useScrollState} from "./scroll-provider.js";

const VISIBLE_BUFFER_ROWS = 30;

export interface CardStreamItem<T> {
    kind: "spacer" | "card";
    rows?: number;
    key: string;
    card?: T;
}

export function computeCardStreamItems<T extends { id: string }>(
    cards: readonly T[],
    cardHeights: ReadonlyMap<string, number>,
    scrollRows: number,
    outerHeight: number,
): CardStreamItem<T>[] {
    const bucket = Math.floor(scrollRows / VISIBLE_BUFFER_ROWS) * VISIBLE_BUFFER_ROWS;
    const winStart = Math.max(0, bucket - VISIBLE_BUFFER_ROWS);
    const winEnd = bucket + outerHeight + VISIBLE_BUFFER_ROWS * 2;
    const out: CardStreamItem<T>[] = [];
    let cursor = 0;
    let pendingSpacer = 0;
    let spacerKey = 0;
    for (const card of cards) {
        const h = cardHeights.get(card.id);
        const cardEnd = cursor + (h ?? 0);
        const live = h === undefined || (cardEnd >= winStart && cursor <= winEnd);
        if (live) {
            if (pendingSpacer > 0) {
                out.push({kind: "spacer", rows: pendingSpacer, key: `sp-${spacerKey++}`});
                pendingSpacer = 0;
            }
            out.push({kind: "card", key: card.id, card});
        } else {
            pendingSpacer += h ?? 0;
        }
        cursor = cardEnd;
    }
    if (pendingSpacer > 0) {
        out.push({kind: "spacer", rows: pendingSpacer, key: `sp-${spacerKey}`});
    }
    return out;
}

export interface CardStreamProps<T extends { id: string }> {
    cards: readonly T[];
    renderCard: (card: T) => React.ReactElement;
    onScrollChange?: (scrollRows: number, maxScroll: number) => void;
}

function CardStreamInner<T extends { id: string }>({
                                                       cards,
                                                       renderCard,
                                                       onScrollChange,
                                                   }: CardStreamProps<T>): React.ReactElement {
    const scrollRows = useScrollState((s) => s.scrollRows);
    const cardHeights = useScrollState((s) => s.cardHeights);
    const {setMaxScroll, setCardHeight, pruneCardHeights} = useScrollActions();

    const outerRef = useRef<DOMElement>(null!);
    const innerRef = useRef<DOMElement>(null!);
    // Only measure outer — used for virtual-scroll viewport window.
    // We intentionally do NOT measure inner to derive maxScroll, because that
    // creates a measurement→maxScroll→re-render cascade every chunk.
    const outer = useElementSize(outerRef);

    // ── maxScroll from cardHeights sum (synchronous, no measurement lag) ──
    // Each card's measured height already includes its marginTop gap.
    const totalCardHeight = useMemo(() => {
        let sum = 0;
        for (const h of cardHeights.values()) sum += h;
        return sum;
    }, [cardHeights]);

    // Stable viewport height — terminal rows minus fixed chrome (header, indicator, composer)
    const viewportRows = Math.max(10, (process.stdout?.rows ?? 40) - 6);
    const maxScroll = Math.max(0, totalCardHeight - viewportRows);

    // Track last-reported maxScroll to avoid spurious updates
    const lastMaxScrollRef = useRef(0);
    useEffect(() => {
        if (maxScroll === lastMaxScrollRef.current) return;
        lastMaxScrollRef.current = maxScroll;
        setMaxScroll(maxScroll);
    }, [maxScroll, setMaxScroll]);

    useEffect(() => {
        if (onScrollChange) onScrollChange(scrollRows, maxScroll);
    }, [scrollRows, maxScroll, onScrollChange]);

    useEffect(() => {
        const live = new Set(cards.map((c) => c.id));
        pruneCardHeights(live);
    }, [cards, pruneCardHeights]);

    const items = useMemo(
        () => computeCardStreamItems(cards, cardHeights, scrollRows, outer.height || viewportRows),
        [cards, cardHeights, scrollRows, outer.height, viewportRows],
    );

    // Always reserve 1 row for scroll indicator — prevents outer.height from
    // oscillating when the indicator appears/disappears, which would feed back
    // into maxScroll → scrollRows → indicator visibility.
    return (
        <>
            <Box height={1} flexShrink={0}>
                {scrollRows > 0 ? (
                    <Text color="#4d5666">
                        ↑ {scrollRows} more · PgUp to scroll
                    </Text>
                ) : (
                    <Text> </Text>
                )}
            </Box>
            <Box ref={outerRef} flexDirection="column" flexGrow={1} overflow="hidden">
                <Box ref={innerRef} flexDirection="column" marginTop={-scrollRows} flexShrink={0}>
                    {items.map((item) =>
                        item.kind === "spacer" ? (
                            <Box key={item.key} height={item.rows!} flexShrink={0}/>
                        ) : (
                            <MemoMeasuredCard key={item.key} card={item.card!} render={renderCard}
                                              report={setCardHeight}/>
                        ),
                    )}
                </Box>
            </Box>
        </>
    );
}

/** Measure element size after each render. Replaces Ink 4's useBoxMetrics. */
function useElementSize(ref: React.RefObject<DOMElement | null>): { width: number; height: number } {
    const [size, setSize] = useState({width: 0, height: 0});

    useEffect(() => {
        const el = ref.current;
        if (!el) return;
        const m = measureElement(el);
        setSize((prev) =>
            prev.width !== m.width || prev.height !== m.height ? m : prev,
        );
    });

    return size;
}

/** Check whether a card is still receiving streaming updates */
function isCardStreaming(card: Record<string, unknown>): boolean {
    // Assistant cards have an explicit `streaming` flag
    if (card.streaming === true) return true;
    // Reasoning / tool cards: not done means still in progress
    if (card.done === false && (card.kind === "reasoning" || card.kind === "tool" || card.kind === "assistant")) return true;
    return false;
}

function MeasuredCard<T extends { id: string }>({
                                                    card,
                                                    render,
                                                    report,
                                                }: {
    card: T;
    render: (c: T) => React.ReactElement;
    report: (id: string, rows: number) => void;
}): React.ReactElement {
    const ref = useRef<DOMElement>(null!);
    const m = useElementSize(ref);
    const lastReportedRef = useRef(0);
    // Track whether we've ever reported a height for this card.
    // Once reported, we keep it — streaming cards grow monotonically.
    const hasReportedRef = useRef(false);

    useEffect(() => {
        const h = m.height;
        if (h <= 0) return;

        const streaming = isCardStreaming(card as unknown as Record<string, unknown>);

        // During streaming, only report the FIRST non-zero height so the card
        // gets a placeholder in the scroll layout. Subsequent growth is ignored
        // until streaming finishes, avoiding a measurement→re-render cascade.
        if (streaming && hasReportedRef.current) return;

        if (h === lastReportedRef.current) return;
        if (h < lastReportedRef.current) return; // monotonic lock
        lastReportedRef.current = h;
        hasReportedRef.current = true;
        report(card.id, h);
    }, [card.id, m.height, report]);

    return (
        <Box ref={ref} flexDirection="column" flexShrink={0}>
            {render(card)}
        </Box>
    );
}

/** 按卡片内容比较的 memo —— streaming 时不重渲染未变化的卡片 */
const MemoMeasuredCard = React.memo(MeasuredCard, (prev, next) => {
    if (prev.card.id !== next.card.id) return false;
    const a = prev.card as Record<string, unknown>;
    const b = next.card as Record<string, unknown>;
    if (a.kind !== b.kind) return false;
    if (a.text !== b.text) return false;
    if (a.output !== b.output) return false;
    if (a.done !== b.done) return false;
    if (a.streaming !== b.streaming) return false;
    return true;
}) as typeof MeasuredCard;

/** Memoised CardStream — avoids re-rendering when scroll position changes but cards don't */
export const CardStream = React.memo(CardStreamInner, (prev, next) => {
    if (prev.cards !== next.cards) return false;
    if (prev.renderCard !== next.renderCard) return false;
    if (prev.onScrollChange !== next.onScrollChange) return false;
    return true;
}) as typeof CardStreamInner;
