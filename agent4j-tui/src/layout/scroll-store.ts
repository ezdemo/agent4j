/** 滚动状态 —— 记录当前滚动位置、最大滚动高度、卡片高度缓存 */
export interface ScrollState {
    scrollRows: number;
    maxScroll: number;
    cardHeights: Map<string, number>;
    pinned: boolean;
}

export interface ScrollStore {
    getState(): ScrollState;

    scrollUp(n: number): void;

    scrollDown(n: number): void;

    scrollPageUp(pageSize: number): void;

    scrollPageDown(pageSize: number): void;

    jumpToBottom(): void;

    setMaxScroll(max: number): void;

    setCardHeight(id: string, rows: number): void;

    pruneCardHeights(activeIds: Set<string>): void;

    subscribe(listener: () => void): () => void;
}

export function createScrollStore(): ScrollStore {
    let state: ScrollState = {
        scrollRows: 0,
        maxScroll: 0,
        cardHeights: new Map(),
        pinned: true,
    };
    const listeners = new Set<() => void>();
    let notifyPending = false;
    const notify = () => {
        if (notifyPending) return;
        notifyPending = true;
        queueMicrotask(() => {
            notifyPending = false;
            for (const fn of listeners) fn();
        });
    };

    return {
        getState: () => state,
        scrollUp: (n) => {
            const next = Math.min(Math.ceil(state.scrollRows) + n, state.maxScroll);
            state = {...state, scrollRows: next, pinned: next >= state.maxScroll};
            notify();
        },
        scrollDown: (n) => {
            const next = Math.max(0, Math.ceil(state.scrollRows) - n);
            state = {...state, scrollRows: next, pinned: next >= state.maxScroll};
            notify();
        },
        scrollPageUp: (pageSize) => {
            const next = Math.min(Math.ceil(state.scrollRows) + pageSize, state.maxScroll);
            state = {...state, scrollRows: next, pinned: next >= state.maxScroll};
            notify();
        },
        scrollPageDown: (pageSize) => {
            const next = Math.max(0, Math.ceil(state.scrollRows) - pageSize);
            state = {...state, scrollRows: next, pinned: next >= state.maxScroll};
            notify();
        },
        jumpToBottom: () => {
            state = {...state, scrollRows: state.maxScroll, pinned: true};
            notify();
        },
        setMaxScroll: (max) => {
            // Round to integer — fractional scroll values cause terminal jitter
            const maxInt = Math.ceil(max);
            if (maxInt === state.maxScroll) return;
            const pinned = state.scrollRows >= state.maxScroll;
            const rows = pinned ? maxInt : state.scrollRows;
            state = {...state, maxScroll: maxInt, scrollRows: Math.min(rows, maxInt), pinned};
            notify();
        },
        setCardHeight: (id, rows) => {
            if (rows <= 0) return;
            const heights = new Map(state.cardHeights);
            const prev = heights.get(id) ?? 0;
            // Monotonic lock for streaming cards: only accept height INCREASES
            if (rows <= prev) return;
            heights.set(id, rows);
            state = {...state, cardHeights: heights};
            notify();
        },
        pruneCardHeights: (activeIds) => {
            const heights = new Map(state.cardHeights);
            for (const id of heights.keys()) {
                if (!activeIds.has(id)) heights.delete(id);
            }
            state = {...state, cardHeights: heights};
            notify();
        },
        subscribe: (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
    };
}
