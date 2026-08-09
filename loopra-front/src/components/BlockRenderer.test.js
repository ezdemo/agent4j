/* @vitest-environment jsdom */

import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import {nextTick} from 'vue'
import BlockRenderer from './BlockRenderer.vue'

describe('BlockRenderer sub-agent streaming', () => {
  it('keeps a manually expanded sub-agent open when later stream data replaces its block', async () => {
    const wrapper = mount(BlockRenderer, {
      props: {
        blocks: [{
          type: 'sub_agent',
          subId: 'worker-1',
          expanded: false,
          status: '运行中',
          blocks: [{type: 'content', content: '第一条消息'}]
        }]
      }
    })

    await wrapper.find('.tool-head').trigger('click')
    expect(wrapper.text()).toContain('第一条消息')

    await wrapper.setProps({
      blocks: [{
        type: 'sub_agent',
        subId: 'worker-1',
        expanded: false,
        status: '运行中',
        blocks: [
          {type: 'content', content: '第一条消息'},
          {type: 'content', content: '后续消息'}
        ]
      }]
    })
    await nextTick()

    expect(wrapper.text()).toContain('后续消息')
  })
})

describe('BlockRenderer active response state', () => {
  const completedToolPath = [
    {type: 'reasoning', content: '正在检查文件', showContent: false},
    {type: 'tool_call', name: 'read', status: '成功', args: {}, result: 'ok'}
  ]

  it('keeps the trailing tool path running until a terminal response arrives', () => {
    const wrapper = mount(BlockRenderer, {
      props: {blocks: [...completedToolPath, {type: 'content', content: ''}], streaming: true}
    })

    expect(wrapper.find('.tool-icon .animate-spin').exists()).toBe(true)
  })

  it('treats non-empty content, finish, and ask_choice as terminal responses', async () => {
    const wrapper = mount(BlockRenderer, {
      props: {
        blocks: [...completedToolPath, {type: 'content', content: '已完成'}],
        streaming: true
      }
    })

    expect(wrapper.find('.tool-icon .animate-spin').exists()).toBe(false)

    await wrapper.setProps({
      blocks: [...completedToolPath, {type: 'tool_call', name: 'finish', status: '成功', result: '已完成'}]
    })
    expect(wrapper.find('.tool-icon .animate-spin').exists()).toBe(false)

    await wrapper.setProps({
      blocks: [...completedToolPath, {type: 'tool_call', name: 'ask_choice', status: '成功', result: '请选择'}]
    })
    expect(wrapper.find('.tool-icon .animate-spin').exists()).toBe(false)
  })

  it('shows persisted durations in milliseconds below one second', async () => {
    const wrapper = mount(BlockRenderer, {
      props: {
        blocks: [{type: 'tool_call', name: 'read', status: '成功', args: {}, result: 'ok', toolDurationMs: 640}]
      }
    })

    await wrapper.find('.tool-head').trigger('click')
    expect(wrapper.find('.tool-duration').text()).toBe('640ms')
  })
})
