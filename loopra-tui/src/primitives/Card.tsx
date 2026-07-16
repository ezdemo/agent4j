/** Card 容器 — 单张卡片的包裹层，提供统一间距 */

import {Box} from "ink";
import React from "react";

export interface CardProps {
    /** 卡片色调（保留给 CardHeader 使用，不再驱动左边框） */
    tone?: string;
    children: React.ReactNode;
}

export function Card({children}: CardProps): React.ReactElement {
    return (
        <Box flexDirection="column" marginTop={1} width="100%">
            {children}
        </Box>
    );
}
