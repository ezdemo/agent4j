/**
 * 主题 Token 系统 —— 基于 Proxy 的热切换颜色引擎
 *
 * 移植自 reasonix，包含 7 套配色方案。
 * FG / TONE / TONE_ACTIVE / SURFACE / ROLE 为响应式 Proxy，
 * 主题切换时无需重渲染消费者。
 */

export type ThemeName =
    | "default"
    | "dark"
    | "light"
    | "tokyo-night"
    | "github-dark"
    | "github-light"
    | "high-contrast";

export interface ThemeTokens {
    fg: {
        strong: string;
        body: string;
        sub: string;
        meta: string;
        faint: string;
    };
    tone: {
        brand: string;
        accent: string;
        violet: string;
        ok: string;
        warn: string;
        err: string;
        info: string;
    };
    toneActive: ThemeTokens["tone"];
    surface: {
        bg: string;
        bgInput: string;
        bgCode: string;
        bgElev: string;
    };
    user: { color: string; glyph: string };
    assistant: { color: string; glyph: string };
    tool: { color: string; glyph: string };
    system: { color: string; glyph: string };
    reasoning: { color: string; glyph: string };
}

type ThemeBase = Omit<ThemeTokens, "user" | "assistant" | "tool" | "system" | "reasoning">;

function roles(tone: ThemeTokens["tone"]): Pick<ThemeTokens, "user" | "assistant" | "tool" | "system" | "reasoning"> {
    return {
        user: {color: tone.brand, glyph: "◇"},
        assistant: {color: tone.accent, glyph: "◆"},
        tool: {color: tone.info, glyph: "▣"},
        system: {color: "#6e7681", glyph: "■"},
        reasoning: {color: tone.violet, glyph: "◆"},
    };
}

function defineTheme(base: ThemeBase): ThemeTokens {
    return {...base, ...roles(base.tone)};
}

// ── github-dark（默认）───────────────────────────────────────

const githubDark = defineTheme({
    fg: {
        strong: "#e6edf3",
        body: "#c9d1d9",
        sub: "#8b949e",
        meta: "#6e7681",
        faint: "#484f58",
    },
    tone: {
        brand: "#79c0ff",
        accent: "#d2a8ff",
        violet: "#b395f5",
        ok: "#7ee787",
        warn: "#f0b07d",
        err: "#ff8b81",
        info: "#79c0ff",
    },
    toneActive: {
        brand: "#a5d6ff",
        accent: "#e2c5ff",
        violet: "#c8aaff",
        ok: "#a8f5ad",
        warn: "#ffc99e",
        err: "#ffaba3",
        info: "#a5d6ff",
    },
    surface: {
        bg: "#0a0c10",
        bgInput: "#0d1015",
        bgCode: "#06080c",
        bgElev: "#11141a",
    },
});

// ── dark ────────────────────────────────────────────────────

const dark = defineTheme({
    fg: {
        strong: "#f4f7fb",
        body: "#d8dee9",
        sub: "#a7b1c2",
        meta: "#778294",
        faint: "#4d5666",
    },
    tone: {
        brand: "#7dd3fc",
        accent: "#c084fc",
        violet: "#a78bfa",
        ok: "#86efac",
        warn: "#fbbf24",
        err: "#f87171",
        info: "#60a5fa",
    },
    toneActive: {
        brand: "#bae6fd",
        accent: "#e9d5ff",
        violet: "#ddd6fe",
        ok: "#bbf7d0",
        warn: "#fde68a",
        err: "#fecaca",
        info: "#bfdbfe",
    },
    surface: {
        bg: "#0b1020",
        bgInput: "#111827",
        bgCode: "#080c16",
        bgElev: "#151d2f",
    },
});

// ── light ───────────────────────────────────────────────────

const light = defineTheme({
    fg: {
        strong: "#111827",
        body: "#1f2937",
        sub: "#4b5563",
        meta: "#6b7280",
        faint: "#9ca3af",
    },
    tone: {
        brand: "#2563eb",
        accent: "#7c3aed",
        violet: "#6d28d9",
        ok: "#15803d",
        warn: "#b45309",
        err: "#dc2626",
        info: "#0369a1",
    },
    toneActive: {
        brand: "#1d4ed8",
        accent: "#6d28d9",
        violet: "#5b21b6",
        ok: "#166534",
        warn: "#92400e",
        err: "#b91c1c",
        info: "#075985",
    },
    surface: {
        bg: "#ffffff",
        bgInput: "#f8fafc",
        bgCode: "#f3f4f6",
        bgElev: "#eef2f7",
    },
});

// ── tokyo-night ─────────────────────────────────────────────

const tokyoNight = defineTheme({
    fg: {
        strong: "#c0caf5",
        body: "#a9b1d6",
        sub: "#9aa5ce",
        meta: "#565f89",
        faint: "#414868",
    },
    tone: {
        brand: "#7aa2f7",
        accent: "#bb9af7",
        violet: "#9d7cd8",
        ok: "#9ece6a",
        warn: "#e0af68",
        err: "#f7768e",
        info: "#2ac3de",
    },
    toneActive: {
        brand: "#a9c7ff",
        accent: "#d7b9ff",
        violet: "#c6a0f6",
        ok: "#b9f27c",
        warn: "#ffd089",
        err: "#ff9cac",
        info: "#7dcfff",
    },
    surface: {
        bg: "#1a1b26",
        bgInput: "#1f2335",
        bgCode: "#16161e",
        bgElev: "#24283b",
    },
});

// ── github-light ────────────────────────────────────────────

const githubLight = defineTheme({
    fg: {
        strong: "#1f2328",
        body: "#24292f",
        sub: "#57606a",
        meta: "#6e7781",
        faint: "#8c959f",
    },
    tone: {
        brand: "#0969da",
        accent: "#8250df",
        violet: "#6639ba",
        ok: "#1a7f37",
        warn: "#9a6700",
        err: "#cf222e",
        info: "#0969da",
    },
    toneActive: {
        brand: "#0550ae",
        accent: "#6639ba",
        violet: "#512a97",
        ok: "#116329",
        warn: "#7d4e00",
        err: "#a40e26",
        info: "#0550ae",
    },
    surface: {
        bg: "#ffffff",
        bgInput: "#f6f8fa",
        bgCode: "#f6f8fa",
        bgElev: "#eaeef2",
    },
});

// ── high-contrast ───────────────────────────────────────────

const highContrast = defineTheme({
    fg: {
        strong: "#ffffff",
        body: "#f5f5f5",
        sub: "#d4d4d4",
        meta: "#bdbdbd",
        faint: "#8a8a8a",
    },
    tone: {
        brand: "#00e5ff",
        accent: "#ff4dff",
        violet: "#b388ff",
        ok: "#00ff66",
        warn: "#ffdd00",
        err: "#ff4d4d",
        info: "#4da3ff",
    },
    toneActive: {
        brand: "#80f2ff",
        accent: "#ff99ff",
        violet: "#d0b3ff",
        ok: "#80ffb3",
        warn: "#ffee80",
        err: "#ff9999",
        info: "#99c9ff",
    },
    surface: {
        bg: "#000000",
        bgInput: "#0a0a0a",
        bgCode: "#050505",
        bgElev: "#141414",
    },
});

// ── 注册表 ─────────────────────────────────────────────────

export const THEMES = {
    default: githubDark,
    dark,
    light,
    "tokyo-night": tokyoNight,
    "github-dark": githubDark,
    "github-light": githubLight,
    "high-contrast": highContrast,
} as const satisfies Record<ThemeName, ThemeTokens>;

export const DEFAULT_THEME_NAME: ThemeName = "default";

export function isThemeName(value: string): value is ThemeName {
    return Object.prototype.hasOwnProperty.call(THEMES, value);
}

export function resolveThemeName(value?: string | null): ThemeName {
    if (!value || value === "auto") return DEFAULT_THEME_NAME;
    return isThemeName(value) ? value : DEFAULT_THEME_NAME;
}

// ── 响应式 Proxy ────────────────────────────────────────────

let activeTheme: ThemeTokens = THEMES[DEFAULT_THEME_NAME];
let activeThemeVersion = 0;

export function setActiveTheme(theme: ThemeTokens): () => void {
    const previous = activeTheme;
    activeTheme = theme;
    activeThemeVersion += 1;
    const version = activeThemeVersion;
    return () => {
        if (activeThemeVersion !== version || activeTheme !== theme) return;
        activeTheme = previous;
        activeThemeVersion += 1;
    };
}

function proxyTokens<T extends object>(select: (theme: ThemeTokens) => T): T {
    const target = select(THEMES[DEFAULT_THEME_NAME]);
    return new Proxy(target, {
        get(_target, prop: string | symbol) {
            return select(activeTheme)[prop as keyof T];
        },
        getOwnPropertyDescriptor(_target, prop: string | symbol) {
            return Reflect.getOwnPropertyDescriptor(select(activeTheme), prop);
        },
        has(_target, prop: string | symbol) {
            return prop in select(activeTheme);
        },
        ownKeys() {
            return Reflect.ownKeys(select(activeTheme));
        },
    });
}

export const FG = proxyTokens((t) => t.fg);
export const TONE = proxyTokens((t) => t.tone);
export const TONE_ACTIVE = proxyTokens((t) => t.toneActive);
export const SURFACE = proxyTokens((t) => t.surface);
export const ROLE = proxyTokens((t) => ({
    user: t.user,
    assistant: t.assistant,
    tool: t.tool,
    system: t.system,
    reasoning: t.reasoning,
}));
