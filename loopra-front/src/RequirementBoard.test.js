/* @vitest-environment jsdom */

import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {nextTick} from 'vue'
import RequirementBoard from './views/RequirementBoard.vue'

// ============ mock 后端数据 ============
const NOW = 1750000000000
let idCounter = 0
const nextId = () => `req_${(idCounter++).toString(36)}`

function req(title, status, extra = {}) {
  return {
    id: nextId(),
    title,
    description: '描述文本',
    priority: 'medium',
    projectHash: 'ws_front',
    projectName: 'loopra-front',
    status,
    summary: '',
    sessionName: '',
    createdAt: NOW,
    updatedAt: NOW,
    ...extra
  }
}

const REQUIREMENTS = [
  req('待执行需求A', 'todo'),
  req('待执行需求B', 'todo'),
  req('待执行需求C', 'todo', {priority: 'high'}),
  req('执行中需求D', 'doing'),
  req('执行中需求E', 'doing'),
  req('已完成需求F', 'done'),
  req('已失败需求G', 'failed')
]

const MESSAGES = [
  {id: 'm1', role: 'user', content: '这是一条评论', timestamp: NOW - 4000},
  {id: 'm2', role: 'assistant', content: 'AI 回复评论：收到，正在处理', timestamp: NOW - 3000},
  {id: 'm3', role: 'user', content: '第二条评论', timestamp: NOW - 2000},
  {id: 'm4', role: 'assistant', content: '开始执行需求', reasoning_content: '先分析需求描述与验收标准', timestamp: NOW - 1500,
   tool_calls: [{id: 'tc1', type: 'function', function: {name: 'read', arguments: '{"file_path":"src/a.java"}'}}]},
  {id: 'm4b', role: 'tool', tool_call_id: 'tc1', content: '{"content":"文件内容"}', timestamp: NOW - 1400},
  {id: 'm5', role: 'assistant', content: 'AI 完成实现并通过验证', file_changes: [{path: 'src/a.java', additions: 10, deletions: 2, created: false}], timestamp: NOW},
  {id: 'm6', role: 'assistant', content: '✅ 已完成：重构完成，测试全过', timestamp: NOW + 100},
  {id: 'm7', role: 'user', content: '用户发表了新评论，请阅读并回复', web_hidden: true, timestamp: NOW + 200},
  {id: 'm8', role: 'assistant', content: '收到，我会检查最新评论', timestamp: NOW + 300}
]

// hoisted：mock 工厂只能引用此容器（vi.mock 提升到文件顶部执行）
const {requirementAPI, configAPI} = vi.hoisted(() => ({
  requirementAPI: {
    list: vi.fn(),
    create: vi.fn(),
    addComment: vi.fn(),
    getMessages: vi.fn(),
    delete: vi.fn(),
    update: vi.fn(),
    run: vi.fn(),
    abort: vi.fn()
  },
  configAPI: {
    listWorkspaces: vi.fn()
  }
}))

vi.mock('./services/api', () => ({requirementAPI, configAPI}))

beforeEach(() => {
  requirementAPI.list.mockResolvedValue({success: true, data: REQUIREMENTS})
  requirementAPI.create.mockResolvedValue({success: true, data: req('新需求标题', 'todo')})
  requirementAPI.addComment.mockResolvedValue({success: true})
  requirementAPI.getMessages.mockResolvedValue({success: true, data: MESSAGES})
  requirementAPI.delete.mockResolvedValue({success: true})
  requirementAPI.update.mockResolvedValue({success: true})
  requirementAPI.run.mockResolvedValue({success: true})
  requirementAPI.abort.mockResolvedValue({success: true})
  configAPI.listWorkspaces.mockResolvedValue({
    success: true,
    data: [
      { hash: 'ws_agent4j', name: 'agent4j', path: '/p/agent4j' },
      { hash: 'ws_front', name: 'loopra-front', path: '/p/loopra-front' }
    ]
  })
})

// ChatMessage 用 stub 验证消息流数据契约（组件本身由 ChatMessage.test.js 覆盖）
const ChatMessageStub = {
  props: ['msg'],
  template: '<div class="stub-msg" :data-role="msg.role">{{ msg.content || (msg.blocks || []).map((b) => b.content || "").join("") }}</div>'
}

// 项目风格自定义下拉用可交互 stub（组件本身的行为与样式在 ReqSelect.vue）
const ReqSelectStub = {
  props: ['modelValue', 'options', 'placeholder'],
  emits: ['update:modelValue'],
  data: () => ({ open: false }),
  template: `
    <div class="stub-select">
      <button type="button" class="stub-select-trigger" @click="open = !open">{{ modelValue ? (options.find((o) => o.value === modelValue)?.label || "") : placeholder }}</button>
      <div v-if="open" class="stub-select-panel">
        <button v-for="o in options" :key="o.value" type="button" class="stub-select-option" @click="$emit('update:modelValue', o.value); open = false">{{ o.label }}</button>
      </div>
    </div>
  `
}

function mountBoard() {
  return shallowMount(RequirementBoard, {
    global: {stubs: {ChatMessage: ChatMessageStub, ReqSelect: ReqSelectStub}}
  })
}

// 异步轮询链需要多轮微任务才能完成（loadFromAPI → loadMessages），循环 flush
async function flushAll() {
  for (let i = 0; i < 10; i++) {
    await Promise.resolve()
  }
}

describe('RequirementBoard 需求池看板（后端数据）', () => {
  let wrapper

  beforeEach(async () => {
    idCounter = 0
    requirementAPI.list.mockClear()
    requirementAPI.create.mockClear()
    requirementAPI.addComment.mockClear()
    requirementAPI.getMessages.mockClear()
    requirementAPI.delete.mockClear()
    requirementAPI.run.mockClear()
    requirementAPI.abort.mockClear()
    vi.useFakeTimers()
    wrapper = mountBoard()
    // flushPromises 依赖 setTimeout，在 fake timers 下不可靠，显式等待数据加载
    await wrapper.vm.loadFromAPI?.()
    await wrapper.vm.loadProjects?.()
    await nextTick()
  })

  afterEach(() => {
    vi.useRealTimers()
    wrapper.unmount()
  })

  it('从后端加载需求并渲染四列看板', () => {
    expect(requirementAPI.list).toHaveBeenCalled()
    const columns = wrapper.findAll('.req-column')
    expect(columns).toHaveLength(4)
    expect(columns.map((col) => col.find('.req-column-name').text()))
      .toEqual(['待执行', '执行中', '已完成', '已失败'])
    expect(wrapper.findAll('.req-column-todo .req-card')).toHaveLength(3)
    expect(wrapper.findAll('.req-column-doing .req-card')).toHaveLength(2)
    expect(wrapper.findAll('.req-column-done .req-card')).toHaveLength(1)
    expect(wrapper.findAll('.req-column-failed .req-card')).toHaveLength(1)
  })

  it('卡片展示 AI 执行标识与项目名', () => {
    const card = wrapper.findAll('.req-column-todo .req-card')[0]
    expect(card.find('.req-ai-badge').text()).toContain('AI 执行')
    expect(card.find('.req-project-badge').text()).toBe('loopra-front')
    expect(card.find('.req-card-agent').exists()).toBe(false)
  })

  it('点击卡片打开全屏详情并拉取消息流', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')

    expect(requirementAPI.getMessages).toHaveBeenCalled()
    expect(wrapper.find('.req-detail-view').exists()).toBe(true)
    expect(wrapper.find('.req-board-columns').exists()).toBe(false)
    expect(wrapper.find('.req-info-desc').text()).toContain('项目：loopra-front')
    expect(wrapper.find('.req-ai-box').exists()).toBe(true)

    // 消息流异步到达
    await nextTick()
    // 评论 tab（看板风格）：user 评论 + AI 回复 + AI 结束评论 + 指令后回复 共 6 条
    expect(wrapper.findAll('.req-comment')).toHaveLength(6)
  })

  it('评论 tab 为看板风格条目（头像/作者/时间），执行日志 tab 为聊天消息流', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await nextTick()

    // 评论条目：m1(user) + m2(AI回复) + m3(user) + m4(AI回复) + m6(AI 结束评论) + m8(webHidden 指令后的 AI 回复)
    // m5 为执行过程日志、m7 为 webHidden 内部指令（不显示但保留回复配对）
    const comments = wrapper.findAll('.req-comment')
    expect(comments).toHaveLength(6)
    expect(comments[0].find('.req-comment-author').text()).toBe('我')
    expect(comments[0].find('.req-comment-text').text()).toBe('这是一条评论')
    expect(comments[0].find('.req-comment-time').text()).toBeTruthy() // 相对时间
    expect(comments[0].find('.req-comment-avatar').text()).toBe('我')
    expect(comments[1].find('.req-comment-author').text()).toBe('AI 执行 Agent')
    expect(comments[1].find('.req-comment-avatar').exists()).toBe(true)
    expect(comments[1].find('.req-comment-text').text()).toBe('AI 回复评论：收到，正在处理')
    expect(comments[3].find('.req-comment-text').text()).toBe('开始执行需求')
    // AI 结束评论（✅ 前缀）即使前面没有用户评论也独立展示
    expect(comments[4].find('.req-comment-author').text()).toBe('AI 执行 Agent')
    expect(comments[4].find('.req-comment-text').text()).toBe('✅ 已完成：重构完成，测试全过')
    // webHidden 内部指令不显示，但其后的 AI 回复正常展示
    expect(comments[5].find('.req-comment-text').text()).toBe('收到，我会检查最新评论')
    // 评论计数不含 webHidden 指令（m1、m3 共 2 条用户评论）
    expect(wrapper.find('.req-detail-tab').text()).toContain('评论 (2)')
    // 输入区：多行 textarea + 发送按钮
    expect(wrapper.find('.req-comment-form textarea').exists()).toBe(true)
    expect(wrapper.find('.req-comment-form-foot .req-btn-primary').text()).toBe('发送评论')

    // 执行日志 tab：ChatMessage 聊天流（assistant 消息，连续 assistant 合并，m7 指令分隔 m8）
    await wrapper.findAll('.req-detail-tab')[1].trigger('click')
    const logs = wrapper.findAll('.stub-msg')
    expect(logs).toHaveLength(3)
    expect(logs.map((log) => log.attributes('data-role'))).toEqual(['assistant', 'assistant', 'assistant'])
    expect(logs[1].text()).toContain('AI 完成实现并通过验证')

    // 消息结构与聊天框一致：思考 / 工具调用（含结果）/ 文本 / 文件改动
    const logMessages = wrapper.vm.logMessages
    expect(logMessages).toHaveLength(3) // m2 / m4+m5+m6 合并 / m8（m7 为 user 指令，分隔 assistant 组）
    const main = logMessages[1] // m4+m5+m6 合并
    expect(main.blocks.some((b) => b.type === 'reasoning' && b.content.includes('先分析需求'))).toBe(true)
    const toolCall = main.blocks.find((b) => b.type === 'tool_call')
    expect(toolCall.name).toBe('read')
    expect(toolCall.status).toBe('成功')
    expect(toolCall.args).toEqual({ file_path: 'src/a.java' })
    expect(toolCall.result).toContain('文件内容')
    const fileChanges = main.blocks.find((b) => b.type === 'file_changes')
    expect(fileChanges.changes[0].additions).toBe(10)
    // file_changes 块移到消息末尾
    expect(main.blocks[main.blocks.length - 1].type).toBe('file_changes')
  })

  it('提交评论调用后端接口并重新拉取消息流', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await nextTick()

    const textarea = wrapper.find('.req-comment-form textarea')
    await textarea.setValue('新评论内容')
    await wrapper.find('.req-comment-form').trigger('submit')
    await nextTick()

    expect(requirementAPI.addComment).toHaveBeenCalledWith(expect.any(String), '新评论内容')
    expect(requirementAPI.getMessages).toHaveBeenCalledTimes(2)
  })

  it('让 AI 执行：调用后端 run 接口并进入执行中', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await nextTick()
    expect(wrapper.find('.req-status-todo').exists()).toBe(true)

    await wrapper.find('.req-ai-actions .req-btn-primary').trigger('click')
    await nextTick()

    expect(requirementAPI.run).toHaveBeenCalledWith(expect.any(String))
    expect(wrapper.find('.req-status-doing').exists()).toBe(true)
    expect(wrapper.find('.req-ai-actions .req-btn-danger').text()).toBe('取消执行')
  })

  it('取消执行：调用后端 abort 接口并回退待执行', async () => {
    // 打开一个执行中的需求
    await wrapper.findAll('.req-column-doing .req-card')[0].trigger('click')
    await nextTick()
    expect(wrapper.find('.req-status-doing').exists()).toBe(true)

    await wrapper.find('.req-ai-actions .req-btn-danger').trigger('click')
    await nextTick()

    expect(requirementAPI.abort).toHaveBeenCalledWith(expect.any(String))
    expect(wrapper.find('.req-status-todo').exists()).toBe(true)
  })

  it('删除需求：二次确认后调用后端接口并刷新列表', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await nextTick()

    // 第一次点击：进入确认态
    await wrapper.find('[title="删除需求"]').trigger('click')
    expect(wrapper.find('.req-delete-confirm').exists()).toBe(true)
    expect(requirementAPI.delete).not.toHaveBeenCalled()

    // 第二次点击：执行删除
    await wrapper.find('.req-delete-confirm').trigger('click')
    await nextTick()

    expect(requirementAPI.delete).toHaveBeenCalledWith(expect.any(String))
    expect(wrapper.find('.req-detail-view').exists()).toBe(false) // 详情已关闭
    expect(wrapper.find('.req-board-columns').exists()).toBe(true) // 回到看板
  })

  it('详情打开时 3 秒定时刷新评论与执行日志', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await nextTick()
    const callsAfterOpen = requirementAPI.getMessages.mock.calls.length
    expect(callsAfterOpen).toBeGreaterThanOrEqual(1) // 打开时立即拉取

    // 推进 3s：轮询自动刷新消息流（评论/日志/回复回合）
    vi.advanceTimersByTime(3000)
    await flushAll()
    expect(requirementAPI.getMessages.mock.calls.length).toBeGreaterThan(callsAfterOpen)

    // 关闭详情后不再拉取消息流（列表仍刷新）
    await wrapper.find('.req-detail-back').trigger('click')
    const callsAfterClose = requirementAPI.getMessages.mock.calls.length
    vi.advanceTimersByTime(6000)
    await nextTick()
    expect(requirementAPI.getMessages.mock.calls.length).toBe(callsAfterClose)
  })

  it('用户上滑浏览历史时，轮询刷新不强制滚回底部', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await nextTick()
    await flushAll()
    // 初始状态：跟随底部
    expect(wrapper.vm.userScrolledAway).toBe(false)

    // 模拟用户上滑到历史位置
    wrapper.vm.onListScroll({ target: { scrollHeight: 1000, clientHeight: 500, scrollTop: 100 } })
    expect(wrapper.vm.userScrolledAway).toBe(true)

    // 3s 轮询刷新消息流：不重置浏览位置，也不强制滚底
    vi.advanceTimersByTime(3000)
    await flushAll()
    expect(wrapper.vm.userScrolledAway).toBe(true)

    // 用户滚回底部 → 恢复自动跟随
    wrapper.vm.onListScroll({ target: { scrollHeight: 1000, clientHeight: 500, scrollTop: 500 } })
    expect(wrapper.vm.userScrolledAway).toBe(false)

    // 切换 tab / 重新打开详情 → 重置为跟随底部
    await wrapper.findAll('.req-detail-tab')[1].trigger('click')
    wrapper.vm.onListScroll({ target: { scrollHeight: 1000, clientHeight: 500, scrollTop: 100 } })
    expect(wrapper.vm.userScrolledAway).toBe(true)
    await wrapper.findAll('.req-detail-tab')[0].trigger('click')
    expect(wrapper.vm.userScrolledAway).toBe(false)
  })

  it('返回按钮关闭全屏详情，回到看板', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    expect(wrapper.find('.req-detail-view').exists()).toBe(true)

    await wrapper.find('.req-detail-back').trigger('click')
    expect(wrapper.find('.req-detail-view').exists()).toBe(false)
    expect(wrapper.find('.req-board-columns').exists()).toBe(true)
  })

  it('新建需求：必须选择项目，创建走后端接口', async () => {
    await wrapper.find('.req-btn-primary').trigger('click')
    expect(wrapper.find('.req-create-modal').exists()).toBe(true)

    const selects = wrapper.findAll('.stub-select')
    expect(selects).toHaveLength(2)

    // 未选项目：创建按钮禁用
    await wrapper.find('.req-field input').setValue('新需求标题')
    expect(wrapper.find('.req-create-footer .req-btn-primary').attributes('disabled')).toBeDefined()
    await wrapper.find('.req-create-modal').trigger('submit')
    expect(requirementAPI.create).not.toHaveBeenCalled()

    // 选择项目后创建
    await selects[0].find('.stub-select-trigger').trigger('click')
    await selects[0].findAll('.stub-select-option')[1].trigger('click') // loopra-front
    expect(wrapper.find('.req-create-footer .req-btn-primary').attributes('disabled')).toBeUndefined()
    await wrapper.find('.req-create-modal').trigger('submit')
    await nextTick()

    expect(requirementAPI.create).toHaveBeenCalledWith(expect.objectContaining({
      title: '新需求标题',
      projectHash: 'ws_front',
      projectName: 'loopra-front'
    }))
    // 创建后刷新列表（含挂载时加载，共至少 2 次）
    expect(requirementAPI.list.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
})
