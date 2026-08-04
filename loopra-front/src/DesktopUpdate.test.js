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
    expect(text).toContain('更新将在聊天框中执行')
  })

  it('does not render desktop-only buttons on web', async () => {
    const wrapper = mount(DesktopUpdate, {
      global: {plugins: [createPinia()]}
    })
    await wrapper.vm.$nextTick()
    const text = wrapper.find('.du-footer').text()
    expect(text).not.toContain('更新桌面端')
  })
})
