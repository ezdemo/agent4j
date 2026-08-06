/* @vitest-environment jsdom */

import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {nextTick} from 'vue'
import {configAPI} from './services/api'
import RequirementBoard from './views/RequirementBoard.vue'

vi.mock('./services/api', () => ({
  configAPI: {
    listWorkspaces: vi.fn().mockResolvedValue({
      success: true,
      data: [
        { hash: 'ws_agent4j', name: 'agent4j', path: '/p/agent4j' },
        { hash: 'ws_front', name: 'loopra-front', path: '/p/loopra-front' }
      ]
    })
  }
}))

const STORAGE_KEY = 'loopra-requirement-board'

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

describe('RequirementBoard 需求池看板', () => {
  let wrapper

  beforeEach(async () => {
    localStorage.clear()
    vi.useFakeTimers()
    wrapper = mountBoard()
    // flushPromises 依赖 setTimeout，在 fake timers 下不可靠，显式等待项目列表加载
    await wrapper.vm.loadProjects?.()
    await nextTick()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('渲染四列看板且种子数据分布在各列', () => {
    const columns = wrapper.findAll('.req-column')
    expect(columns).toHaveLength(4)
    expect(columns.map((col) => col.find('.req-column-name').text()))
      .toEqual(['待执行', '执行中', '已完成', '已失败'])
    // 种子数据：待执行 3 条、执行中 2 条、已完成 1 条、已失败 1 条
    expect(wrapper.findAll('.req-column-todo .req-card')).toHaveLength(3)
    expect(wrapper.findAll('.req-column-doing .req-card')).toHaveLength(2)
    expect(wrapper.findAll('.req-column-done .req-card')).toHaveLength(1)
    expect(wrapper.findAll('.req-column-failed .req-card')).toHaveLength(1)
  })

  it('卡片展示 AI 执行标识，无执行 Agent', () => {
    const card = wrapper.findAll('.req-column-todo .req-card')[0]
    expect(card.find('.req-ai-badge').exists()).toBe(true)
    expect(card.find('.req-ai-badge').text()).toContain('AI 执行')
    expect(card.find('.req-card-agent').exists()).toBe(false)
  })

  it('点击卡片打开全屏详情：描述 + AI 执行区 + 聊天区，看板隐藏', async () => {
    const card = wrapper.findAll('.req-column-todo .req-card')[0]
    const title = card.find('.req-card-title').text().replace(/高|中|低$/, '').trim()
    await card.trigger('click')

    // 全屏详情视图覆盖，看板不渲染
    expect(wrapper.find('.req-detail-view').exists()).toBe(true)
    expect(wrapper.find('.req-board-columns').exists()).toBe(false)
    expect(wrapper.find('.req-detail-title-text').text()).toBe(title)
    expect(wrapper.find('.req-info-desc').text()).toContain('描述')
    expect(wrapper.find('.req-ai-box').exists()).toBe(true)
    expect(wrapper.find('.req-ai-name').text()).toBe('待执行')
    expect(wrapper.find('.req-chat-messages').exists()).toBe(true)
  })

  it('执行日志以聊天消息流展示（assistant 消息），评论以 user 消息展示', async () => {
    // 第一条待执行需求：1 条日志、0 条评论
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')

    // 默认评论 tab：空态
    expect(wrapper.findAll('.stub-msg')).toHaveLength(0)
    expect(wrapper.find('.req-chat-empty').text()).toContain('暂无评论')

    // 切到执行日志 tab：日志渲染为 assistant 消息
    await wrapper.findAll('.req-detail-tab')[1].trigger('click')
    const logStubs = wrapper.findAll('.stub-msg')
    expect(logStubs).toHaveLength(1)
    expect(logStubs[0].attributes('data-role')).toBe('assistant')
    expect(logStubs[0].text()).toContain('等待 AI 调度')

    // 切回评论 tab
    await wrapper.findAll('.req-detail-tab')[0].trigger('click')
    expect(wrapper.find('.req-chat-input').exists()).toBe(true)
  })

  it('添加评论：以 user 消息出现在聊天流并持久化', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    await wrapper.findAll('.req-detail-tab')[0].trigger('click')

    const input = wrapper.find('.req-chat-input input')
    await input.setValue('这是一条测试评论')
    await wrapper.find('.req-chat-input').trigger('submit')

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY))
    const target = stored.find((item) => item.status === 'todo')
    expect(target.comments.at(-1).text).toBe('这是一条测试评论')

    const commentStubs = wrapper.findAll('.stub-msg')
    expect(commentStubs).toHaveLength(1)
    expect(commentStubs[0].attributes('data-role')).toBe('user')
    expect(commentStubs[0].text()).toBe('这是一条测试评论')
  })

  it('AI 流转：待执行 → 执行中 → 已完成，日志以聊天消息呈现', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    expect(wrapper.find('.req-status-todo').exists()).toBe(true)

    // 让 AI 执行（模拟 loading 后流转）
    await wrapper.find('.req-ai-actions .req-btn-primary').trigger('click')
    expect(wrapper.find('.req-ai-name').text()).toBe('AI 执行中…')
    vi.advanceTimersByTime(700)
    await nextTick()

    expect(wrapper.find('.req-status-doing').exists()).toBe(true)
    const doing = JSON.parse(localStorage.getItem(STORAGE_KEY))
    expect(doing.find((item) => item.status === 'doing').logs.at(-1).text).toContain('AI 已接收需求')

    // AI 已完成
    const actions = wrapper.findAll('.req-ai-actions .req-btn')
    await actions.find((btn) => btn.text().includes('AI 已完成')).trigger('click')
    vi.advanceTimersByTime(700)
    await nextTick()

    expect(wrapper.find('.req-status-done').exists()).toBe(true)
    const done = JSON.parse(localStorage.getItem(STORAGE_KEY))
    expect(done.find((item) => item.status === 'done').logs.at(-1).text).toContain('AI 已完成实现并通过验证')

    // 日志 tab 中新增日志以 assistant 消息展示（原 1 条 + 流转 2 条）
    await wrapper.findAll('.req-detail-tab')[1].trigger('click')
    const logStubs = wrapper.findAll('.stub-msg')
    expect(logStubs).toHaveLength(3)
    expect(logStubs[2].text()).toContain('AI 已完成实现并通过验证')
  })

  it('AI 流转：执行中 → 已失败', async () => {
    const card = wrapper.findAll('.req-column-doing .req-card')[0]
    await card.trigger('click')
    const actions = wrapper.findAll('.req-ai-actions .req-btn')
    await actions.find((btn) => btn.text().includes('AI 失败')).trigger('click')
    vi.advanceTimersByTime(700)
    await nextTick()

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY))
    const failed = stored.find((item) => item.id === wrapper.vm.selected.id)
    expect(failed.status).toBe('failed')
    expect(failed.logs.at(-1).text).toContain('AI 执行失败')
    expect(wrapper.find('.req-status-failed').exists()).toBe(true)
  })

  it('返回按钮关闭全屏详情，回到看板', async () => {
    await wrapper.findAll('.req-column-todo .req-card')[0].trigger('click')
    expect(wrapper.find('.req-detail-view').exists()).toBe(true)

    await wrapper.find('.req-detail-back').trigger('click')
    expect(wrapper.find('.req-detail-view').exists()).toBe(false)
    expect(wrapper.find('.req-board-columns').exists()).toBe(true)
  })

  it('新建需求：必须选择项目才能创建', async () => {
    await wrapper.find('.req-btn-primary').trigger('click')
    expect(wrapper.find('.req-create-modal').exists()).toBe(true)

    const selects = wrapper.findAll('.stub-select')
    // 项目 + 优先级两个自定义下拉
    expect(selects).toHaveLength(2)

    // 未选项目：创建按钮禁用，提交不创建
    await wrapper.find('.req-field input').setValue('新需求标题')
    expect(wrapper.find('.req-create-footer .req-btn-primary').attributes('disabled')).toBeDefined()
    await wrapper.find('.req-create-modal').trigger('submit')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY))).toHaveLength(7)

    // 通过自定义下拉选择项目后创建成功
    await selects[0].find('.stub-select-trigger').trigger('click')
    await selects[0].findAll('.stub-select-option')[1].trigger('click') // loopra-front
    expect(wrapper.find('.req-create-footer .req-btn-primary').attributes('disabled')).toBeUndefined()
    await wrapper.find('.req-create-modal').trigger('submit')

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY))
    const created = stored.find((item) => item.title === '新需求标题')
    expect(created.status).toBe('todo')
    expect(created.project).toEqual({ hash: 'ws_front', name: 'loopra-front' })
    expect(created.logs[0].text).toContain('等待 AI 调度')
    expect(wrapper.findAll('.req-column-todo .req-card')).toHaveLength(4)
  })

  it('卡片展示所属项目徽章，详情描述区显示项目', async () => {
    const card = wrapper.findAll('.req-column-todo .req-card')[0]
    expect(card.find('.req-project-badge').exists()).toBe(true)
    expect(card.find('.req-project-badge').text()).toBe('loopra-front')

    await card.trigger('click')
    expect(wrapper.find('.req-info-desc .req-info-project').text()).toContain('loopra-front')
  })
})
