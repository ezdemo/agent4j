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
  {id: 'm1', role: 'user', content: '这是一条评论', timestamp: NOW - 3000},
  {id: 'm2', role: 'assistant', content: 'AI 回复评论：收到，正在处理', timestamp: NOW - 2000},
  {id: 'm3', role: 'user', content: '第二条评论', timestamp: NOW - 1000},
  {id: 'm4', role: 'assistant', content: 'AI 已接收需求，开始执行', timestamp: NOW - 500},
  {id: 'm5', role: 'assistant', content: 'AI 完成实现并通过验证', timestamp: NOW}
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
    // 评论 tab（看板风格）：user 评论 + AI 回复 共 4 条
    expect(wrapper.findAll('.req-comment')).toHaveLength(4)
  })

  it('评论 tab 为看板风格条目（头像/作者/时间），执行日志 tab 为聊天消息流', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await nextTick()

    // 评论条目：m1(user) + m2(AI回复) + m3(user) + m4(AI回复)，m5 为执行过程日志不混入
    const comments = wrapper.findAll('.req-comment')
    expect(comments).toHaveLength(4)
    expect(comments[0].find('.req-comment-author').text()).toBe('我')
    expect(comments[0].find('.req-comment-text').text()).toBe('这是一条评论')
    expect(comments[0].find('.req-comment-time').text()).toBeTruthy() // 相对时间
    expect(comments[0].find('.req-comment-avatar').text()).toBe('我')
    expect(comments[1].find('.req-comment-author').text()).toBe('AI 执行 Agent')
    expect(comments[1].find('.req-comment-avatar').exists()).toBe(true)
    expect(comments[1].find('.req-comment-text').text()).toBe('AI 回复评论：收到，正在处理')
    expect(comments[3].find('.req-comment-text').text()).toBe('AI 已接收需求，开始执行')
    // 输入区：多行 textarea + 发送按钮
    expect(wrapper.find('.req-comment-form textarea').exists()).toBe(true)
    expect(wrapper.find('.req-comment-form-foot .req-btn-primary').text()).toBe('发送评论')

    // 执行日志 tab：ChatMessage 聊天流（assistant 消息）
    await wrapper.findAll('.req-detail-tab')[1].trigger('click')
    const logs = wrapper.findAll('.stub-msg')
    expect(logs).toHaveLength(3)
    expect(logs.map((log) => log.attributes('data-role'))).toEqual(['assistant', 'assistant', 'assistant'])
    expect(logs[2].text()).toContain('AI 完成实现并通过验证')
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
