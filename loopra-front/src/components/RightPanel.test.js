/* @vitest-environment jsdom */

import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it} from 'vitest'
import {nextTick} from 'vue'
import RightPanel from './RightPanel.vue'

const originalInnerWidth = window.innerWidth

function mountPanel(props = {}) {
  return shallowMount(RightPanel, {
    props: {
      open: true,
      modelValue: 'schedule',
      showFilesTab: false,
      showGitTab: false,
      ...props
    }
  })
}

beforeEach(() => {
  localStorage.clear()
  Object.defineProperty(window, 'innerWidth', {configurable: true, value: 1000})
})

afterEach(() => {
  Object.defineProperty(window, 'innerWidth', {configurable: true, value: originalInnerWidth})
})

describe('RightPanel 拖拽调整宽度', () => {
  it('仅在启用时显示手柄，向左拖动增宽并保存宽度', async () => {
    const fixedPanel = mountPanel()
    expect(fixedPanel.find('.rp-resize-handle').exists()).toBe(false)
    fixedPanel.unmount()

    const wrapper = mountPanel({resizable: true})
    const handle = wrapper.find('.rp-resize-handle')
    handle.element.dispatchEvent(new MouseEvent('mousedown', {
      bubbles: true,
      cancelable: true,
      clientX: 700
    }))
    window.dispatchEvent(new MouseEvent('mousemove', {clientX: 600}))
    await nextTick()

    expect(wrapper.find('.rp-panel').attributes('style')).toContain('width: 420px')

    window.dispatchEvent(new MouseEvent('mouseup'))
    expect(localStorage.getItem('loopra-right-panel-width')).toBe('420')
    wrapper.unmount()
  })
})
