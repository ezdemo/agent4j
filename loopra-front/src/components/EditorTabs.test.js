// @vitest-environment jsdom

import {mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import EditorTabs from './EditorTabs.vue'

const tabs = [
  {id: 'chat', label: '对话', closable: false},
  {id: 'file-1', label: 'first.js', fileIcon: {kind: 'javascript', color: '#cbcb41', icon: 'codicon-file-code'}, dirty: true},
  {id: 'file-2', label: 'second.js', fileIcon: {kind: 'javascript', color: '#cbcb41', icon: 'codicon-file-code'}}
]

describe('EditorTabs', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      disconnect() {}
    })
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('keeps the conversation tab outside the file tab scroller', () => {
    const wrapper = mount(EditorTabs, {props: {tabs, activeId: 'chat'}})

    expect(wrapper.find('.et-fixed-tab').text()).toBe('对话')
    expect(wrapper.find('.et-scroll').text()).not.toContain('对话')
    expect(wrapper.findAll('.et-scroll .et-tab')).toHaveLength(2)
    expect(wrapper.find('.et-scroll .et-file-icon').attributes('data-icon')).toBe('javascript')
    expect(wrapper.find('.et-scroll .et-file-icon').classes()).toContain('codicon-file-code')
    expect(wrapper.find('.et-close.is-dirty .et-dirty-dot').exists()).toBe(true)
  })

  it('uses the mouse wheel to scroll file tabs horizontally', async () => {
    const wrapper = mount(EditorTabs, {props: {tabs, activeId: 'file-1'}})
    const scroller = wrapper.find('.et-scroll').element
    Object.defineProperties(scroller, {
      clientWidth: {value: 200},
      scrollWidth: {value: 600}
    })

    await wrapper.find('.et-scroll').trigger('wheel', {deltaY: 120})

    expect(scroller.scrollLeft).toBe(120)
  })
})
