/* @vitest-environment jsdom */

import {flushPromises, shallowMount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import DesktopHome from './DesktopHome.vue'

vi.mock('../services/api', () => ({
  sessionsAPI: {
    list: vi.fn().mockResolvedValue({success: true, data: []})
  }
}))

const FIXTURES = [
  {hash: 'h1', name: 'A', path: '/p/a'},
  {hash: 'h2', name: 'B', path: '/p/b'},
  {hash: 'h3', name: 'C', path: '/p/c'}
]

function mountHome(props) {
  return shallowMount(DesktopHome, {
    props: {
      workspaces: FIXTURES,
      activeWorkspaceHash: '',
      theme: 'gray',
      refreshKey: 0,
      refreshing: false,
      ...props
    },
    global: {stubs: {ServiceProcessManager: true, Teleport: false}}
  })
}

/** 模拟元素几何信息（jsdom 默认全 0），height 32 与样式定义一致 */
function mockRect(element, {top = 0, height = 32} = {}) {
  element.getBoundingClientRect = () => ({
    top, height, bottom: top + height,
    left: 0, right: 200, width: 200, x: 0, y: top,
    toJSON: () => ({})
  })
}

/** 在目标元素上派发冒泡到容器的拖拽事件 */
function dispatchDragEvent(element, type, clientY) {
  element.dispatchEvent(new MouseEvent(type, {bubbles: true, cancelable: true, clientY}))
}

function projectNames(wrapper) {
  return wrapper.findAll('.desktop-project').map((project) => {
    const spans = project.findAll('span')
    return spans[spans.length - 1].text()
  })
}

describe('DesktopHome 项目拖拽排序', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mountHome()
  })

  afterEach(() => {
    wrapper.unmount()
  })

  it('按 workspaces prop 顺序渲染项目', async () => {
    await flushPromises()
    expect(projectNames(wrapper)).toEqual(['A', 'B', 'C'])
  })

  it('左下角菜单精简：需求池/技能/设置文字入口 + 工具图标，子代理/数据面板已收进设置页', async () => {
    await flushPromises()
    const menuButtons = wrapper.findAll('.desktop-project-footer-menu > button')
    expect(menuButtons.map((button) => button.text().trim())).toEqual(['需求池', '技能'])
    expect(wrapper.find('.desktop-project-footer-settings').text()).toContain('设置')
    expect(wrapper.find('.desktop-sub-agents-button').exists()).toBe(true)
    expect(wrapper.find('.desktop-tools-button').exists()).toBe(true)
    expect(wrapper.find('.desktop-theme-button').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('子代理')
    expect(wrapper.text()).not.toContain('数据面板')
  })

  it('拖拽过程中列表保持不动，仅显示插入指示，drop 后按新顺序发出 reorder-workspaces', async () => {
    await flushPromises()
    const projects = wrapper.findAll('.desktop-project')

    // 开始拖拽 A
    await projects[0].trigger('dragstart')
    expect(wrapper.find('.desktop-project.dragging').exists()).toBe(true)
    // 悬停到 C 的下半部（before = false → 插入到 C 之后）
    mockRect(projects[2].element, {top: 0, height: 32})
    dispatchDragEvent(projects[2].element, 'dragover', 40)
    await flushPromises()

    // 拖动中列表不变，只出现行间指示
    expect(projectNames(wrapper)).toEqual(['A', 'B', 'C'])
    expect(projects[2].classes()).toContain('drag-over-after')

    await wrapper.find('.desktop-project-list').trigger('drop')
    expect(projectNames(wrapper)).toEqual(['B', 'C', 'A'])
    expect(wrapper.emitted('reorder-workspaces')).toBeTruthy()
    expect(wrapper.emitted('reorder-workspaces')[0]).toEqual([['h2', 'h3', 'h1']])
  })

  it('拖拽到另一项目上方时显示上侧指示，drop 后插入到其前', async () => {
    await flushPromises()
    const projects = wrapper.findAll('.desktop-project')

    await projects[2].trigger('dragstart')
    // 悬停到 A 的上半部（before = true → 插入到 A 之前）
    mockRect(projects[0].element, {top: 0, height: 32})
    dispatchDragEvent(projects[0].element, 'dragover', 4)
    await flushPromises()

    expect(projectNames(wrapper)).toEqual(['A', 'B', 'C'])
    expect(projects[0].classes()).toContain('drag-over-before')

    await wrapper.find('.desktop-project-list').trigger('drop')
    expect(projectNames(wrapper)).toEqual(['C', 'A', 'B'])
    expect(wrapper.emitted('reorder-workspaces')[0]).toEqual([['h3', 'h1', 'h2']])
  })

  it('拖到列表空白处 drop 后移到末尾', async () => {
    await flushPromises()
    const projects = wrapper.findAll('.desktop-project')

    await projects[1].trigger('dragstart')
    // 在容器空白处悬停（target 为容器自身），指示落在最后一项下方
    dispatchDragEvent(wrapper.find('.desktop-project-list').element, 'dragover', 999)
    await flushPromises()

    expect(projectNames(wrapper)).toEqual(['A', 'B', 'C'])
    expect(projects[2].classes()).toContain('drag-over-after')

    await wrapper.find('.desktop-project-list').trigger('drop')
    expect(projectNames(wrapper)).toEqual(['A', 'C', 'B'])
    expect(wrapper.emitted('reorder-workspaces')[0]).toEqual([['h1', 'h3', 'h2']])
  })

  it('拖起后放回原位不发出排序事件', async () => {
    await flushPromises()
    const projects = wrapper.findAll('.desktop-project')

    await projects[1].trigger('dragstart')
    await wrapper.find('.desktop-project-list').trigger('drop')

    expect(projectNames(wrapper)).toEqual(['A', 'B', 'C'])
    expect(wrapper.emitted('reorder-workspaces')).toBeUndefined()
  })

  it('未拖拽时 drop 不发出排序事件', async () => {
    await flushPromises()
    await wrapper.find('.desktop-project-list').trigger('drop')
    expect(wrapper.emitted('reorder-workspaces')).toBeUndefined()
  })
})

describe('DesktopHome 项目右键菜单', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mountHome()
  })

  afterEach(() => {
    wrapper.unmount()
  })

  it('项目右键提供清空会话/清空三天前的会话/删除项目，点击发出 clear-old-sessions', async () => {
    await flushPromises()
    await wrapper.find('.desktop-project').trigger('contextmenu', {clientX: 200, clientY: 200})

    const menu = document.body.querySelector('.desktop-context-menu')
    expect(menu).not.toBeNull()
    expect(menu.textContent).toContain('清空会话')
    expect(menu.textContent).toContain('清空三天前的会话')
    expect(menu.textContent).toContain('删除项目')

    menu.querySelectorAll('button')[1].click()
    await flushPromises()
    expect(wrapper.emitted('clear-old-sessions')).toBeTruthy()
    expect(wrapper.emitted('clear-old-sessions')[0][0]).toEqual({hash: 'h1', name: 'A', path: '/p/a'})
  })
})
