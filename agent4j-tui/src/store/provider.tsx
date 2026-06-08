/**
 * Store Provider —— 基于 createStore + useSyncExternalStore 的状态管理
 * 纯 reducer 模式，无外部依赖
 */
import React, {createContext, useCallback, useContext, useSyncExternalStore} from "react";
import type {AgentEvent} from "./events.js";
import type {AgentState} from "./reducer.js";
import {initialState, reduce} from "./reducer.js";

export interface AgentStore {
    getState(): AgentState;

    dispatch(event: AgentEvent): void;

    subscribe(listener: () => void): () => void;
}

function createStore(): AgentStore {
    let state = initialState();
    const listeners = new Set<() => void>();

    // 短延迟批量合并：30ms 内的多次 dispatch 合并为一次渲染
    // queueMicrotask 只能合并同一宏任务内的调用；流式 chunk 来自不同
    // 宏任务（WebSocket / setInterval），需要短 timer 将它们聚拢。
    let notifyTimer: ReturnType<typeof setTimeout> | null = null;
    const FLUSH_DELAY_MS = 30;
    const notify = () => {
        if (notifyTimer !== null) return;
        notifyTimer = setTimeout(() => {
            notifyTimer = null;
            for (const fn of listeners) fn();
        }, FLUSH_DELAY_MS);
    };

    return {
        getState: () => state,
        dispatch: (event) => {
            state = reduce(state, event);
            notify();
        },
        subscribe: (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
    };
}

const StoreContext = createContext<AgentStore | null>(null);

export function StoreProvider({children}: { children: React.ReactNode }): React.ReactElement {
    const storeRef = React.useRef<AgentStore | null>(null);
    if (storeRef.current === null) storeRef.current = createStore();
    return <StoreContext.Provider value={storeRef.current}>{children}</StoreContext.Provider>;
}

export function useStore(): AgentStore {
    const store = useContext(StoreContext);
    if (!store) throw new Error("useStore() called outside <StoreProvider>");
    return store;
}

export function useAgentState<U>(selector: (state: AgentState) => U): U {
    const store = useStore();
    const subscribe = useCallback((cb: () => void) => store.subscribe(cb), [store]);
    const get = useCallback(() => selector(store.getState()), [store, selector]);
    return useSyncExternalStore(subscribe, get, get);
}

export function useDispatch(): (event: AgentEvent) => void {
    return useStore().dispatch;
}
