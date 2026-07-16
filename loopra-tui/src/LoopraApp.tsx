/**
 * LoopraApp —— 连接 loopra 后端的终端 AI 代理界面
 *
 * 通过 HTTP REST + SSE 流式桥接 loopra 后端的所有能力：
 * - 流式对话（reasoning → action → text）
 * - 会话管理（CRUD）
 * - 模型查询
 * - HITL 人机交互
 * - 斜杠命令
 */

import {Box, Text, useStdout} from "ink";
import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {ApiClient, ApiError} from "./api/client.js";
import type {CommandItem, ModelItem, SessionItem, SseEvent} from "./api/types.js";
import type {StreamParams} from "./api/sse.js";
import {SseClient} from "./api/sse.js";
import type {ToolGroupData} from "./cards/CardRenderer.js";
import {CardRenderer, ToolGroupCard} from "./cards/CardRenderer.js";
import type {StatusPart} from "./ComposerArea.js";
import {ComposerArea} from "./ComposerArea.js";
import {useKeystroke} from "./input/keystroke-context.js";
import {CardStream} from "./layout/CardStream.js";
import {useScrollActions} from "./layout/scroll-provider.js";
import {handleSlash, parseSlash, SLASH_COMMANDS} from "./slash/index.js";
import type {SlashCommandSpec} from "./slash/types.js";
import {useAgentState, useDispatch} from "./store/provider.js";
import type {AgentEvent} from "./store/events.js";
import type {Card} from "./store/cards.js";
import {FG, setActiveTheme, type ThemeName, THEMES, TONE} from "./theme/tokens.js";

// ── Props ────────────────────────────────────────────────────

export interface LoopraAppProps {
    /** loopra 后端地址，如 http://localhost:8097 */
    apiUrl: string;
    /** 可选的工作区哈希 */
    workspaceHash?: string;
    /** 可选的认证令牌 */
    token?: string;
}

// ── SSE Chunk → Store 事件映射 ───────────────────────────────

function handleSseEvent(
    event: SseEvent,
    dispatch: (ev: AgentEvent) => void,
    streamingRef: React.MutableRefObject<{ reason: boolean; assistant: boolean }>,
): void {
    const type = event.type;

    switch (type) {
        case "reason":
        case "reasoning": {
            if (!streamingRef.current.reason) {
                streamingRef.current.reason = true;
                dispatch({type: "reasoning.start"});
            }
            dispatch({type: "reasoning.chunk", text: event.text ?? event.content ?? ""});
            break;
        }

        case "text":
        case "message":
        case "content": {
            if (!streamingRef.current.assistant) {
                streamingRef.current.assistant = true;
                dispatch({type: "assistant.start"});
            }
            dispatch({type: "assistant.chunk", text: event.content ?? event.text ?? ""});
            break;
        }

        case "action":
        case "tool_start":
        case "tool_call": {
            const toolName = event.toolName ?? event.name ?? "unknown";
            const args = event.args ?? {};
            dispatch({type: "tool.start", name: toolName, args});
            break;
        }

        case "tool_end":
        case "tool_result": {
            const raw = event.output ?? event.result ?? event.content ?? "";
            const output = typeof raw === "string" ? raw : String(raw);
            const elapsedMs = typeof event.elapsedMs === "number" ? event.elapsedMs : 0;
            dispatch({type: "tool.end", output, elapsedMs});
            break;
        }

        case "command": {
            dispatch({type: "tool.start", name: event.toolName ?? "command", args: {command: event.command}});
            dispatch({type: "tool.end", output: event.content ?? "", elapsedMs: 0});
            break;
        }

        case "hitl": {
            dispatch({
                type: "hitl.request",
                toolName: event.toolName ?? "unknown",
                command: event.command,
            });
            break;
        }

        case "error": {
            const msg = event.message ?? event.content ?? "发生错误";
            dispatch({type: "system.message", text: msg, tone: "err"});
            break;
        }

        case "done": {
            // 结束所有进行中的流式卡片
            if (streamingRef.current.reason) {
                dispatch({type: "reasoning.end", aborted: !!event.aborted});
                streamingRef.current.reason = false;
            }
            if (streamingRef.current.assistant) {
                dispatch({type: "assistant.end", aborted: !!event.aborted});
                streamingRef.current.assistant = false;
            }
            break;
        }

        case "rewind": {
            // 回退：清除最后一张卡片
            dispatch({type: "system.message", text: "正在重做...", tone: "info"});
            break;
        }

        default: {
            // 未知事件类型，尝试作为文本处理
            if (event.content) {
                if (!streamingRef.current.assistant) {
                    streamingRef.current.assistant = true;
                    dispatch({type: "assistant.start"});
                }
                dispatch({type: "assistant.chunk", text: event.content});
            }
        }
    }
}

/** 将 History MessageItem[] 转换为 Card[] */
function buildCardsFromMessages(messages: Array<{
    role: string;
    content: string;
    toolName?: string;
    toolArgs?: unknown;
    createdAt?: string
}>): Card[] {
    const cards: Card[] = [];
    let seq = 0;
    for (const msg of messages) {
        seq++;
        const id = `h${seq}`;
        const ts = msg.createdAt ? new Date(msg.createdAt).getTime() : Date.now();
        switch (msg.role) {
            case "user":
                cards.push({id, ts, kind: "user", text: msg.content});
                break;
            case "assistant":
                cards.push({id, ts, kind: "assistant", text: msg.content, done: true, streaming: false});
                break;
            case "reasoning":
                cards.push({id, ts, kind: "reasoning", text: msg.content, done: true});
                break;
            case "tool":
                cards.push({
                    id, ts, kind: "tool",
                    name: msg.toolName ?? "unknown",
                    args: msg.toolArgs ?? {},
                    output: msg.content,
                    done: true,
                    elapsedMs: 0,
                });
                break;
            case "system":
                cards.push({id, ts, kind: "system", text: msg.content, tone: "info"});
                break;
        }
    }
    return cards;
}

/** 将连续 tool 卡片分组为虚拟 tool_group 卡片 */
let _tgSeq = 0;
type RenderableItem = Card | ToolGroupData;

function groupConsecutiveTools(cards: readonly Card[]): RenderableItem[] {
    const out: RenderableItem[] = [];
    let i = 0;
    while (i < cards.length) {
        const c = cards[i]!;
        if (c.kind === "tool") {
            const group: Array<Card & { kind: "tool" }> = [c as Card & { kind: "tool" }];
            let j = i + 1;
            while (j < cards.length && cards[j]!.kind === "tool") {
                group.push(cards[j]! as Card & { kind: "tool" });
                j++;
            }
            if (group.length >= 2) {
                _tgSeq++;
                out.push({
                    kind: "tool_group",
                    id: `tg-${_tgSeq}`,
                    tools: group,
                    allDone: group.every((t) => t.done),
                    running: group.some((t) => !t.done),
                });
            } else {
                out.push(c);
            }
            i = j;
        } else {
            out.push(c);
            i++;
        }
    }
    return out;
}

// ── 主组件 ────────────────────────────────────────────────────

export function LoopraApp({apiUrl, workspaceHash: defaultWorkspace, token}: LoopraAppProps): React.ReactElement {
    const dispatch = useDispatch();
    const cards = useAgentState((s) => s.cards);
    const hitlPendingId = useAgentState((s) => s.hitlPendingId);
    const streamingAssistant = useAgentState((s) => s.streamingAssistant);
    const streamingReasoning = useAgentState((s) => s.streamingReasoning);

    const {jumpToBottom} = useScrollActions();

    // ── API 客户端（ref 保持稳定引用）─────────────────────────
    const apiRef = useRef<ApiClient>(null!);
    const sseRef = useRef<SseClient>(null!);
    if (apiRef.current == null) {
        apiRef.current = new ApiClient({baseURL: apiUrl, token});
    }
    if (sseRef.current == null) {
        sseRef.current = new SseClient(apiUrl);
    }

    // ── 连接状态 ───────────────────────────────────────────────
    const [connected, setConnected] = useState(false);
    const [appTitle, setAppTitle] = useState("Loopra");
    const [sessions, setSessions] = useState<SessionItem[]>([]);
    const [models, setModels] = useState<ModelItem[]>([]);
    const [backendCommands, setBackendCommands] = useState<CommandItem[]>([]);
    const [workspace, setWorkspace] = useState(defaultWorkspace ?? "");
    const sessionNameRef = useRef(`session-${Date.now().toString(36)}`);

    // ── 输入状态 ───────────────────────────────────────────────
    const [input, setInput] = useState("");
    const [history, setHistory] = useState<string[]>([]);
    const [historyIdx, setHistoryIdx] = useState(-1);
    const [slashSelected, setSlashSelected] = useState(0);
    const [themeName, setThemeName] = useState<ThemeName>("default");
    const [expandedCards, setExpandedCards] = useState<Record<string, boolean>>({});

    // ── 初始化 SSE + 加载数据 ────────────────────────────────
    useEffect(() => {
        const sse = sseRef.current;
        const api = apiRef.current;

        // 注册全局 SSE handler（使用默认会话，确保事件始终可达）
        const unsub = sse.subscribe(SseClient.DEFAULT_SESSION, (event: SseEvent) => {
            handleSseEvent(event, dispatch, streamingRef);
        });

        // 连接状态监听
        sse.connect();

        // 加载初始数据
        Promise.all([
            api.getAgentInfo().then((r) => {
                if (r.success && r.data) {
                    setAppTitle(r.data.name);
                    setWorkspace(r.data.workspace ?? defaultWorkspace ?? "");
                }
            }).catch(() => {
            }),
            api.listSessions(workspace).then((r) => {
                if (r.success && r.data) setSessions(r.data);
            }).catch(() => {
            }),
            api.listModels().then((r) => {
                if (r.success && r.data) {
                    // 后端返回 { current, models: [{name, active}] }
                    const raw = r.data as unknown as {
                        current?: string;
                        models?: Array<{ name: string; active?: boolean }>
                    };
                    const list = raw.models ?? (Array.isArray(r.data) ? r.data as unknown as ModelItem[] : []);
                    const mapped: ModelItem[] = list.map((m: {
                        name?: string;
                        id?: string;
                        description?: string;
                        provider?: string;
                        capabilities?: string[];
                        maxTokens?: number
                    }) => ({
                        id: m.name ?? m.id ?? '?',
                        name: m.name ?? m.id ?? '?',
                        provider: m.provider ?? '',
                        description: m.description ?? '',
                        capabilities: m.capabilities ?? [],
                        maxTokens: m.maxTokens ?? 0,
                    }));
                    setModels(mapped);
                }
            }).catch(() => {
            }),
            api.getCommands().then((r) => {
                if (r.success && r.data) setBackendCommands(r.data);
            }).catch(() => {
            }),
            api.healthCheck().then(() => setConnected(true)).catch(() => setConnected(false)),
        ]);

        return () => {
            unsub();
        };
    }, [apiUrl, dispatch, workspace, defaultWorkspace]);

    // ── 新消息到达时自动滚底 ──────────────────────────────────
    useEffect(() => {
        jumpToBottom();
    }, [cards.length, jumpToBottom]);

    // ── SseEvent → Store 事件 ──────────────────────────────────
    const streamingRef = useRef({reason: false, assistant: false});
    streamingRef.current.reason = streamingReasoning;
    streamingRef.current.assistant = streamingAssistant;

    // ── 卡片展开/收起 ─────────────────────────────────────────
    const expandedRef = useRef(expandedCards);
    expandedRef.current = expandedCards;

    // ── 连续工具分组 ─────────────────────────────────────────
    const groupedCards = useMemo(() => groupConsecutiveTools(cards), [cards]);

    const stableRenderCard = useCallback(
        (item: RenderableItem) => {
            if (item.kind === "tool_group") {
                const groupExpanded = expandedRef.current[item.id] ?? false;
                return (
                    <ToolGroupCard
                        group={item}
                        expanded={groupExpanded}
                        expandedTools={expandedRef.current}
                        onToggleGroup={() => {
                            setExpandedCards((prev) => ({...prev, [item.id]: !prev[item.id]}));
                        }}
                        onToggleTool={(toolId: string) => {
                            setExpandedCards((prev) => ({...prev, [toolId]: !prev[toolId]}));
                        }}
                    />
                );
            }
            const card = item as Card;
            const expanded = expandedRef.current[card.id] ?? (card.kind === "reasoning");
            const toggleable = card.kind === "reasoning" || card.kind === "tool";
            return (
                <CardRenderer
                    card={card}
                    expanded={expanded}
                    onToggle={toggleable ? () => {
                        setExpandedCards((prev) => ({...prev, [card.id]: !prev[card.id]}));
                    } : undefined}
                />
            );
        },
        [],
    );

    // ── 键盘 e 展开最后一个可折叠卡片 ─────────────────────────
    const lastCollapsible = useMemo(() => {
        for (let i = groupedCards.length - 1; i >= 0; i--) {
            const c = groupedCards[i];
            if (c && (c.kind === "reasoning" || c.kind === "tool" || c.kind === "tool_group")) return c.id;
        }
        return null;
    }, [groupedCards]);

    useKeystroke((ev) => {
        if (ev.input === "e" && !ev.ctrl && !ev.meta && lastCollapsible) {
            setExpandedCards((prev) => ({...prev, [lastCollapsible]: !prev[lastCollapsible]}));
        }
    }, true);

    // ── 鼠标点击展开/收起 ─────────────────────────────────────
    useKeystroke((ev) => {
        if (!ev.mouseClick || !ev.mouseRow) return;
        let cursor = 1;
        for (const c of groupedCards) {
            if (ev.mouseRow >= cursor - 1 && ev.mouseRow <= cursor + 3) {
                if (c.kind === "reasoning" || c.kind === "tool" || c.kind === "tool_group") {
                    setExpandedCards((prev) => ({...prev, [c.id]: !prev[c.id]}));
                }
                break;
            }
            cursor += 4;
        }
    }, true);

    // ── 斜杠命令匹配 ──────────────────────────────────────────
    const dynamicSlashCommands = useMemo<SlashCommandSpec[]>(() => {
        const list: SlashCommandSpec[] = [...SLASH_COMMANDS];
        for (const cmd of backendCommands) {
            if (!cmd.name) continue;
            const exists = SLASH_COMMANDS.some((s) => s.cmd === cmd.name || s.aliases?.includes(cmd.name));
            if (!exists) {
                list.push({
                    cmd: cmd.name,
                    group: "chat" as const,
                    summary: cmd.description,
                });
            }
        }
        return list;
    }, [backendCommands]);

    const slashMatches = useMemo(() => {
        if (!input.startsWith("/")) return null;
        const parts = input.slice(1).split(/\s+/);
        const prefix = parts[0] ?? "";
        if (parts.length > 1) return null;
        const lower = prefix.toLowerCase();
        return dynamicSlashCommands.filter(
            (s) => s.cmd?.startsWith(lower) || s.aliases?.some((a) => a.startsWith(lower)),
        ).slice(0, 24);
    }, [input, dynamicSlashCommands]);

    useEffect(() => {
        setSlashSelected(0);
    }, [slashMatches?.length]);

    // ── 交互选择器（模型 / 会话） ────────────────────────────
    const pickerMode = useMemo<{ type: "model" | "session"; items: string[] } | null>(() => {
        if (!input.startsWith("/")) return null;
        const spaceIdx = input.indexOf(" ");
        if (spaceIdx === -1) return null;
        const cmd = input.slice(1, spaceIdx);
        if (cmd === "model" || cmd === "models") {
            return {type: "model", items: models.map((m) => `${m.name ?? m.id}  - ${m.description}`)};
        }
        if (cmd === "session") {
            return {
                type: "session",
                items: sessions.map((s) => `${s.name.slice(0, 16)}…  ${(s.displayName ?? s.name).slice(0, 40)}`),
            };
        }
        return null;
    }, [input, models, sessions]);

    const [pickerIdx, setPickerIdx] = useState(0);
    useEffect(() => {
        setPickerIdx(0);
    }, [pickerMode?.type]);

    const pickerActive = pickerMode !== null;

    // ── Tab 补全参数 ────────────────────────────────────────
    const doTabComplete = useCallback(() => {
        if (!input.startsWith("/")) return;
        const spaceIdx = input.indexOf(" ");
        if (spaceIdx === -1) return;
        const cmd = input.slice(1, spaceIdx);
        const arg = input.slice(spaceIdx + 1);
        const lower = arg.toLowerCase();

        if (cmd === "model" || cmd === "models") {
            if (models.length === 0) return;
            const current = models[pickerIdx];
            if (current && (current.name ?? current.id).toLowerCase().startsWith(lower)) {
                setInput(`/${cmd} ${current.name ?? current.id}`);
            } else {
                const match = models.find((m) => (m.name ?? m.id).toLowerCase().startsWith(lower));
                if (match) {
                    setInput(`/${cmd} ${match.name ?? match.id}`);
                } else if (!arg) {
                    setInput(`/${cmd} ${models[0]!.name ?? models[0]!.id}`);
                }
            }
        } else if (cmd === "session") {
            if (sessions.length === 0) return;
            const current = sessions[pickerIdx];
            if (current && current.name.toLowerCase().startsWith(lower)) {
                setInput(`/${cmd} ${current.name}`);
            } else {
                const match = sessions.find((s) => s.name.toLowerCase().startsWith(lower));
                if (match) {
                    setInput(`/${cmd} ${match.name}`);
                } else if (!arg) {
                    setInput(`/${cmd} ${sessions[0]!.name}`);
                }
            }
        }
    }, [input, models, sessions, pickerIdx]);

    // ── Enter 执行选择 ──────────────────────────────────────
    const doPickerEnter = useCallback(() => {
        if (!pickerMode) return;
        const spaceIdx = input.indexOf(" ");
        const arg = spaceIdx >= 0 ? input.slice(spaceIdx + 1).trim().toLowerCase() : "";

        if (pickerMode.type === "model") {
            const current = models[pickerIdx];
            const modelName = current?.name ?? current?.id;
            if (modelName) {
                dispatch({type: "system.message", text: `已选择模型 ${modelName}`, tone: "ok"});
            }
        } else if (pickerMode.type === "session") {
            const current = sessions[pickerIdx];
            if (current) {
                switchToSession(current.name);
            }
        }
        setInput("");
    }, [input, pickerMode, models, sessions, pickerIdx, dispatch]);

    // ── 键盘导航（命令补全 + 选择器） ─────────────────────────
    const slashActive = !pickerActive && slashMatches && input.startsWith("/") && !input.includes(" ");
    useKeystroke((ev) => {
        if (pickerActive && pickerMode) {
            if (ev.upArrow) {
                setPickerIdx((p) => Math.max(0, p - 1));
                return;
            }
            if (ev.downArrow) {
                setPickerIdx((p) => Math.min(pickerMode.items.length - 1, p + 1));
                return;
            }
            if (ev.tab) {
                doTabComplete();
                return;
            }
            if (ev.return && !ev.shift) {
                doPickerEnter();
                return;
            }
            if (ev.escape) {
                setInput("");
                return;
            }
            return;
        }

        if (!slashActive || !slashMatches?.length) return;
        if (ev.upArrow) {
            setSlashSelected((p) => Math.max(0, p - 1));
            return;
        }
        if (ev.downArrow) {
            setSlashSelected((p) => Math.min(slashMatches.length - 1, p + 1));
            return;
        }
        if (ev.tab || (ev.return && !ev.shift)) {
            const sel = slashMatches[slashSelected];
            if (sel) {
                setInput(`/${sel.cmd} `);
            }
            return;
        }
        if (ev.escape) {
            setInput("");
            return;
        }
    }, true);

    // ── Esc 打断生成 ──────────────────────────────────────────
    useKeystroke((ev) => {
        if (!ev.escape) return;
        if (input.trim() !== "") return;
        if (!streamingAssistant && !streamingReasoning) return;

        const api = apiRef.current;
        api.abort(workspace, sessionNameRef.current).catch((err) => console.warn("abort failed:", err));
        if (streamingRef.current.reason) {
            dispatch({type: "reasoning.end", aborted: true});
            streamingRef.current.reason = false;
        }
        if (streamingRef.current.assistant) {
            dispatch({type: "assistant.end", aborted: true});
            streamingRef.current.assistant = false;
        }
        dispatch({type: "system.message", text: "已打断生成", tone: "warn"});
    }, true);

    // ── 会话切换 ──────────────────────────────────────────────
    const switchToSession = useCallback((newName: string) => {
        if (newName === sessionNameRef.current && sessions.some((s) => s.name === newName)) return;

        sessionNameRef.current = newName;
        const api = apiRef.current;
        api.switchSession(newName).catch(() => {
        });

        dispatch({type: "clear"});
        api.getAgentHistory(workspace, newName).then((r) => {
            if (r.success && r.data) {
                const cards = buildCardsFromMessages(r.data);
                dispatch({type: "session.restore", cards});
                dispatch({
                    type: "system.message",
                    text: `已切换到会话 ${newName.slice(0, 12)}… (${cards.length} 条)`,
                    tone: "info"
                });
            }
        }).catch(() => {
            dispatch({type: "system.message", text: "加载会话失败", tone: "err"});
        });

        api.listSessions(workspace).then((r) => {
            if (r.success && r.data) setSessions(r.data);
        }).catch(() => {
        });
    }, [dispatch, workspace]);

    // ── 自动加载最近会话 ──────────────────────────────────────
    useEffect(() => {
        const api = apiRef.current;
        api.listSessions(workspace).then((r) => {
            if (!r.success || !r.data) return;
            setSessions(r.data);
            if (r.data.length > 0) {
                const latest = r.data[0]!;
                if (latest.name !== sessionNameRef.current) {
                    sessionNameRef.current = latest.name;
                    api.getAgentHistory(workspace, latest.name).then((hr) => {
                        if (hr.success && hr.data) {
                            const cards = buildCardsFromMessages(hr.data);
                            dispatch({type: "session.restore", cards});
                        }
                    }).catch(() => {
                    });
                }
            }
        }).catch(() => {
        });
    }, [workspace]);

    // ── loopra 专用斜杠命令 ─────────────────────────────────
    const handleLoopraSlash = useCallback((cmd: string, args: string[]): boolean => {
        switch (cmd) {
            case "new":
            case "clear":
            case "reset": {
                const newName = `session-${Date.now().toString(36)}`;
                sessionNameRef.current = newName;
                dispatch({type: "clear"});
                dispatch({type: "system.message", text: "已开始新会话", tone: "ok"});
                apiRef.current.createSession(workspace, newName).catch(() => {
                });
                apiRef.current.listSessions(workspace).then((r) => {
                    if (r.success && r.data) setSessions(r.data);
                }).catch(() => {
                });
                return true;
            }
            case "model": {
                const name = args[0];
                if (!name) {
                    const list = models.map((m) => `  ○ ${m.name ?? m.id} - ${m.description}`).join("\n");
                    dispatch({type: "system.message", text: `可用模型:\n${list}`, tone: "info"});
                    return true;
                }
                dispatch({type: "system.message", text: `已选择模型 ${name}`, tone: "ok"});
                return true;
            }
            case "models": {
                const list = models.map((m) => `  ○ ${m.name ?? m.id} - ${m.description}`).join("\n");
                dispatch({type: "system.message", text: `可用模型 (${models.length}):\n${list}`, tone: "info"});
                return true;
            }
            case "session": {
                const name = args[0];
                if (!name) {
                    const list = sessions.map((s) => `  ${s.name.slice(0, 16)}…  ${(s.displayName ?? s.name).slice(0, 40)}`).join("\n");
                    dispatch({type: "system.message", text: `会话列表 (${sessions.length}):\n${list}`, tone: "info"});
                    return true;
                }
                switchToSession(name);
                return true;
            }
            case "sessions": {
                const list = sessions.map((s) => `  ${s.name.slice(0, 16)}…  ${(s.displayName ?? s.name).slice(0, 40)}`).join("\n");
                dispatch({type: "system.message", text: `会话列表 (${sessions.length}):\n${list}`, tone: "info"});
                return true;
            }
            case "interrupt":
            case "stop": {
                apiRef.current.abort(workspace, sessionNameRef.current).catch(() => {
                });
                if (streamingRef.current.reason) {
                    dispatch({type: "reasoning.end", aborted: true});
                    streamingRef.current.reason = false;
                }
                if (streamingRef.current.assistant) {
                    dispatch({type: "assistant.end", aborted: true});
                    streamingRef.current.assistant = false;
                }
                dispatch({type: "system.message", text: "已中断生成", tone: "warn"});
                return true;
            }
            default:
                return false;
        }
    }, [models, sessions, dispatch, workspace, switchToSession]);

    // ── HITL 辅助 ──────────────────────────────────────────────
    const hasHitlPending = useCallback(() => hitlPendingId !== null, [hitlPendingId]);
    const approveHitl = useCallback(() => {
        if (!hitlPendingId) return;
        dispatch({type: "hitl.resolve", response: "approve"});
        apiRef.current.sendMessage("continue", workspace, sessionNameRef.current).catch(() => {
        });
    }, [hitlPendingId, dispatch, workspace]);
    const denyHitl = useCallback(() => {
        if (!hitlPendingId) return;
        dispatch({type: "hitl.resolve", response: "deny"});
        apiRef.current.sendMessage("deny", workspace, sessionNameRef.current).catch(() => {
        });
    }, [hitlPendingId, dispatch, workspace]);

    // ── 斜杠命令上下文 ─────────────────────────────────────────
    const slashCtx = useMemo(() => ({
        clearAll: () => dispatch({type: "clear"}),
        getLastUserMessage: (): string | null => {
            for (let i = cards.length - 1; i >= 0; i--) {
                const c = cards[i];
                if (c?.kind === "user") return c.text;
            }
            return null;
        },
        setTheme: (name: ThemeName) => {
            setActiveTheme(THEMES[name] ?? THEMES.default);
            setThemeName(name);
        },
        hasHitlPending,
        approveHitl,
        denyHitl,
    }), [dispatch, cards, hasHitlPending, approveHitl, denyHitl]);

    // ── 提交处理 ──────────────────────────────────────────────
    const handleSubmit = useCallback((text: string) => {
        const slash = parseSlash(text);
        if (slash) {
            const handled = handleLoopraSlash(slash.cmd, slash.args);
            if (handled) {
                setInput("");
                return;
            }

            const result = handleSlash(slash.cmd, slash.args, slashCtx);
            if (result.exit) {
                process.exit(0);
                return;
            }
            if (result.info) {
                dispatch({type: "system.message", text: result.info, tone: "info"});
            }
            if (result.resubmit) {
                setInput(result.resubmit);
            } else {
                setInput("");
            }
            return;
        }

        // 正常消息 → 通过 SSE 发送到后端
        dispatch({type: "user.submit", text});
        setInput("");
        setHistory((prev) => [...prev, text]);
        setHistoryIdx(-1);
        const sse = sseRef.current;
        const params: StreamParams = {
            message: text,
            workspaceHash: workspace || undefined,
            sessionName: sessionNameRef.current,
        };
        sse.startStream(params, sessionNameRef.current).catch((err: unknown) => {
            const msg = err instanceof ApiError ? err.message : String(err);
            dispatch({type: "system.message", text: `发送失败: ${msg}`, tone: "err"});
        });

        // 刷新会话列表
        apiRef.current.listSessions(workspace).then((r) => {
            if (r.success && r.data) setSessions(r.data);
        }).catch(() => {
        });
    }, [dispatch, workspace, slashCtx, handleLoopraSlash]);

    // ── 获取后端命令列表 ─────────────────────────────────────
    useEffect(() => {
        apiRef.current.getCommands().then((r) => {
            if (r.success && r.data) setBackendCommands(r.data);
        }).catch(() => {
        });
    }, []);

    // ── 状态栏右侧信息 ────────────────────────────────────────
    const statusRight = useMemo<StatusPart[]>(() => {
        const parts: StatusPart[] = [];
        if (workspace) {
            parts.push({text: `${workspace.slice(0, 8)}…`, color: FG.meta});
        }
        if (!connected) {
            parts.push({text: "● 离线", color: TONE.err});
        }
        return parts;
    }, [workspace, connected]);

    const {stdout} = useStdout();
    const terminalRows = stdout?.rows ?? 40;

    return (
        <Box flexDirection="column" height={terminalRows}>
            {/* 标题栏 */}
            <Box height={1} flexShrink={0}>
                <Text bold color={TONE.brand}>
                    {appTitle}
                </Text>
                <Text color={FG.faint}>
                    {` · ${connected ? "已连接" : "未连接"}`}
                </Text>
            </Box>

            {/* 卡片流 */}
            <CardStream
                cards={groupedCards}
                renderCard={stableRenderCard}
            />

            {/* 底部输入区 */}
            <ComposerArea
                input={input}
                setInput={setInput}
                onSubmit={handleSubmit}
                onHistoryPrev={() => {
                    if (history.length === 0) return;
                    const newIdx = Math.min(history.length - 1, historyIdx + 1);
                    setHistoryIdx(newIdx);
                    setInput(history[history.length - 1 - newIdx] ?? "");
                }}
                onHistoryNext={() => {
                    if (historyIdx <= 0) {
                        setHistoryIdx(-1);
                        setInput("");
                        return;
                    }
                    const newIdx = historyIdx - 1;
                    setHistoryIdx(newIdx);
                    setInput(history[history.length - 1 - newIdx] ?? "");
                }}
                slashMatches={slashMatches}
                slashSelected={slashSelected}
                statusRight={statusRight}
            />
        </Box>
    );
}
