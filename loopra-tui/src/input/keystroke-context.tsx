/**
 * KeystrokeContext — React surface in front of the raw stdin reader.
 *
 * Replaces Ink's `useInput` chain. Components call `useKeystroke(handler, enabled)`
 * from this module instead of importing `useInput` from "ink". The provider
 * mounted at App level owns a `StdinReader`, subscribes a single fan-out function
 * to it, and dispatches each parsed `KeyEvent` to every active consumer.
 *
 * Why a Context: the provider can be disabled in tests / replay mode without
 * touching the components, and lifecycle (start/stop on mount/unmount) is tied
 * to the React tree rather than a global side effect.
 *
 * Why not just Ink's useInput: Ink's parse-keypress uses a 100 ms intra-CSI
 * timeout that's too short for Windows ConPTY, leaking arrow-key bytes / paste
 * markers into the buffer. This reader uses 250 ms and recognises ESC-stripped
 * variants too.
 */

import {useInput} from "ink";
import React, {createContext, useContext, useEffect, useRef} from "react";
import {getStdinReader, type KeyEvent} from "./stdin-reader.js";

interface KeystrokeBus {
    subscribe(handler: KeystrokeHandler): () => void;
}

export type KeystrokeHandler = (ev: KeyEvent) => void;

export interface KeystrokeReader {
    start(): void;

    subscribe(handler: KeystrokeHandler): () => void;
}

const KeystrokeContext = createContext<KeystrokeBus | null>(null);

export interface KeystrokeProviderProps {
    children: React.ReactNode;
    reader?: KeystrokeReader;
}

export function KeystrokeProvider({children, reader: providedReader}: KeystrokeProviderProps): React.ReactElement {
    const handlersRef = useRef<Set<KeystrokeHandler>>(new Set());
    const busRef = useRef<KeystrokeBus | null>(null);
    if (busRef.current === null) {
        busRef.current = {
            subscribe(handler) {
                handlersRef.current.add(handler);
                return () => {
                    handlersRef.current.delete(handler);
                };
            },
        };
    }

    useEffect(() => {
        const reader = providedReader ?? getStdinReader();
        reader.start();
        const unsubscribe = reader.subscribe((ev) => {
            for (const fn of [...handlersRef.current]) fn(ev);
        });
        return () => {
            unsubscribe();
        };
    }, [providedReader]);

    return <KeystrokeContext.Provider value={busRef.current}>{children}</KeystrokeContext.Provider>;
}

export function useKeystroke(handler: KeystrokeHandler, enabled = true): void {
    const bus = useContext(KeystrokeContext);
    const handlerRef = useRef(handler);
    handlerRef.current = handler;

    useEffect(() => {
        if (!bus || !enabled) return undefined;
        return bus.subscribe((ev) => handlerRef.current(ev));
    }, [bus, enabled]);

    useInput(
        (input, key) => {
            if (bus) return;
            handlerRef.current({
                input,
                upArrow: key.upArrow,
                downArrow: key.downArrow,
                leftArrow: key.leftArrow,
                rightArrow: key.rightArrow,
                return: key.return,
                escape: key.escape,
                backspace: key.backspace,
                delete: key.delete,
                tab: key.tab,
                shift: key.shift,
                ctrl: key.ctrl,
                meta: key.meta,
                pageUp: key.pageUp,
                pageDown: key.pageDown,
            });
        },
        {isActive: !bus && enabled},
    );
}

export function useKeystrokeBus(): KeystrokeBus | null {
    return useContext(KeystrokeContext);
}

export function makeKeyEvent(overrides: Partial<KeyEvent> = {}): KeyEvent {
    return {input: "", ...overrides};
}
