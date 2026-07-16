# Continue to New Session Design

## Goal

Allow a user to continue from an assistant response in a new session while preserving the complete original assistant turn. The branched session must include reasoning, tool calls, tool results, and final assistant content exactly as stored.

## User Experience

- Show the "continue to new session" action only on assistant messages.
- Do not show the action on user messages.
- After a successful branch, switch the UI to the new session and refresh the session list.
- Show the existing success or failure notification for the operation.

## Data Flow

The frontend renders one assistant bubble from several persisted `ChatMessage` records. A rendered message index therefore cannot be used as the persisted message count.

While converting raw history into rendered messages, the frontend records `sourceMessageCount` on each assistant bubble. This value is the exclusive end position in the raw history and is updated for every assistant or tool record belonging to that bubble. Clicking the action sends this exact count to the existing branch endpoint.

For a newly streamed assistant message that does not yet have `sourceMessageCount`, the frontend reloads persisted history, resolves the corresponding assistant bubble, and then branches. The backend continues to copy the original `ChatMessage` objects with `subList`, so no reasoning or tool fields are reconstructed by the frontend.

## Error Handling

- Ignore the action when the session or workspace is unavailable.
- Reject an unresolved or invalid source boundary rather than creating a partial branch.
- Preserve the current session when the branch request fails.
- Prevent duplicate branch requests while one is in progress.

## Scope

The implementation will also fix the malformed `.msg-actions` CSS placement introduced by the current uncommitted feature work. It will not change general history rendering, session naming, or message persistence.

## Verification

- A user message has no branch action.
- A plain assistant response branches through its final raw message.
- An assistant turn containing reasoning, tool calls, tool results, and final content is copied completely.
- Branching from an earlier assistant turn excludes later user and assistant turns.
- A newly streamed assistant response can be branched after persistence completes.
- Frontend build and focused backend tests pass.
