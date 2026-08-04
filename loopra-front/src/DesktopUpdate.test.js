/* @vitest-environment jsdom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createPinia} from 'pinia'
import DesktopUpdate from './DesktopUpdate.vue'

vi.mock('./services/api', () => ({
  systemAPI: {
    getCurrentVersion: vi.fn().mockResolvedValue({success: true, data: {version: '1.0.0'}}),
    checkLatestVersion: vi.fn().mockResolvedValue({success: true, data: {hasNewVersion: false, latestVersion: '1.0.0', releaseUrl: ''}})
  }
}))

describe('DesktopUpdate footer buttons (web)', () => {
  it('renders 检查更新 and 更新核心服务 buttons without electron API', async () => {
    const wrapper = mount(DesktopUpdate, {
      global: {plugins: [createPinia()]}
    })
    await wrapper.vm.$nextTick()
    const footer = wrapper.find('.du-footer')
    expect(footer.exists()).toBe(true)
    const text = footer.text()
    expect(text).toContain('检查更新')
    expect(text).toContain('更新核心服务')
    expect(text).toContain('下载桌面端（推荐）')
    expect(text).toContain('更新将在聊天框中执行')
    // 推荐按钮使用独立高亮样式
    expect(wrapper.find('.du-footer .btn-recommend').exists()).toBe(true)
    expect(wrapper.find('.du-footer .btn-recommend').text()).toContain('下载桌面端（推荐）')
  })

  it('shows 有新版本 badge on core service button when core has updates', async () => {
    const wrapper = mount(DesktopUpdate, {
      global: {plugins: [createPinia()]}
    })
    await wrapper.vm.$nextTick()
    // 模拟核心服务有新版本（onMounted 的检查返回 hasNewVersion）
    wrapper.vm.hasNewVersion = true
    await wrapper.vm.$nextTick()
    const badges = wrapper.findAll('.du-footer .btn-update-badge')
    expect(badges.length).toBe(1)
    expect(badges[0].text()).toContain('新版')
    // 与桌面端按钮一致：核心服务按钮整体蓝色高亮
    const coreButton = wrapper.findAll('.du-footer .btn-update-highlight')
    expect(coreButton.length).toBe(1)
    expect(coreButton[0].text()).toContain('更新核心服务')
  })

  it('does not render desktop-only button label on web', async () => {
    const wrapper = mount(DesktopUpdate, {
      global: {plugins: [createPinia()]}
    })
    await wrapper.vm.$nextTick()
    const text = wrapper.find('.du-footer').text()
    expect(text).not.toContain('更新桌面端')
  })
})
