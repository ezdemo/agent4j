import React from "react";
import type {ScrollState, ScrollStore} from "./scroll-store.js";
import {createScrollStore} from "./scroll-store.js";

const Ctx = React.createContext<ScrollStore | null>(null);

export function ScrollProvider({children}: { children: React.ReactNode }): React.ReactElement {
    const store = React.useMemo(() => createScrollStore(), []);
    return <Ctx.Provider value={store}>{children}</Ctx.Provider>;
}

function useScrollStore(): ScrollStore {
    const s = React.useContext(Ctx);
    if (!s) throw new Error("useScrollState must be used inside ScrollProvider");
    return s;
}

export function useScrollState<T>(selector: (s: ScrollState) => T): T {
    const store = useScrollStore();
    const subscribe = React.useCallback((cb: () => void) => store.subscribe(cb), [store]);
    const getSnapshot = React.useCallback(() => selector(store.getState()), [store, selector]);
    return React.useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}

export function useScrollActions(): Pick<
    ScrollStore,
    "scrollUp" | "scrollDown" | "scrollPageUp" | "scrollPageDown" | "jumpToBottom" | "setMaxScroll" | "setCardHeight" | "pruneCardHeights"
> {
    return useScrollStore();
}
