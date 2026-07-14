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
