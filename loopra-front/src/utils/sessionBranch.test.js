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
