# Continue to New Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Branch from an assistant bubble into a new session without losing any persisted reasoning, tool calls, tool results, or final content.

**Architecture:** A small frontend utility maps persisted `ChatMessage` records to assistant-turn end offsets. `Chat.vue` attaches or resolves that offset before calling the existing branch endpoint, while `ChatMessage.vue` exposes the action only for assistant messages. The backend validates the exact offset and writes the copied records through a separate session store so the source agent remains bound to the source session.

**Tech Stack:** Vue 3, Vitest, Java 17, Solon, JUnit 5, JSONL session storage

---

### Task 1: Persisted Assistant-Turn Boundaries

**Files:**
- Create: `loopra-front/src/utils/sessionBranch.js`
- Create: `loopra-front/src/utils/sessionBranch.test.js`

- [ ] **Step 1: Write failing boundary tests**

```js
import {describe, expect, it} from 'vitest'
import {getAssistantTurnBoundaries} from './sessionBranch'

describe('getAssistantTurnBoundaries', () => {
  it('returns the exclusive raw end offset for a plain assistant reply', () => {
    expect(getAssistantTurnBoundaries([
      {role: 'user', content: 'one'},
      {role: 'assistant', content: 'answer'}
    ])).toEqual([2])
  })

  it('keeps reasoning, tool calls, tool results, and final content in one turn', () => {
    expect(getAssistantTurnBoundaries([
      {role: 'user', content: 'inspect'},
      {role: 'assistant', reasoning_content: 'thinking', tool_calls: [{id: '1'}]},
      {role: 'tool', tool_call_id: '1', content: 'result'},
      {role: 'assistant', content: 'done'},
      {role: 'user', content: 'next'},
      {role: 'assistant', content: 'later'}
    ])).toEqual([4, 6])
  })
})
```

- [ ] **Step 2: Run the test and confirm the missing-module failure**

Run: `pnpm vitest run src/utils/sessionBranch.test.js`

Expected: FAIL because `sessionBranch.js` does not exist.

- [ ] **Step 3: Implement the boundary utility**

```js
export const getAssistantTurnBoundaries = (rawHistory = []) => {
  const boundaries = []
  let activeAssistantTurn = -1

  rawHistory.forEach((message, index) => {
    if (message.role === 'user') {
      activeAssistantTurn = -1
      return
    }
    if (message.role === 'assistant' && activeAssistantTurn === -1) {
      activeAssistantTurn = boundaries.length
      boundaries.push(index + 1)
      return
    }
    if (activeAssistantTurn >= 0 && (message.role === 'assistant' || message.role === 'tool')) {
      boundaries[activeAssistantTurn] = index + 1
    }
  })

  return boundaries
}
```

- [ ] **Step 4: Run the focused test**

Run: `pnpm vitest run src/utils/sessionBranch.test.js`

Expected: 2 tests pass.

### Task 2: Assistant-Only Branch Action

**Files:**
- Create: `loopra-front/src/components/ChatMessage.test.js`
- Modify: `loopra-front/src/components/ChatMessage.vue`
- Modify: `loopra-front/src/views/Chat.vue`

- [ ] **Step 1: Write failing component tests**

Mount `ChatMessage` with a user message and assert that `[title="继续到新会话"]` does not exist. Mount it with an assistant message and assert that the same selector exists, emits `branchSession`, and is disabled when the new `branchDisabled` prop is true.

```js
import {shallowMount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import ChatMessage from './ChatMessage.vue'

const mountMessage = (msg, branchDisabled = false) => shallowMount(ChatMessage, {
  props: {msg, idx: 1, snapshotRollbackLoading: new Map(), branchDisabled},
  global: {stubs: {Teleport: true, BlockRenderer: true}}
})

it('does not offer branching for user messages', () => {
  expect(mountMessage({id: 1, role: 'user', content: 'hello'}).find('[title="继续到新会话"]').exists()).toBe(false)
})

it('offers branching for assistant messages and respects disabled state', async () => {
  const message = {id: 2, role: 'assistant', blocks: [{type: 'content', content: 'hi'}]}
  const disabled = mountMessage(message, true)
  expect(disabled.find('[title="继续到新会话"]').attributes('disabled')).toBeDefined()

  const enabled = mountMessage(message)
  await enabled.find('[title="继续到新会话"]').trigger('click')
  expect(enabled.emitted('branchSession')).toEqual([[message, 1]])
})
```

- [ ] **Step 2: Run the component test and confirm it fails**

Run: `pnpm vitest run src/components/ChatMessage.test.js`

Expected: FAIL because user messages currently contain the action and `branchDisabled` is not implemented.

- [ ] **Step 3: Restrict and stabilize the action**

Remove the branch button from the user-message footer. Keep it in the assistant footer and change it to:

```vue
<button class="copy-msg-btn"
        :disabled="branchDisabled"
        @click="$emit('branchSession', msg, idx)"
        title="继续到新会话"
        v-html="BRANCH_ICON"></button>
```

Add `branchDisabled: {type: Boolean, default: false}` to the component props. Move `.msg-actions` out of the `.msg-time-group` declaration so both CSS rules are syntactically complete.

- [ ] **Step 4: Resolve exact source boundaries in Chat.vue**

Import `sessionsAPI` statically and import `getAssistantTurnBoundaries`. During `loadHistory`, calculate all boundaries once and assign the next boundary to each newly created assistant item as `sourceMessageCount`.

Update `branchSession` to accept `(msg, msgIdx)`. Guard it with a `branchingSession` ref. Use `msg.sourceMessageCount` when present; otherwise fetch fresh raw history, calculate the selected assistant ordinal from visible assistant messages, and resolve its boundary. Reject a missing boundary. Pass `streaming || branchingSession` to `ChatMessage` as `branch-disabled`.

- [ ] **Step 5: Run focused frontend tests**

Run: `pnpm vitest run src/utils/sessionBranch.test.js src/components/ChatMessage.test.js`

Expected: all tests pass.

### Task 3: Exact and Isolated Backend Copy

**Files:**
- Create: `loopra-web/src/test/java/site/sorghum/loopra/web/service/AgentServiceBranchTest.java`
- Modify: `loopra-web/src/main/java/site/sorghum/loopra/web/service/AgentService.java`
- Modify: `loopra-web/src/main/java/site/sorghum/loopra/web/controller/SessionController.java`

- [ ] **Step 1: Write failing prefix-validation tests**

Add package-level tests for a new `AgentService.copyBranchMessages` helper. Verify it returns all fields and object identities for a valid prefix, and throws `ServiceException` for zero or a count greater than the source size.

```java
import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;
import site.sorghum.loopra.web.common.ServiceException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentServiceBranchTest {

@Test
void copiesTheExactRequestedPrefix() {
    ChatMessage assistant = ChatMessage.assistant("done", List.of(new ToolCallEntry("1", "read", Map.of())), "thinking");
    ChatMessage tool = ChatMessage.tool("1", "result");
    List<ChatMessage> source = List.of(assistant, tool);
    List<ChatMessage> copied = AgentService.copyBranchMessages(source, 2);
    assertEquals(source, copied);
    assertNotSame(source, copied);
}

@Test
void rejectsAnOutOfRangeBoundary() {
    assertThrows(ServiceException.class, () -> AgentService.copyBranchMessages(List.of(ChatMessage.ofUser("x")), 2));
}

}
```

- [ ] **Step 2: Run the backend test and confirm it fails**

Run: `mvn -pl loopra-web -am -DskipTests=false -Dtest=AgentServiceBranchTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `copyBranchMessages` does not exist.

- [ ] **Step 3: Implement exact boundary validation**

Add a package-private static helper that rejects empty sources and counts outside `1..sourceMessages.size()`, then returns `new ArrayList<>(sourceMessages.subList(0, messageCount))`. Replace `Math.min` in `branchSession` with this helper.

- [ ] **Step 4: Write through a separate store**

Resolve the workspace sessions directory with `WorkspaceManager`, create a dedicated `JsonlSessionStore`, bind it to a millisecond-precision new session name, rewrite the copied original `ChatMessage` list, flush it, and always call `shutdown()` in `finally`. Do not call `bindTo` on the source agent's store.

Update the controller documentation so `messageCount` is described as the exclusive raw-history end offset.

- [ ] **Step 5: Run focused backend and frontend verification**

Run: `mvn -pl loopra-web -am -DskipTests=false -Dtest=AgentServiceBranchTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `AgentServiceBranchTest` passes.

Run: `pnpm vitest run src/utils/sessionBranch.test.js src/components/ChatMessage.test.js`

Expected: all frontend tests pass.

Run: `pnpm build`

Expected: Vite build succeeds.
