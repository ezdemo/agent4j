/** CardHeader — 卡片标题行：图标 + 标题 + 副标题 + 元信息 */

import {Box, Text} from "ink";
import React from "react";
import {FG} from "../theme/tokens.js";

export type MetaItem = string | { text: string; color: string };

export interface CardHeaderProps {
    glyph: string;
    tone: string;
    title: string;
    subtitle?: string;
    meta?: ReadonlyArray<MetaItem>;
    right?: React.ReactNode;
}

export function CardHeader({
                               glyph,
                               tone,
                               title,
                               subtitle,
                               meta,
                               right,
                           }: CardHeaderProps): React.ReactElement {
    return (
        <Box flexDirection="row" gap={1}>
            <Text color={tone}>{glyph}</Text>
            <Text bold color={tone}>
                {title}
            </Text>
            {subtitle ? <Text color={FG.body}>{subtitle}</Text> : null}
            {meta?.map((item, i) => {
                const isStr = typeof item === "string";
                const text = isStr ? item : item.text;
                const color = isStr ? FG.faint : item.color;
                return (
                    <Text key={i} color={color}>
                        {" · "}
                        {text}
                    </Text>
                );
            })}
            {right ? <Box>{right}</Box> : null}
        </Box>
    );
}
