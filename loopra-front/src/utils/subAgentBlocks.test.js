import {describe, expect, it} from 'vitest'
import {applySubAgentEvent, createSubAgentContainer, findSubAgentBlock} from './subAgentBlocks'

describe('createSubAgentContainer / findSubAgentBlock', () => {
  it('creates container with default structure', () => {
    const c = createSubAgentContainer(1)
    expect(c).toEqual({
      type: 'sub_agent',
      subId: 1,
      blocks: [],
      status: '运行中',
      taskName: '子代理',
      expanded: true
    })
  })

  it('reuses existing container by subId and creates missing one', () => {
    const blocks = []
    const first = findSubAgentBlock(blocks, 7)
    expect(blocks).toHaveLength(1)
    expect(blocks[0].subId).toBe(7)
    const second = findSubAgentBlock(blocks, 7)
    expect(second).toBe(first)
    expect(blocks).toHaveLength(1)
  })
})

describe('applySubAgentEvent: 内容累积', () => {
  it('sub_start updates taskName and subSessionId without content blocks', () => {
    const c = createSubAgentContainer(1)
    expect(applySubAgentEvent(c, {type: 'sub_start', subId: 1, subSessionId: 'sub-abc', task: '探索项目'})).toBe(true)
    expect(c.taskName).toBe('探索项目')
    expect(c.subSessionId).toBe('sub-abc')
    expect(c.blocks).toHaveLength(0)
  })

  it('sub_content appends delta tokens into one content block', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_content', subId: 1, token: '你好'})
    applySubAgentEvent(c, {type: 'sub_content', subId: 1, token: '世界'})
    expect(c.blocks).toHaveLength(1)
    expect(c.blocks[0]).toEqual({type: 'content', content: '你好世界'})
  })

  it('sub_content with full content (replay) appends to existing block', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_content', subId: 1, content: '完整段一'})
    applySubAgentEvent(c, {type: 'sub_content', subId: 1, content: '完整段二'})
    expect(c.blocks[0].content).toBe('完整段一完整段二')
  })

  it('ignores blank content between reasoning gaps', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_content', subId: 1, token: '  \n\n  '})
    expect(c.blocks).toHaveLength(0)
  })

  it('sub_reasoning accumulates into reasoning block (folded)', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_reasoning_started', subId: 1})
    applySubAgentEvent(c, {type: 'sub_reasoning', subId: 1, token: '先分析'})
    applySubAgentEvent(c, {type: 'sub_reasoning', subId: 1, content: '再动手'})
    expect(c.blocks).toHaveLength(1)
    expect(c.blocks[0].type).toBe('reasoning')
    expect(c.blocks[0].content).toBe('先分析再动手')
    expect(c.blocks[0].showContent).toBe(false)
  })
})

describe('applySubAgentEvent: 工具调用', () => {
  it('sub_tool_call pushes executing tool block with parsed args', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_tool_call', subId: 1, name: 'read', args: '{"file_path":"a.java"}', startedAt: 1000})
    const tool = c.blocks[0]
    expect(tool.type).toBe('tool_call')
    expect(tool.name).toBe('read')
    expect(tool.args).toEqual({file_path: 'a.java'})
    expect(tool.status).toBe('执行中')
    expect(tool.toolStartedAt).toBe(1000)
  })

  it('sub_tool_call keeps non-JSON args as-is', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_tool_call', subId: 1, name: 'bash', args: 'echo hi'})
    expect(c.blocks[0].args).toBe('echo hi')
  })

  it('sub_tool_result fills matching tool block with duration from event timestamps', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_tool_call', subId: 1, name: 'read', args: '{}', startedAt: 1000})
    applySubAgentEvent(c, {type: 'sub_tool_result', subId: 1, name: 'read', result: '内容', finishedAt: 2500})
    const tool = c.blocks[0]
    expect(tool.status).toBe('成功')
    expect(tool.result).toBe('内容')
    expect(tool.toolDurationMs).toBe(1500)
    expect(tool.expanded).toBe(false)
  })

  it('sub_tool_result fills last unfinished tool when name mismatch', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_tool_call', subId: 1, name: 'read', args: '{}', startedAt: 1000})
    applySubAgentEvent(c, {type: 'sub_tool_result', subId: 1, name: 'unknown', result: '兜底', finishedAt: 2000})
    expect(c.blocks[0].result).toBe('兜底')
  })
})

describe('applySubAgentEvent: 状态与边界', () => {
  it('sub_complete then sub_end updates status', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_complete', subId: 1})
    expect(c.status).toBe('已完成')
    applySubAgentEvent(c, {type: 'sub_end', subId: 1, status: 'completed'})
    expect(c.status).toBe('已完成')
    expect(c.expanded).toBe(false)
  })

  it('sub_end aborted/error maps to cancelled/failed', () => {
    const aborted = createSubAgentContainer(1)
    applySubAgentEvent(aborted, {type: 'sub_end', subId: 1, status: 'aborted'})
    expect(aborted.status).toBe('已取消')
    const failed = createSubAgentContainer(1)
    applySubAgentEvent(failed, {type: 'sub_end', subId: 1, status: 'error'})
    expect(failed.status).toBe('失败')
  })

  it('sub_error pushes error content block', () => {
    const c = createSubAgentContainer(1)
    applySubAgentEvent(c, {type: 'sub_error', subId: 1, error: '模型调用失败'})
    expect(c.blocks[0].content).toContain('模型调用失败')
  })

  it('sub_choice appends choice block by default (replay)', () => {
    const c = createSubAgentContainer(1)
    const consumed = applySubAgentEvent(c, {type: 'sub_choice', subId: 1, title: '审批', options: [{title: '同意', value: 'approve'}]})
    expect(consumed).toBe(true)
    expect(c.blocks[0].type).toBe('choice')
    expect(c.blocks[0].options).toEqual([{title: '同意', value: 'approve'}])
    expect(c.blocks[0].question).toBe('子代理 审批')
  })

  it('sub_choice with attachChoice=false is not consumed (main stream handles it)', () => {
    const c = createSubAgentContainer(1)
    const consumed = applySubAgentEvent(c, {type: 'sub_choice', subId: 1, options: []}, {attachChoice: false})
    expect(consumed).toBe(false)
    expect(c.blocks).toHaveLength(0)
  })

  it('unknown / ignored types return false', () => {
    const c = createSubAgentContainer(1)
    expect(applySubAgentEvent(c, {type: 'sub_usage', subId: 1})).toBe(false)
    expect(applySubAgentEvent(c, {type: 'sub_log', subId: 1})).toBe(false)
    expect(applySubAgentEvent(c, {type: 'content', content: '主代理正文'})).toBe(false)
    expect(c.blocks).toHaveLength(0)
  })
})

describe('回放完整流程', () => {
  it('replays a full recorded session into container blocks', () => {
    const c = createSubAgentContainer(1)
    const events = [
      {type: 'sub_start', subId: 1, subSessionId: 'sub-full', task: '审查代码', profile: 'review', startedAt: 1000},
      {type: 'sub_reasoning', subId: 1, content: '先看关键路径'},
      {type: 'sub_tool_call', subId: 1, name: 'read', args: {file_path: 'src/a.java'}, startedAt: 2000},
      {type: 'sub_tool_result', subId: 1, name: 'read', result: '文件内容', finishedAt: 3000},
      {type: 'sub_content', subId: 1, content: '结论：无严重问题'},
      {type: 'sub_complete', subId: 1},
      {type: 'sub_end', subId: 1, status: 'completed', endedAt: 4000}
    ]
    for (const e of events) applySubAgentEvent(c, e)

    expect(c.taskName).toBe('审查代码')
    expect(c.status).toBe('已完成')
    expect(c.blocks.map(b => b.type)).toEqual(['reasoning', 'tool_call', 'content'])
    expect(c.blocks[1].result).toBe('文件内容')
    expect(c.blocks[1].toolDurationMs).toBe(1000)
    expect(c.blocks[2].content).toBe('结论：无严重问题')
  })
})
