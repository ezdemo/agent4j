import React, {createContext, useContext, useMemo} from "react";
import type {ThemeName, ThemeTokens} from "./tokens.js";
import {DEFAULT_THEME_NAME, setActiveTheme, THEMES} from "./tokens.js";

const ThemeContext = createContext<ThemeTokens>(THEMES[DEFAULT_THEME_NAME]);

export interface ThemeProviderProps {
    children: React.ReactNode;
    theme?: ThemeName;
}

export function ThemeProvider({children, theme = DEFAULT_THEME_NAME}: ThemeProviderProps): React.ReactElement {
    const tokens = THEMES[theme] ?? THEMES[DEFAULT_THEME_NAME];
    return <ThemeContext.Provider value={tokens}>{children}</ThemeContext.Provider>;
}

export function useThemeTokens(): ThemeTokens {
    return useContext(ThemeContext);
}

export function useThemeSwitcher(): {
    setTheme: (name: ThemeName) => () => void;
    currentTheme: ThemeName;
} {
    const tokens = useThemeTokens();
    const currentTheme = useMemo<ThemeName>(() => {
        for (const [name, t] of Object.entries(THEMES)) {
            if (t === tokens) return name as ThemeName;
        }
        return DEFAULT_THEME_NAME;
    }, [tokens]);

    return {
        setTheme: (name: ThemeName) => setActiveTheme(THEMES[name]),
        currentTheme,
    };
}
