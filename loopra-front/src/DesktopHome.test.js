/* @vitest-environment jsdom */

import {flushPromises, shallowMount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import DesktopHome from './DesktopHome.vue'
import {sessionsAPI} from './services/api'

vi.mock('./services/api', () => ({
  sessionsAPI: {
    list: vi.fn().mockResolvedValue({success: true, data: []}),
    renameSession: vi.fn().mockResolvedValue({success: true, data: '新名称'})
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

    menu.querySelectorAll('button')[2].click()
    await flushPromises()
    expect(wrapper.emitted('clear-old-sessions')).toBeTruthy()
    expect(wrapper.emitted('clear-old-sessions')[0][0]).toEqual({hash: 'h1', name: 'A', path: '/p/a'})
  })
})

describe('DesktopHome 项目多选删除', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mountHome()
  })

  afterEach(() => {
    wrapper.unmount()
  })

  it('未开启多选时不渲染复选框，点击开关后进入多选模式', async () => {
    await flushPromises()
    expect(wrapper.find('.desktop-project-check').exists()).toBe(false)

    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    expect(wrapper.findAll('.desktop-project-check')).toHaveLength(3)
    // 多选模式下隐藏刷新/添加按钮
    expect(wrapper.find('.desktop-add-project').exists()).toBe(false)
    expect(wrapper.find('.desktop-multi-toggle-project').classes()).toContain('active')
  })

  it('勾选复选框后出现删除选中按钮，点击发出 delete-workspaces（含全部选中项）', async () => {
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    const projects = wrapper.findAll('.desktop-project')

    await projects[0].find('.desktop-project-check').trigger('click')
    await projects[2].find('.desktop-project-check').trigger('click')
    expect(projects[0].classes()).toContain('selected')
    expect(projects[2].classes()).toContain('selected')
    expect(projects[1].classes()).not.toContain('selected')

    const deleteButton = wrapper.find('.desktop-delete-selected')
    expect(deleteButton.exists()).toBe(true)
    expect(deleteButton.text()).toContain('2')

    await deleteButton.trigger('click')
    expect(wrapper.emitted('delete-workspaces')[0][0]).toEqual([
      {hash: 'h1', name: 'A', path: '/p/a'},
      {hash: 'h3', name: 'C', path: '/p/c'}
    ])
  })

  it('再次点击复选框取消勾选，关闭开关退出多选并清空全部选中', async () => {
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    const projects = wrapper.findAll('.desktop-project')

    await projects[0].find('.desktop-project-check').trigger('click')
    await projects[1].find('.desktop-project-check').trigger('click')
    await projects[0].find('.desktop-project-check').trigger('click')
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('1')

    // 关闭开关：退出多选并清空，恢复刷新/添加按钮
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    expect(wrapper.find('.desktop-delete-selected').exists()).toBe(false)
    expect(wrapper.findAll('.desktop-project-check')).toHaveLength(0)
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(0)
    expect(wrapper.find('.desktop-add-project').exists()).toBe(true)
  })

  it('Shift+点击复选框按区间批量选中', async () => {
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    const projects = wrapper.findAll('.desktop-project')

    await projects[0].find('.desktop-project-check').trigger('click')
    await projects[2].find('.desktop-project-check').trigger('click', {shiftKey: true})
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(3)
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('3')
  })

  it('全选按钮全选全部项目，再次点击取消全选', async () => {
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')

    await wrapper.find('.desktop-select-all').trigger('click')
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(3)
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('3')
    expect(wrapper.find('.desktop-select-all').text()).toContain('取消全选')

    await wrapper.find('.desktop-select-all').trigger('click')
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(0)
    expect(wrapper.find('.desktop-delete-selected').exists()).toBe(false)
    expect(wrapper.find('.desktop-select-all').text()).toContain('全选')
  })

  it('全选后取消一个勾选，按钮恢复为全选', async () => {
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    await wrapper.find('.desktop-select-all').trigger('click')

    const projects = wrapper.findAll('.desktop-project')
    await projects[0].find('.desktop-project-check').trigger('click')
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(2)
    expect(wrapper.find('.desktop-select-all').text()).toContain('全选')
  })

  it('开启多选后点击整行与点击复选框效果一致（切换选择，不触发切换项目）', async () => {
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    const projects = wrapper.findAll('.desktop-project')

    // 点击复选框：切换选择
    await projects[1].find('.desktop-project-check').trigger('click')
    expect(projects[1].classes()).toContain('selected')
    expect(wrapper.emitted('select-workspace')).toBeUndefined()

    // 点击行主体：同样切换选择
    await projects[1].trigger('click')
    expect(projects[1].classes()).not.toContain('selected')
    expect(wrapper.emitted('select-workspace')).toBeUndefined()
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(0)

    await projects[1].trigger('click')
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(1)
    expect(wrapper.emitted('select-workspace')).toBeUndefined()
  })

  it('关闭多选后点击行主体恢复切换项目行为', async () => {
    await flushPromises()
    const projects = wrapper.findAll('.desktop-project')

    await projects[1].trigger('click')
    expect(wrapper.emitted('select-workspace')[0]).toEqual(['h2'])
    expect(wrapper.findAll('.desktop-project.selected')).toHaveLength(0)
  })

  it('项目从列表中移除后自动清理选中项（批量删除后不残留）', async () => {
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-project').trigger('click')
    const projects = wrapper.findAll('.desktop-project')

    await projects[0].find('.desktop-project-check').trigger('click')
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('1')

    await wrapper.setProps({workspaces: FIXTURES.filter((workspace) => workspace.hash !== 'h1')})
    expect(wrapper.find('.desktop-delete-selected').exists()).toBe(false)
    expect(wrapper.findAll('.desktop-project')).toHaveLength(2)
  })
})

describe('DesktopHome 会话多选删除', () => {
  let wrapper

  function mountWithSessions(sessions) {
    sessionsAPI.list.mockResolvedValue({success: true, data: sessions})
    wrapper = mountHome()
  }

  const SESSIONS = [
    {name: 's1', title: '会话一', mtime: Date.now()},
    {name: 's2', title: '会话二', mtime: Date.now() - 3600_000},
    {name: 's3', title: '会话三', mtime: Date.now() - 7200_000}
  ]

  afterEach(() => {
    wrapper.unmount()
  })

  it('未开启多选时不渲染会话复选框，点击开关后进入多选模式', async () => {
    mountWithSessions(SESSIONS)
    await flushPromises()
    expect(wrapper.find('.desktop-session-check').exists()).toBe(false)

    await wrapper.find('.desktop-multi-toggle-session').trigger('click')
    expect(wrapper.findAll('.desktop-session-check')).toHaveLength(9)
    // 多选模式下隐藏新建会话按钮
    expect(wrapper.text()).not.toContain('新建会话')
  })

  it('勾选会话后出现删除选中按钮，点击发出 delete-sessions（含全部选中项）', async () => {
    mountWithSessions(SESSIONS)
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-session').trigger('click')
    // 未选中项目时加载全部项目（h1/h2/h3）的会话，每项目 3 个
    const sessions = wrapper.findAll('.desktop-session')
    expect(sessions).toHaveLength(9)

    await sessions[0].find('.desktop-session-check').trigger('click')
    await sessions[8].find('.desktop-session-check').trigger('click')
    expect(sessions[0].classes()).toContain('selected')
    expect(sessions[8].classes()).toContain('selected')
    expect(sessions[1].classes()).not.toContain('selected')

    const deleteButton = wrapper.find('.desktop-delete-selected')
    expect(deleteButton.exists()).toBe(true)
    expect(deleteButton.text()).toContain('2')

    await deleteButton.trigger('click')
    const emitted = wrapper.emitted('delete-sessions')[0][0]
    expect(emitted).toHaveLength(2)
    expect(emitted[0]).toMatchObject({workspaceHash: 'h1', name: 's1', title: '会话一'})
    expect(emitted[1]).toMatchObject({workspaceHash: 'h3', name: 's3', title: '会话三'})
  })

  it('Shift+点击会话复选框按显示顺序区间选中（跨分组）', async () => {
    // s1/s2 今天，s3 三天前 → 两个分组
    mountWithSessions([
      {name: 's1', title: '会话一', mtime: Date.now()},
      {name: 's2', title: '会话二', mtime: Date.now() - 4 * 86400_000},
      {name: 's3', title: '会话三', mtime: Date.now() - 5 * 86400_000}
    ])
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-session').trigger('click')
    const sessions = wrapper.findAll('.desktop-session')

    await sessions[2].find('.desktop-session-check').trigger('click')
    await sessions[0].find('.desktop-session-check').trigger('click', {shiftKey: true})
    expect(wrapper.findAll('.desktop-session.selected')).toHaveLength(3)
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('3')
  })

  it('全选按钮全选全部会话，再次点击取消全选', async () => {
    mountWithSessions(SESSIONS)
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-session').trigger('click')

    await wrapper.find('.desktop-select-all').trigger('click')
    expect(wrapper.findAll('.desktop-session.selected')).toHaveLength(9)
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('9')
    expect(wrapper.find('.desktop-select-all').text()).toContain('取消全选')

    await wrapper.find('.desktop-select-all').trigger('click')
    expect(wrapper.findAll('.desktop-session.selected')).toHaveLength(0)
    expect(wrapper.find('.desktop-delete-selected').exists()).toBe(false)
    expect(wrapper.find('.desktop-select-all').text()).toContain('全选')
  })

  it('开启多选后点击整行与点击复选框效果一致（切换选择，不打开会话）', async () => {
    mountWithSessions(SESSIONS)
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-session').trigger('click')
    const sessions = wrapper.findAll('.desktop-session')

    // 点击复选框：切换选择
    await sessions[4].find('.desktop-session-check').trigger('click')
    expect(sessions[4].classes()).toContain('selected')
    expect(wrapper.emitted('open-session')).toBeUndefined()

    // 点击行主体：同样切换选择
    await sessions[4].trigger('click')
    expect(sessions[4].classes()).not.toContain('selected')
    expect(wrapper.emitted('open-session')).toBeUndefined()
    expect(wrapper.findAll('.desktop-session.selected')).toHaveLength(0)

    await sessions[4].trigger('click')
    expect(wrapper.findAll('.desktop-session.selected')).toHaveLength(1)
    expect(wrapper.emitted('open-session')).toBeUndefined()
  })

  it('关闭多选后点击会话行主体恢复打开会话', async () => {
    mountWithSessions(SESSIONS)
    await flushPromises()
    const sessions = wrapper.findAll('.desktop-session')

    await sessions[4].trigger('click')
    expect(wrapper.emitted('open-session')[0][0]).toMatchObject({workspaceHash: 'h2', sessionName: 's2'})
    expect(wrapper.findAll('.desktop-session.selected')).toHaveLength(0)
  })

  it('关闭开关退出会话多选并清空全部选中，恢复新建会话按钮', async () => {
    mountWithSessions(SESSIONS)
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-session').trigger('click')
    const sessions = wrapper.findAll('.desktop-session')

    await sessions[0].find('.desktop-session-check').trigger('click')
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('1')
    await wrapper.find('.desktop-multi-toggle-session').trigger('click')
    expect(wrapper.find('.desktop-delete-selected').exists()).toBe(false)
    expect(wrapper.findAll('.desktop-session.selected')).toHaveLength(0)
    expect(wrapper.findAll('.desktop-session-check')).toHaveLength(0)
    expect(wrapper.text()).toContain('新建会话')
  })

  it('会话列表刷新后自动清理已被删除的勾选（不残留）', async () => {
    mountWithSessions(SESSIONS)
    await flushPromises()
    await wrapper.find('.desktop-multi-toggle-session').trigger('click')
    const sessions = wrapper.findAll('.desktop-session')
    await sessions[0].find('.desktop-session-check').trigger('click')
    await sessions[1].find('.desktop-session-check').trigger('click')
    expect(wrapper.find('.desktop-delete-selected').text()).toContain('2')

    // 模拟删除后刷新：每个项目只剩 s3（共 3 个）
    sessionsAPI.list.mockResolvedValue({success: true, data: [SESSIONS[2]]})
    await wrapper.setProps({refreshKey: 1})
    await flushPromises()
    expect(wrapper.find('.desktop-delete-selected').exists()).toBe(false)
    expect(wrapper.findAll('.desktop-session')).toHaveLength(3)
  })
})

describe('DesktopHome 会话重命名', () => {
  let wrapper

  beforeEach(() => {
    sessionsAPI.renameSession.mockClear()
  })

  function mountWithSessions(sessions) {
    sessionsAPI.list.mockResolvedValue({success: true, data: sessions})
    wrapper = mountHome()
  }

  afterEach(() => {
    wrapper.unmount()
  })

  it('会话右键菜单提供重命名入口，弹窗预填当前显示名称', async () => {
    mountWithSessions([{name: 's1', title: '会话一', mtime: Date.now()}])
    await flushPromises()
    await wrapper.find('.desktop-session').trigger('contextmenu', {clientX: 200, clientY: 200})

    const menu = document.body.querySelector('.desktop-context-menu')
    expect(menu).not.toBeNull()
    expect(menu.textContent).toContain('重命名会话')

    const renameButton = [...menu.querySelectorAll('button')].find((b) => b.textContent.includes('重命名会话'))
    await renameButton.click()
    await flushPromises()

    const dialog = document.body.querySelector('.desktop-rename-dialog')
    expect(dialog).not.toBeNull()
    expect(dialog.querySelector('input').value).toBe('会话一')
  })

  it('确认重命名：调用 renameSession 并发出 session-renamed / refresh', async () => {
    mountWithSessions([{name: 's1', title: '会话一', mtime: Date.now()}])
    await flushPromises()
    await wrapper.find('.desktop-session').trigger('contextmenu', {clientX: 200, clientY: 200})
    const menu = document.body.querySelector('.desktop-context-menu')
    const renameButton = [...menu.querySelectorAll('button')].find((b) => b.textContent.includes('重命名会话'))
    await renameButton.click()
    await flushPromises()

    const input = document.body.querySelector('.desktop-rename-dialog input')
    input.value = '新名称'
    input.dispatchEvent(new Event('input'))
    const confirm = [...document.body.querySelectorAll('.desktop-rename-dialog button')].find((b) => b.textContent.includes('确定'))
    await confirm.click()
    await flushPromises()

    expect(sessionsAPI.renameSession).toHaveBeenCalledWith('s1', 'h1', '新名称')
    expect(wrapper.emitted('session-renamed')[0][0]).toEqual({workspaceHash: 'h1', sessionName: 's1', title: '新名称'})
    expect(wrapper.emitted('refresh')).toBeTruthy()
  })

  it('取消重命名不调用接口', async () => {
    mountWithSessions([{name: 's1', title: '会话一', mtime: Date.now()}])
    await flushPromises()
    await wrapper.find('.desktop-session').trigger('contextmenu', {clientX: 200, clientY: 200})
    const menu = document.body.querySelector('.desktop-context-menu')
    const renameButton = [...menu.querySelectorAll('button')].find((b) => b.textContent.includes('重命名会话'))
    await renameButton.click()
    await flushPromises()

    const cancel = [...document.body.querySelectorAll('.desktop-rename-dialog button')].find((b) => b.textContent.includes('取消'))
    await cancel.click()
    await flushPromises()

    expect(sessionsAPI.renameSession).not.toHaveBeenCalled()
    expect(document.body.querySelector('.desktop-rename-dialog')).toBeNull()
  })
})

describe('DesktopHome 会话列表时间字段', () => {
  let wrapper

  function mountWithSessions(sessions) {
    sessionsAPI.list.mockResolvedValue({success: true, data: sessions})
    wrapper = mountHome()
  }

  afterEach(() => {
    wrapper.unmount()
  })

  it('每行末尾显示时间：今天 HH:mm、昨天「昨天」、跨年 Y/M/D，title 为完整日期时间', async () => {
    const today = Date.now()
    const d = new Date(today)
    const pad = (n) => String(n).padStart(2, '0')
    mountWithSessions([
      {name: 'today', title: '今天会话', mtime: today},
      {name: 'yesterday', title: '昨天会话', mtime: today - 86400000},
      {name: 'old', title: '更早会话', mtime: new Date('2024-03-15T10:00:00').getTime()}
    ])
    await flushPromises()

    // 未选中项目时加载全部 3 个项目（h1/h2/h3），同一组内按时间降序、项目顺序稳定
    const times = wrapper.findAll('.desktop-session-time')
    expect(times).toHaveLength(9)
    const todayText = `${pad(d.getHours())}:${pad(d.getMinutes())}`
    expect(times[0].text()).toBe(todayText)
    expect(times[1].text()).toBe(todayText)
    expect(times[2].text()).toBe(todayText)
    expect(times[3].text()).toBe('昨天')
    expect(times[4].text()).toBe('昨天')
    expect(times[5].text()).toBe('昨天')
    expect(times[6].text()).toBe('2024/3/15')
    expect(times[8].text()).toBe('2024/3/15')
    expect(times[0].attributes('title')).toBe(`${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${todayText}`)
  })

  it('无 mtime 的会话不渲染时间字段', async () => {
    mountWithSessions([{name: 'no-time', title: '无时间'}])
    await flushPromises()
    // 每个项目都有一条无时间会话（共 3 行），但均不渲染时间
    expect(wrapper.findAll('.desktop-session-time')).toHaveLength(0)
    expect(wrapper.findAll('.desktop-session')).toHaveLength(3)
  })
})
