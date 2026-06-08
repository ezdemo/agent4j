import type {Card, CardId} from "./cards.js";
import type {AgentEvent} from "./events.js";

export interface AgentState {
    cards: Card[];
    streamBuf: string;
    turnInProgress: boolean;
    /** 当前是否有活跃的流式 assistant 卡片 */
    streamingAssistant: boolean;
    /** 当前是否有活跃的流式 reasoning 卡片 */
    streamingReasoning: boolean;
    /** 当前等待用户处理的 HITL 卡片 ID */
    hitlPendingId: string | null;
}

let seq = 0;

function nextId(): CardId {
    seq += 1;
    return `c${seq}`;
}

export function initialState(): AgentState {
    return {
        cards: [],
        streamBuf: "",
        turnInProgress: false,
        streamingAssistant: false,
        streamingReasoning: false,
        hitlPendingId: null,
    };
}

export function reduce(state: AgentState, event: AgentEvent): AgentState {
    switch (event.type) {
        case "user.submit": {
            const card: Card = {
                id: nextId(),
                ts: Date.now(),
                kind: "user",
                text: event.text,
            };
            return {...state, cards: [...state.cards, card]};
        }

        case "assistant.start": {
            const card: Card = {
                id: nextId(),
                ts: Date.now(),
                kind: "assistant",
                text: "",
                done: false,
                streaming: true,
            };
            return {
                ...state,
                cards: [...state.cards, card],
                streamBuf: "",
                turnInProgress: true,
                streamingAssistant: true
            };
        }

        case "assistant.chunk": {
            const cards = [...state.cards];
            const last = cards[cards.length - 1];
            if (last?.kind !== "assistant") return state;
            const updated: Card = {...last, text: last.text + event.text};
            cards[cards.length - 1] = updated;
            return {...state, cards, streamBuf: state.streamBuf + event.text};
        }

        case "assistant.end": {
            const cards = [...state.cards];
            const last = cards[cards.length - 1];
            if (last?.kind !== "assistant") return state;
            const updated: Card = {
                ...last,
                done: true,
                streaming: false,
                ...(event.aborted ? {aborted: true} : {}),
            };
            cards[cards.length - 1] = updated;
            return {...state, cards, streamBuf: "", turnInProgress: false, streamingAssistant: false};
        }

        case "tool.start": {
            const card: Card = {
                id: nextId(),
                ts: Date.now(),
                kind: "tool",
                name: event.name,
                args: event.args,
                output: "",
                done: false,
                elapsedMs: 0,
            };
            return {...state, cards: [...state.cards, card], turnInProgress: true};
        }

        case "tool.end": {
            const cards = [...state.cards];
            const last = cards[cards.length - 1];
            if (last?.kind !== "tool") return state;
            const updated: Card = {...last, output: event.output, elapsedMs: event.elapsedMs, done: true};
            cards[cards.length - 1] = updated;
            return {...state, cards};
        }

        case "reasoning.start": {
            const card: Card = {
                id: nextId(),
                ts: Date.now(),
                kind: "reasoning",
                text: "",
                done: false,
            };
            return {...state, cards: [...state.cards, card], streamingReasoning: true};
        }

        case "reasoning.chunk": {
            const cards = [...state.cards];
            const last = cards[cards.length - 1];
            if (last?.kind !== "reasoning") return state;
            const updated: Card = {...last, text: last.text + event.text};
            cards[cards.length - 1] = updated;
            return {...state, cards};
        }

        case "reasoning.end": {
            const cards = [...state.cards];
            const last = cards[cards.length - 1];
            if (last?.kind !== "reasoning") return state;
            const updated: Card = {
                ...last,
                done: true,
                ...(event.aborted ? {aborted: true} : {}),
            };
            cards[cards.length - 1] = updated;
            return {...state, cards, streamingReasoning: false};
        }

        case "system.message": {
            const card: Card = {
                id: nextId(),
                ts: Date.now(),
                kind: "system",
                text: event.text,
                tone: event.tone,
            };
            return {...state, cards: [...state.cards, card]};
        }

        // ── HITL ─────────────────────────────────────────────
        case "hitl.request": {
            const id = nextId();
            const card: Card = {
                id,
                ts: Date.now(),
                kind: "hitl",
                toolName: event.toolName,
                command: event.command,
                resolved: false,
            };
            return {...state, cards: [...state.cards, card], hitlPendingId: id, turnInProgress: true};
        }

        case "hitl.resolve": {
            const cards = [...state.cards];
            const idx = cards.findIndex((c) => c.id === state.hitlPendingId);
            if (idx === -1) return state;
            const hitl = cards[idx] as Card & { kind: "hitl" };
            if (hitl.kind !== "hitl") return state;
            cards[idx] = {...hitl, resolved: true, response: event.response};
            return {...state, cards, hitlPendingId: null};
        }

        case "session.restore":
            return {...initialState(), cards: event.cards, turnInProgress: false};

        case "clear":
            return initialState();

        default:
            return state;
    }
}
