/** Sole stdin owner; 250 ms ESC-ambiguity timer (ConPTY splits sequences past parse-keypress's 100 ms). */

import {stdin} from "node:process";

export interface KeyEvent {
    input: string;
    upArrow?: boolean;
    downArrow?: boolean;
    leftArrow?: boolean;
    rightArrow?: boolean;
    pageUp?: boolean;
    pageDown?: boolean;
    home?: boolean;
    end?: boolean;
    delete?: boolean;
    backspace?: boolean;
    tab?: boolean;
    return?: boolean;
    escape?: boolean;
    shift?: boolean;
    ctrl?: boolean;
    meta?: boolean;
    paste?: boolean;
    mouseScrollUp?: boolean;
    mouseScrollDown?: boolean;
    mouseClick?: boolean;
    mouseDrag?: boolean;
    mouseRelease?: boolean;
    mouseRow?: number;
    mouseCol?: number;
}

type Subscriber = (ev: KeyEvent) => void;

const ESC_TIMEOUT_MS = 250;

const PASTE_START = "\x1b[200~";
const PASTE_END = "\x1b[201~";
const PASTE_START_BARE = "[200~";
const PASTE_END_BARE = "[201~";

const CSI_TAIL_MAP: ReadonlyArray<{ tail: string; ev: KeyEvent }> = [
    {tail: "A", ev: {input: "", upArrow: true}},
    {tail: "B", ev: {input: "", downArrow: true}},
    {tail: "C", ev: {input: "", rightArrow: true}},
    {tail: "D", ev: {input: "", leftArrow: true}},
    {tail: "H", ev: {input: "", home: true}},
    {tail: "F", ev: {input: "", end: true}},
    {tail: "1~", ev: {input: "", home: true}},
    {tail: "4~", ev: {input: "", end: true}},
    {tail: "5~", ev: {input: "", pageUp: true}},
    {tail: "6~", ev: {input: "", pageDown: true}},
    {tail: "3~", ev: {input: "", delete: true}},
    {tail: "Z", ev: {input: "", shift: true, tab: true}},
    {tail: "1;2Z", ev: {input: "", shift: true, tab: true}},
    {tail: "27;2;9~", ev: {input: "", tab: true, shift: true}},
    {tail: "27;2;13~", ev: {input: "", return: true, shift: true}},
    {tail: "27;5;13~", ev: {input: "", return: true, ctrl: true}},
    {tail: "27;6;13~", ev: {input: "", return: true, ctrl: true, shift: true}},
    {tail: "9;2u", ev: {input: "", tab: true, shift: true}},
    {tail: "13;2u", ev: {input: "", return: true, shift: true}},
    {tail: "13;5u", ev: {input: "", return: true, ctrl: true}},
    {tail: "13;6u", ev: {input: "", return: true, ctrl: true, shift: true}},
];

const SS3_MAP: Record<string, KeyEvent> = {
    A: {input: "", upArrow: true},
    B: {input: "", downArrow: true},
    C: {input: "", rightArrow: true},
    D: {input: "", leftArrow: true},
    H: {input: "", home: true},
    F: {input: "", end: true},
};

function tryEscapelessCsi(chunk: string, i: number): { advance: number; ev: KeyEvent } | null {
    if (chunk[i] !== "[") return null;
    for (const entry of CSI_TAIL_MAP) {
        const candidate = `[${entry.tail}`;
        if (chunk.slice(i, i + candidate.length) === candidate) {
            return {advance: candidate.length, ev: entry.ev};
        }
    }
    return null;
}

const SGR_MOUSE_ESCAPELESS_RE = /^\[<\d+;\d+;\d+[Mm]/;

function decodeSgrMouseBody(body: string): KeyEvent | null {
    const m = /^<(\d+);(\d+);(\d+)([Mm])$/.exec(body);
    if (!m) return null;
    const btn = Number.parseInt(m[1]!, 10);
    const col = Number.parseInt(m[2]!, 10);
    const row = Number.parseInt(m[3]!, 10);
    if (!Number.isFinite(btn) || !Number.isFinite(col) || !Number.isFinite(row)) return null;
    const tail = m[4]!;
    if (tail === "m") return {input: "", mouseRelease: true, mouseRow: row, mouseCol: col};
    if (btn === 64) return {input: "", mouseScrollUp: true, mouseRow: row, mouseCol: col};
    if (btn === 65) return {input: "", mouseScrollDown: true, mouseRow: row, mouseCol: col};
    if (btn === 0) return {input: "", mouseClick: true, mouseRow: row, mouseCol: col};
    if (btn === 32) return {input: "", mouseDrag: true, mouseRow: row, mouseCol: col};
    return null;
}

function tryEscapelessSgrMouse(
    chunk: string,
    i: number,
): { advance: number; ev: KeyEvent | null } | null {
    if (chunk[i] !== "[") return null;
    const m = SGR_MOUSE_ESCAPELESS_RE.exec(chunk.slice(i));
    if (!m) return null;
    const body = m[0].slice(1);
    return {advance: m[0].length, ev: decodeSgrMouseBody(body)};
}

function isCsiFinal(ch: string): boolean {
    const code = ch.charCodeAt(0);
    return code >= 0x40 && code <= 0x7e;
}

function lookupCsi(tail: string): KeyEvent | null {
    for (const entry of CSI_TAIL_MAP) {
        if (entry.tail === tail) return entry.ev;
    }
    return null;
}

function decodeModifiedKey(cp: number, mod: number): KeyEvent | null {
    if (mod < 1 || mod > 8) return null;
    const bits = mod - 1;
    const shift = (bits & 1) !== 0;
    const alt = (bits & 2) !== 0;
    const ctrl = (bits & 4) !== 0;
    if (cp >= 0x20 && cp <= 0x7e && !ctrl && !alt) {
        const ev: KeyEvent = {input: String.fromCharCode(cp)};
        if (shift) ev.shift = true;
        return ev;
    }
    if (cp >= 0x20 && cp <= 0x7e && alt && !ctrl) {
        const ev: KeyEvent = {input: String.fromCharCode(cp), meta: true};
        if (shift) ev.shift = true;
        return ev;
    }
    if (cp >= 0x41 && cp <= 0x7a && ctrl && !alt) {
        const ev: KeyEvent = {input: String.fromCharCode(cp).toLowerCase(), ctrl: true};
        if (shift) ev.shift = true;
        return ev;
    }
    return null;
}

function tryDecodeGenericCsi(seq: string): KeyEvent | null {
    let m = /^27;(\d+);(\d+)~$/.exec(seq);
    if (m) return decodeModifiedKey(Number.parseInt(m[2]!, 10), Number.parseInt(m[1]!, 10));
    m = /^(\d+);(\d+)u$/.exec(seq);
    if (m) return decodeModifiedKey(Number.parseInt(m[1]!, 10), Number.parseInt(m[2]!, 10));
    m = /^(\d+)u$/.exec(seq);
    if (m) return decodeModifiedKey(Number.parseInt(m[1]!, 10), 1);
    return null;
}

const PASTE_INVISIBLE_RE = /[\u200B\u200E\u200F\u202A-\u202E\u2060\u2066-\u2069\u00AD\uFEFF]/g;

export function sanitizePasteText(s: string): string {
    return s.replace(PASTE_INVISIBLE_RE, "").replace(/\r\n?/g, "\n");
}

export function looksLikeUnbracketedPaste(chunk: string): boolean {
    if (chunk.length < 2) return false;
    if (chunk.includes(PASTE_START) || chunk.includes(PASTE_START_BARE)) return false;
    if (chunk.includes(PASTE_END) || chunk.includes(PASTE_END_BARE)) return false;
    if (chunk.includes("\x1b")) return false;
    const norm = chunk.replace(/\r\n/g, "\n");
    if (norm === "\r" || norm === "\n") return false;
    let breaks = 0;
    let firstBreakIdx = -1;
    for (let i = 0; i < norm.length; i++) {
        const c = norm[i]!;
        if (c === "\r" || c === "\n") {
            if (firstBreakIdx < 0) firstBreakIdx = i;
            breaks++;
        }
    }
    if (breaks >= 2) return true;
    if (breaks === 1) return firstBreakIdx > 0 && firstBreakIdx < norm.length - 1;
    return false;
}

export class StdinReader {
    private subscribers = new Set<Subscriber>();
    private state: "idle" | "esc" | "csi" | "ss3" | "paste" = "idle";
    private csiBuf = "";
    private pasteBuf = "";
    private escTimer: NodeJS.Timeout | null = null;
    private escImmediate: NodeJS.Immediate | null = null;
    private started = false;
    private listener: ((chunk: Buffer | string) => void) | null = null;

    start(): void {
        if (this.started) return;
        try {
            stdin.setRawMode(true);
        } catch {
            return;
        }
        stdin.setEncoding("utf8");
        stdin.resume();
        this.listener = (chunk) =>
            this.handleChunk(typeof chunk === "string" ? chunk : chunk.toString("utf8"));
        stdin.on("data", this.listener);
        this.started = true;
    }

    stop(): void {
        if (!this.started) return;
        if (this.listener) {
            stdin.off("data", this.listener);
            this.listener = null;
        }
        try {
            stdin.setRawMode(false);
        } catch {
            /* ignore */
        }
        stdin.pause();
        this.cancelEscTimer();
        this.state = "idle";
        this.csiBuf = "";
        this.pasteBuf = "";
        this.started = false;
    }

    subscribe(fn: Subscriber): () => void {
        this.subscribers.add(fn);
        return () => {
            this.subscribers.delete(fn);
        };
    }

    feed(chunk: string): void {
        this.handleChunk(chunk);
    }

    private dispatch(ev: KeyEvent): void {
        for (const sub of this.subscribers) sub(ev);
    }

    private cancelEscTimer(): void {
        if (this.escTimer) {
            clearTimeout(this.escTimer);
            this.escTimer = null;
        }
        if (this.escImmediate) {
            clearImmediate(this.escImmediate);
            this.escImmediate = null;
        }
    }

    private scheduleEscTimer(): void {
        this.cancelEscTimer();
        this.escTimer = setTimeout(() => {
            this.escTimer = null;
            this.escImmediate = setImmediate(() => {
                this.escImmediate = null;
                if (this.state === "esc") {
                    this.state = "idle";
                    this.dispatch({input: "", escape: true});
                }
            });
        }, ESC_TIMEOUT_MS);
    }

    private handleChunk(rawChunk: string): void {
        this.cancelEscTimer();
        const chunk =
            this.state === "idle" && looksLikeUnbracketedPaste(rawChunk)
                ? PASTE_START + rawChunk + PASTE_END
                : rawChunk;
        let i = 0;
        while (i < chunk.length) {
            if (this.state === "paste") {
                const endA = chunk.indexOf(PASTE_END, i);
                const endB = chunk.indexOf(PASTE_END_BARE, i);
                let endIdx = -1;
                let endLen = 0;
                if (endA !== -1 && (endB === -1 || endA <= endB)) {
                    endIdx = endA;
                    endLen = PASTE_END.length;
                } else if (endB !== -1) {
                    endIdx = endB;
                    endLen = PASTE_END_BARE.length;
                }
                if (endIdx === -1) {
                    this.pasteBuf += chunk.slice(i);
                    i = chunk.length;
                    break;
                }
                this.pasteBuf += chunk.slice(i, endIdx);
                this.dispatch({input: sanitizePasteText(this.pasteBuf), paste: true});
                this.pasteBuf = "";
                this.state = "idle";
                i = endIdx + endLen;
                continue;
            }

            if (this.state === "csi") {
                const ch = chunk[i]!;
                this.csiBuf += ch;
                if (isCsiFinal(ch)) {
                    this.dispatchCsi(this.csiBuf);
                    this.csiBuf = "";
                    if (this.state === "csi") this.state = "idle";
                }
                i++;
                continue;
            }

            if (this.state === "ss3") {
                const ev = SS3_MAP[chunk[i]!];
                if (ev) this.dispatch(ev);
                this.state = "idle";
                i++;
                continue;
            }

            if (this.state === "esc") {
                const ch = chunk[i]!;
                if (ch === "[") {
                    this.state = "csi";
                    this.csiBuf = "";
                    i++;
                    continue;
                }
                if (ch === "O") {
                    this.state = "ss3";
                    i++;
                    continue;
                }
                if (ch === "\r" || ch === "\n") {
                    this.dispatch({input: "", return: true, meta: true});
                    this.state = "idle";
                    i++;
                    continue;
                }
                this.dispatch({input: ch, meta: true});
                this.state = "idle";
                i++;
                continue;
            }

            const ch = chunk[i]!;

            if (ch === "\x1b") {
                this.state = "esc";
                i++;
                continue;
            }

            if (chunk.slice(i, i + PASTE_START_BARE.length) === PASTE_START_BARE) {
                this.state = "paste";
                this.pasteBuf = "";
                i += PASTE_START_BARE.length;
                continue;
            }

            const escapeless = tryEscapelessCsi(chunk, i);
            if (escapeless) {
                this.dispatch(escapeless.ev);
                i += escapeless.advance;
                continue;
            }
            const mouseEscapeless = tryEscapelessSgrMouse(chunk, i);
            if (mouseEscapeless) {
                if (mouseEscapeless.ev) this.dispatch(mouseEscapeless.ev);
                i += mouseEscapeless.advance;
                continue;
            }

            if (ch === "\r") {
                this.dispatch({input: "", return: true});
                i++;
                continue;
            }
            if (ch === "\n") {
                this.dispatch({input: "j", ctrl: true});
                i++;
                continue;
            }
            if (ch === "\t") {
                this.dispatch({input: "", tab: true});
                i++;
                continue;
            }
            if (ch === "\x7f" || ch === "\b") {
                this.dispatch({input: "", backspace: true});
                i++;
                continue;
            }
            if (ch === "\x03") {
                this.dispatch({input: "c", ctrl: true});
                i++;
                continue;
            }

            const code = ch.charCodeAt(0);
            if (code >= 1 && code <= 26) {
                const letter = String.fromCharCode(0x60 + code);
                this.dispatch({input: letter, ctrl: true});
                i++;
                continue;
            }

            let end = i + 1;
            while (end < chunk.length) {
                const c = chunk[end]!;
                if (c === "\x1b" || c === "\r" || c === "\n" || c === "\t") break;
                if (c === "\x7f" || c === "\b" || c === "\x03") break;
                const cc = c.charCodeAt(0);
                if (cc >= 1 && cc <= 26) break;
                if (c === "[" && (tryEscapelessCsi(chunk, end) || tryEscapelessSgrMouse(chunk, end))) break;
                if (chunk.slice(end, end + PASTE_START_BARE.length) === PASTE_START_BARE) break;
                end++;
            }
            this.dispatch({input: chunk.slice(i, end)});
            i = end;
        }

        if (this.state === "esc") {
            this.scheduleEscTimer();
        }
    }

    private dispatchCsi(seq: string): void {
        if (seq === "200~") {
            this.state = "paste";
            this.pasteBuf = "";
            return;
        }
        if (seq === "201~") {
            return;
        }
        if (seq.length > 1 && seq.charCodeAt(0) === 60 /* '<' */) {
            const ev = decodeSgrMouseBody(seq);
            if (ev) this.dispatch(ev);
            return;
        }
        const ev = lookupCsi(seq);
        if (ev) {
            this.dispatch(ev);
            return;
        }
        const generic = tryDecodeGenericCsi(seq);
        if (generic) {
            this.dispatch(generic);
            return;
        }
    }
}

let singleton: StdinReader | null = null;

export function getStdinReader(): StdinReader {
    if (!singleton) singleton = new StdinReader();
    return singleton;
}
