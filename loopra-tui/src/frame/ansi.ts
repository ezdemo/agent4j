/** Batches same-style runs into one SGR — per-cell escapes balloon 200x50 frames to 50KB+. */

import type {Frame, FrameRow} from "./types.js";

const ESC = "\u001b";
const RESET = `${ESC}[0m`;

interface Style {
    fg?: string;
    bg?: string;
    bold?: boolean;
    dim?: boolean;
    italic?: boolean;
    underline?: boolean;
    inverse?: boolean;
    href?: string;
}

function sameStyle(a: Style, b: Style): boolean {
    return (
        a.fg === b.fg &&
        a.bg === b.bg &&
        !!a.bold === !!b.bold &&
        !!a.dim === !!b.dim &&
        !!a.italic === !!b.italic &&
        !!a.underline === !!b.underline &&
        !!a.inverse === !!b.inverse &&
        a.href === b.href
    );
}

function fgEscape(color: string | undefined): string | null {
    if (!color) return null;
    const rgb = parseColor(color);
    if (rgb) return `38;2;${rgb[0]};${rgb[1]};${rgb[2]}`;
    const named = NAMED_FG[color.toLowerCase()];
    if (named !== undefined) return String(named);
    return null;
}

function bgEscape(color: string | undefined): string | null {
    if (!color) return null;
    const rgb = parseColor(color);
    if (rgb) return `48;2;${rgb[0]};${rgb[1]};${rgb[2]}`;
    const named = NAMED_BG[color.toLowerCase()];
    if (named !== undefined) return String(named);
    return null;
}

function parseColor(s: string): [number, number, number] | null {
    if (!s.startsWith("#")) return null;
    const hex = s.slice(1);
    if (hex.length !== 6) return null;
    const r = Number.parseInt(hex.slice(0, 2), 16);
    const g = Number.parseInt(hex.slice(2, 4), 16);
    const b = Number.parseInt(hex.slice(4, 6), 16);
    if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) return null;
    return [r, g, b];
}

const NAMED_FG: Record<string, number> = {
    black: 30,
    red: 31,
    green: 32,
    yellow: 33,
    blue: 34,
    magenta: 35,
    cyan: 36,
    white: 37,
    gray: 90,
    grey: 90,
    brightred: 91,
    brightgreen: 92,
    brightyellow: 93,
    brightblue: 94,
    brightmagenta: 95,
    brightcyan: 96,
    brightwhite: 97,
};

const NAMED_BG: Record<string, number> = {
    black: 40,
    red: 41,
    green: 42,
    yellow: 43,
    blue: 44,
    magenta: 45,
    cyan: 46,
    white: 47,
    gray: 100,
    grey: 100,
};

function styleToAnsi(s: Style): string {
    const codes: string[] = [];
    if (s.bold) codes.push("1");
    if (s.dim) codes.push("2");
    if (s.italic) codes.push("3");
    if (s.underline) codes.push("4");
    if (s.inverse) codes.push("7");
    const fg = fgEscape(s.fg);
    if (fg) codes.push(fg);
    const bg = bgEscape(s.bg);
    if (bg) codes.push(bg);
    if (codes.length === 0) return "";
    return `${ESC}[${codes.join(";")}m`;
}

const EMPTY_STYLE: Style = {};

/** RESET at row end so styling never bleeds onto the next line. */
export function frameToAnsi(f: Frame, opts: { plain?: boolean } = {}): string {
    const out: string[] = [];
    for (let i = 0; i < f.rows.length; i++) {
        out.push(rowToAnsi(f.rows[i]!, opts));
    }
    return out.join("\n");
}

function rowToAnsi(row: FrameRow, opts: { plain?: boolean }): string {
    if (opts.plain) {
        let s = "";
        for (const c of row) {
            if (c.tail) continue;
            s += c.char;
        }
        return s;
    }
    let result = "";
    let curStyle = EMPTY_STYLE;
    let inHref = false;
    let curHref: string | undefined;

    for (const c of row) {
        if (c.tail) continue;
        const cellStyle: Style = {
            fg: c.fg,
            bg: c.bg,
            bold: c.bold,
            dim: c.dim,
            italic: c.italic,
            underline: c.underline,
            inverse: c.inverse,
            href: c.href,
        };
        if (cellStyle.href !== curHref) {
            if (inHref) {
                result += `${ESC}]8;;${ESC}\\`;
                inHref = false;
            }
            if (cellStyle.href !== undefined) {
                result += `${ESC}]8;;${cellStyle.href}${ESC}\\`;
                inHref = true;
            }
            curHref = cellStyle.href;
        }
        if (!sameStyle(curStyle, cellStyle)) {
            result += RESET;
            result += styleToAnsi(cellStyle);
            curStyle = cellStyle;
        }
        result += c.char;
    }
    if (inHref) result += `${ESC}]8;;${ESC}\\`;
    result += RESET;
    return result;
}

export function rowText(row: FrameRow): string {
    let s = "";
    for (const c of row) {
        if (c.tail) continue;
        s += c.char;
    }
    return s;
}
