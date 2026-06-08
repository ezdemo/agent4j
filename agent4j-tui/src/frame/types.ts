/** Canonical grid: every row's cell array totals exactly `Frame.width` (counting `tail` cells for 2-wide chars). */

export interface Cell {
    /** 2-wide chars emit a `tail: true, char: ""` follower so row.length === Frame.width invariant holds. */
    char: string;
    /** 1 for ASCII / Latin / most BMP. 2 for CJK / emoji / fullwidth. */
    width: 1 | 2;
    /** Sentinel for the second cell of a 2-wide grapheme. */
    tail?: boolean;
    /** Foreground color: hex `#rrggbb` or named ANSI ("red", "cyan"). */
    fg?: string;
    /** Background color: hex `#rrggbb` or named ANSI. */
    bg?: string;
    bold?: boolean;
    dim?: boolean;
    italic?: boolean;
    underline?: boolean;
    inverse?: boolean;
    /** OSC-8 hyperlink target (cell renders as a clickable link). */
    href?: string;
}

/** INVARIANT: `cells.reduce((a, c) => a + (c.tail ? 0 : c.width), 0) === Frame.width`. */
export type FrameRow = readonly Cell[];

export interface Frame {
    readonly width: number;
    readonly rows: readonly FrameRow[];
}

export interface TextOpts {
    /** Wrap column. Mandatory — text without a budget is a rendering bug. */
    width: number;
    fg?: string;
    bg?: string;
    bold?: boolean;
    dim?: boolean;
    italic?: boolean;
    underline?: boolean;
    inverse?: boolean;
    href?: string;
}
