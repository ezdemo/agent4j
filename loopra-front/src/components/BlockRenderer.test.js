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

  it('merges reasoning-started placeholders into the normal execution group', async () => {
    const wrapper = mount(BlockRenderer, {
      props: {
        blocks: [
          {type: 'reasoning_started', content: 'must-not-render', showContent: false},
          {type: 'tool_call', name: 'read', status: '成功', args: {}, result: 'ok'},
          {type: 'reasoning_started', showContent: false}
        ],
        streaming: true
      }
    })

    expect(wrapper.find('.path-steps').text()).toBe('思考2轮.读1次文件')
    expect(wrapper.find('.reasoning-head').exists()).toBe(false)

    await wrapper.find('.tool-head').trigger('click')
    expect(wrapper.findAll('.reasoning-head')).toHaveLength(2)

    await wrapper.find('.reasoning-head').trigger('click')
    expect(wrapper.find('.reasoning-text').text()).toBe('加密思考')
    expect(wrapper.text()).not.toContain('must-not-render')
  })

  it('shows categorized tool stats in the path group header', () => {
    const wrapper = mount(BlockRenderer, {
      props: {
        blocks: [
          {type: 'reasoning', content: 'x', showContent: false},
          {type: 'tool_call', name: 'read', status: '成功', args: {}, result: 'ok'},
          {type: 'tool_call', name: 'write', status: '成功', args: {}, result: 'ok'},
          {type: 'tool_call', name: 'bash_start', status: '成功', args: {}, result: 'ok'},
          {type: 'tool_call', name: 'memory', status: '成功', args: {}, result: 'ok'},
          {type: 'tool_call', name: 'sub_agent', status: '成功', args: {}, result: 'ok'},
          {type: 'tool_call', name: 'ask_choice', status: '成功', args: {}, result: 'ok'}
        ]
      }
    })

    expect(wrapper.find('.path-steps').text())
      .toBe('思考1轮.读1次文件、改1次文件、执行1次命令、记忆1次、子代理1次、其他1次')
  })

  it('merges reasoning blocks separated by blank content into one folded group', () => {
    const wrapper = mount(BlockRenderer, {
      props: {
        blocks: [
          {type: 'reasoning', content: '第一段思考', showContent: false},
          {type: 'content', content: '\n\n'},
          {type: 'reasoning', content: '第二段思考', showContent: false}
        ]
      }
    })

    // 空白正文不拆散连续思考：合并为一个折叠组，标题显示 思考2轮
    expect(wrapper.findAll('.block-tool')).toHaveLength(1)
    expect(wrapper.find('.path-steps').text()).toBe('思考2轮')
    // 空白正文块不渲染
    expect(wrapper.find('.block-content').exists()).toBe(false)
  })
})
