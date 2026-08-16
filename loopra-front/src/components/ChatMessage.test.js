/* @vitest-environment jsdom */

import {shallowMount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import ChatMessage from './ChatMessage.vue'

const mountMessage = (msg, branchDisabled = false) => shallowMount(ChatMessage, {
  props: {msg, idx: 1, snapshotRollbackLoading: new Map(), branchDisabled},
  global: {stubs: {Teleport: true, BlockRenderer: true}}
})

describe('ChatMessage branching', () => {
  it('expands automatic user blocks on demand while keeping the user text visible', async () => {
    const wrapper = mountMessage({
      id: 1,
      role: 'user',
      content: '```折叠块\n调用技能：\n/skill:hv-analysis\n```\n\n你好啊'
    })

    expect(wrapper.find('.user-auto-message').exists()).toBe(true)
    expect(wrapper.find('.user-auto-message-trigger').text()).toContain('附加上下文')
    expect(wrapper.find('.user-auto-message-detail').exists()).toBe(false)
    await wrapper.find('.user-auto-message-trigger').trigger('click')
    expect(wrapper.find('.user-auto-message-detail').text()).toContain('/skill:hv-analysis')
    expect(wrapper.find('.msg-text').text()).toBe('你好啊')
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
})

describe('ChatMessage compacted summary', () => {
  const compactedMessage = {
    id: 10,
    role: 'user',
    time: '10:00',
    content: '[历史上下文折叠]\n<compacted-summary>\n主要意图：实现登录页\n当前进度：已完成\n</compacted-summary>'
  }

  it('renders a compacted notice instead of a normal user bubble', () => {
    const wrapper = mountMessage(compactedMessage)

    expect(wrapper.find('.user-body').exists()).toBe(false)
    expect(wrapper.find('.compacted-summary').exists()).toBe(true)
    expect(wrapper.find('.compacted-summary-title').text()).toBe('较早对话已压缩')
    expect(wrapper.find('.compacted-summary-content').exists()).toBe(false)
  })

  it('expands the checkpoint content on demand', async () => {
    const wrapper = mountMessage(compactedMessage)

    expect(wrapper.find('.compacted-summary-content').exists()).toBe(false)
    await wrapper.find('.compacted-summary-btn').trigger('click')
    expect(wrapper.find('.compacted-summary-content').exists()).toBe(true)
    expect(wrapper.find('.compacted-summary-content').text()).toContain('实现登录页')
  })

  it('emits viewRawEvents when the raw record button is clicked', async () => {
    const wrapper = mountMessage(compactedMessage)

    await wrapper.find('.compacted-summary-btn.primary').trigger('click')
    expect(wrapper.emitted('viewRawEvents')).toEqual([[compactedMessage]])
  })
})
