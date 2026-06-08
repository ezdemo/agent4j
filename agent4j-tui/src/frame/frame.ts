/** Pure primitives on Frame; every row's cells sum to exactly `Frame.width`. */

import type {Cell, Frame, FrameRow, TextOpts} from "./types.js";
import {graphemes, graphemeWidth} from "./width.js";

const SPACE: Cell = {char: " ", width: 1};
const TAIL: Cell = {char: "", width: 1, tail: true};

export function empty(width = 0): Frame {
    return {width, rows: []};
}

export function blank(width: number, height: number): Frame {
    if (width <= 0 || height <= 0) return empty(Math.max(0, width));
    const row: FrameRow = Object.freeze(Array.from({length: width}, () => SPACE));
    const rows: FrameRow[] = [];
    for (let i = 0; i < height; i++) rows.push(row);
    return {width, rows};
}

export function text(s: string, opts: TextOpts): Frame {
    const {width, fg, bg, bold, dim, italic, underline, inverse, href} = opts;
    if (width <= 0) return empty(0);

    const styleOf = (g: string, w: 1 | 2): Cell => {
        const base: Cell = {char: g, width: w};
        if (fg !== undefined) base.fg = fg;
        if (bg !== undefined) base.bg = bg;
        if (bold) base.bold = true;
        if (dim) base.dim = true;
        if (italic) base.italic = true;
        if (underline) base.underline = true;
        if (inverse) base.inverse = true;
        if (href !== undefined) base.href = href;
        return base;
    };

    const rows: FrameRow[] = [];
    const lines = s.split("\n");
    for (const line of lines) {
        if (line.length === 0) {
            rows.push(padRowRight([], width));
            continue;
        }
        let buf: Cell[] = [];
        let bufWidth = 0;
        for (const g of graphemes(line)) {
            const w = graphemeWidth(g);
            if (w === 0) continue;
            if (bufWidth + w > width) {
                rows.push(padRowRight(buf, width - bufWidth));
                buf = [];
                bufWidth = 0;
            }
            buf.push(styleOf(g, w as 1 | 2));
            if (w === 2) buf.push(TAIL);
            bufWidth += w;
        }
        rows.push(padRowRight(buf, width - bufWidth));
    }
    return {width, rows};
}

function padRowRight(cells: Cell[], extraSpaces: number): FrameRow {
    if (extraSpaces <= 0) return cells.slice();
    const out = cells.slice();
    for (let i = 0; i < extraSpaces; i++) out.push(SPACE);
    return out;
}

function spacerRow(width: number): FrameRow {
    if (width <= 0) return [];
    return Array.from({length: width}, () => SPACE);
}

export function vstack(...frames: Frame[]): Frame {
    if (frames.length === 0) return empty(0);
    const w = Math.max(...frames.map((f) => f.width));
    const rows: FrameRow[] = [];
    for (const f of frames) {
        if (f.width === w) {
            rows.push(...f.rows);
        } else {
            const extra = w - f.width;
            for (const r of f.rows) rows.push(padRowRight(r as Cell[], extra));
        }
    }
    return {width: w, rows};
}

export function hstack(...frames: Frame[]): Frame {
    if (frames.length === 0) return empty(0);
    const h = Math.max(...frames.map((f) => f.rows.length));
    const w = frames.reduce((a, f) => a + f.width, 0);
    const rows: FrameRow[] = [];
    for (let i = 0; i < h; i++) {
        const cells: Cell[] = [];
        for (const f of frames) {
            const r = f.rows[i] ?? spacerRow(f.width);
            cells.push(...r);
        }
        rows.push(cells);
    }
    return {width: w, rows};
}

export function pad(f: Frame, top: number, right: number, bottom: number, left: number): Frame {
    const newWidth = f.width + Math.max(0, left) + Math.max(0, right);
    const tPad = Math.max(0, top);
    const bPad = Math.max(0, bottom);
    const blankRow = spacerRow(newWidth);
    const rows: FrameRow[] = [];
    for (let i = 0; i < tPad; i++) rows.push(blankRow);
    if (left <= 0 && right <= 0) {
        rows.push(...f.rows);
    } else {
        const lPad = spacerRow(Math.max(0, left));
        const rPad = spacerRow(Math.max(0, right));
        for (const r of f.rows) rows.push([...lPad, ...r, ...rPad]);
    }
    for (let i = 0; i < bPad; i++) rows.push(blankRow);
    return {width: newWidth, rows};
}

export function borderLeft(f: Frame, color: string, char = "│"): Frame {
    const bar: Cell = {char, width: 1, fg: color};
    const newWidth = f.width + 1;
    const rows: FrameRow[] = [];
    for (const r of f.rows) rows.push([bar, ...r]);
    return {width: newWidth, rows};
}

export function slice(f: Frame, top: number, height: number): Frame {
    if (height <= 0 || f.rows.length === 0) return {width: f.width, rows: []};
    const start = Math.max(0, Math.min(top, f.rows.length));
    const end = Math.max(start, Math.min(start + height, f.rows.length));
    return {width: f.width, rows: f.rows.slice(start, end)};
}

export function bottom(f: Frame, height: number): Frame {
    if (height <= 0) return {width: f.width, rows: []};
    return slice(f, Math.max(0, f.rows.length - height), height);
}

export function viewport(f: Frame, offset: number, height: number): Frame {
    if (height <= 0) return {width: f.width, rows: []};
    const maxOffset = Math.max(0, f.rows.length - height);
    const o = Math.max(0, Math.min(offset, maxOffset));
    const start = Math.max(0, f.rows.length - height - o);
    return slice(f, start, height);
}

export function overlay(base: Frame, top: Frame, x: number, y: number): Frame {
    const rows: FrameRow[] = base.rows.map((r) => r.slice());
    for (let i = 0; i < top.rows.length; i++) {
        const targetRow = rows[y + i] as Cell[] | undefined;
        if (!targetRow) continue;
        const src = top.rows[i]!;
        let col = x;
        for (const cell of src) {
            if (col >= 0 && col < base.width) targetRow[col] = cell;
            col += 1;
        }
    }
    return {width: base.width, rows};
}

export function fitWidth(f: Frame, width: number): Frame {
    if (f.width === width) return f;
    const rows: FrameRow[] = [];
    for (const r of f.rows) {
        if (r.length >= width) {
            const cut = r.slice(0, width) as Cell[];
            const last = cut[cut.length - 1];
            if (last && last.width === 2 && !last.tail) {
                cut[cut.length - 1] = SPACE;
            }
            rows.push(cut);
        } else {
            rows.push(padRowRight(r as Cell[], width - r.length));
        }
    }
    return {width, rows};
}
