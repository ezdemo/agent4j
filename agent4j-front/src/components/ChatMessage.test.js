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
    expect(wrapper.find('.user-auto-message-trigger').text()).toContain('折叠块')
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
