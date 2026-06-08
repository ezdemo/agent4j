/**
 * agent4j-tui 应用入口
 *
 * 启动方式：
 *   npx tsx app/index.tsx --api http://localhost:8097
 */

import React from "react";
import {render} from "ink";
import {KeystrokeProvider} from "../src/input/keystroke-context.js";
import {ScrollProvider} from "../src/layout/scroll-provider.js";
import {StoreProvider} from "../src/store/provider.js";
import {ThemeProvider} from "../src/theme/context.js";
import {Agent4jApp} from "../src/Agent4jApp.js";

// 解析命令行参数
const args = process.argv.slice(2);
const apiUrlIndex = args.indexOf("--api");
const apiUrl = apiUrlIndex !== -1 && args[apiUrlIndex + 1]
    ? args[apiUrlIndex + 1]!
    : "http://localhost:8097";

const workspaceIndex = args.indexOf("--workspace");
const workspaceHash = workspaceIndex !== -1 && args[workspaceIndex + 1]
    ? args[workspaceIndex + 1]
    : undefined;

const tokenIndex = args.indexOf("--token");
const token = tokenIndex !== -1 && args[tokenIndex + 1]
    ? args[tokenIndex + 1]
    : undefined;

console.error(`🔌 连接到 agent4j 后端: ${apiUrl}`);
if (workspaceHash) console.error(`📁 工作区: ${workspaceHash}`);

function Main(): React.ReactElement {
    return (
        <ThemeProvider theme="default">
            <KeystrokeProvider>
                <StoreProvider>
                    <ScrollProvider>
                        <Agent4jApp
                            apiUrl={apiUrl}
                            workspaceHash={workspaceHash}
                            token={token}
                        />
                    </ScrollProvider>
                </StoreProvider>
            </KeystrokeProvider>
        </ThemeProvider>
    );
}

const {waitUntilExit} = render(<Main/>);
await waitUntilExit();
