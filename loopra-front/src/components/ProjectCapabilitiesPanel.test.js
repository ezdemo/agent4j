/* @vitest-environment jsdom */

import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import ProjectCapabilitiesPanel from './ProjectCapabilitiesPanel.vue'

const {getProjectCapabilities, refreshProjectCapabilities} = vi.hoisted(() => ({
  getProjectCapabilities: vi.fn(),
  refreshProjectCapabilities: vi.fn()
}))

vi.mock('../services/api', () => ({
  agentAPI: {getProjectCapabilities, refreshProjectCapabilities}
}))

const capabilities = {
  workspacePath: 'C:/projects/demo',
  mcpConfigExists: true,
  projectSkillsDirectoryExists: true,
  skills: [
    {name: 'release-check', description: '发布前检查', scope: 'project', mountAlias: '@project-skills', path: 'C:/projects/demo/.loopra/skills/release-check'},
    {name: 'browser', description: '浏览器辅助', scope: 'user', mountAlias: '@loopra-skills', path: 'C:/Users/test/.loopra/skills/browser'}
  ],
  mcpServers: [
    {name: 'codegraph', type: 'stdio', enabled: true, loaded: true, toolCount: 2, toolNames: ['graph_query', 'graph_neighbors']},
    {name: 'disabled-server', type: 'sse', enabled: false, loaded: false, toolCount: 0, toolNames: []}
  ]
}

describe('ProjectCapabilitiesPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getProjectCapabilities.mockResolvedValue({success: true, data: capabilities})
    refreshProjectCapabilities.mockResolvedValue({success: true, data: capabilities})
  })

  it('loads project skills and MCP servers without displaying shared skills', async () => {
    const wrapper = mount(ProjectCapabilitiesPanel, {
      props: {workspaceHash: 'workspace-1', workspaceName: 'demo'}
    })
    await flushPromises()

    expect(getProjectCapabilities).toHaveBeenCalledWith('workspace-1')
    expect(wrapper.find('button.icon-button').exists()).toBe(true)
    expect(wrapper.text()).toContain('项目 Skill')
    expect(wrapper.text()).toContain('release-check')
    expect(wrapper.text()).not.toContain('共享 Skill')
    expect(wrapper.text()).not.toContain('browser')
    expect(wrapper.text()).toContain('codegraph')
    expect(wrapper.text()).toContain('已连接')
    expect(wrapper.text()).toContain('disabled-server')
    expect(wrapper.text()).toContain('已禁用')
  })

  it('refreshes against the new workspace and clears the old project data', async () => {
    const wrapper = mount(ProjectCapabilitiesPanel, {
      props: {workspaceHash: 'workspace-1', workspaceName: 'demo'}
    })
    await flushPromises()

    getProjectCapabilities.mockResolvedValue({
      success: true,
      data: {...capabilities, skills: [], mcpServers: [], mcpConfigExists: false, projectSkillsDirectoryExists: false}
    })
    await wrapper.setProps({workspaceHash: 'workspace-2', workspaceName: 'other'})
    await flushPromises()

    expect(getProjectCapabilities).toHaveBeenLastCalledWith('workspace-2')
    expect(wrapper.text()).toContain('未找到 .loopra/skills/')
    expect(wrapper.text()).toContain('未找到 .loopra/mcp-servers.json')
    expect(wrapper.text()).not.toContain('项目配置')
    expect(wrapper.text()).not.toContain('release-check')
  })

  it('reloads the project MCP runtime when the refresh button is clicked', async () => {
    const wrapper = mount(ProjectCapabilitiesPanel, {
      props: {workspaceHash: 'workspace-1', workspaceName: 'demo'}
    })
    await flushPromises()

    const refreshed = {...capabilities, mcpServers: [
      {name: 'new-server', type: 'streamable', enabled: true, loaded: true, toolCount: 1, toolNames: ['new_tool']}
    ]}
    refreshProjectCapabilities.mockResolvedValue({success: true, data: refreshed})
    await wrapper.find('button.icon-button').trigger('click')
    await flushPromises()

    expect(refreshProjectCapabilities).toHaveBeenCalledWith('workspace-1')
    expect(wrapper.text()).toContain('new-server')
    expect(wrapper.text()).not.toContain('codegraph')
  })
})
