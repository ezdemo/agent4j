/**
 * SlashSuggestions —— 斜杠命令补全提示浮层
 * 命令补全浮层，中文标签
 */

import {Box, Text} from "ink";
import React from "react";
import type {SlashCommandSpec} from "./slash/types.js";
import {FG, TONE} from "./theme/tokens.js";

const COMMAND_NAME_CELLS = 14;
const ARGS_CELLS = 14;

export interface SlashSuggestionsProps {
    matches: readonly SlashCommandSpec[] | null;
    selectedIndex: number;
}

export function SlashSuggestions({
                                     matches,
                                     selectedIndex,
                                 }: SlashSuggestionsProps): React.ReactElement | null {
    if (matches === null) return null;

    if (matches.length === 0) {
        return (
            <Box paddingX={1} marginTop={1}>
                <Text color={TONE.warn} bold>⚠</Text>
                <Text> </Text>
                <Text color={TONE.warn}>无匹配命令</Text>
            </Box>
        );
    }

    return (
        <Box flexDirection="column" paddingX={1} marginTop={1} flexShrink={0}>
            {matches.map((spec, i) => (
                <Box key={spec.cmd} flexDirection="row">
                    <Box width={2}>
                        {i === selectedIndex ? <Text color={TONE.accent}>▸</Text> : null}
                    </Box>
                    <Box width={COMMAND_NAME_CELLS}>
                        <Text bold color={i === selectedIndex ? TONE.accent : FG.body}>
                            /{spec.cmd}
                        </Text>
                    </Box>
                    <Box width={ARGS_CELLS}>
                        <Text color={FG.meta}>
                            {spec.argsHint ?? ""}
                        </Text>
                    </Box>
                    <Text color={FG.sub}>{spec.summary}</Text>
                </Box>
            ))}
        </Box>
    );
}
