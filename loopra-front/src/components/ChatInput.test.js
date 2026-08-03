/* @vitest-environment jsdom */

import {flushPromises, shallowMount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {promptPresetsAPI} from '../services/api'
import ChatInput from './ChatInput.vue'

Object.defineProperty(Element.prototype, 'scrollIntoView', {
  configurable: true,
  value: vi.fn()
})

vi.mock('../stores/app', () => ({
  useAppStore: () => ({desktopPetVisible: false, activePetName: ''})
}))

vi.mock('../services/api', () => ({
  agentAPI: {
    getCommands: vi.fn().mockResolvedValue({success: true, data: []}),
    getSkills: vi.fn().mockResolvedValue({success: true, data: []})
  },
  filesAPI: {search: vi.fn().mockResolvedValue({success: true, data: []})},
  petAPI: {
    getInfo: vi.fn().mockResolvedValue({data: null}),
    resolveUrl: vi.fn((url) => url),
    savePosition: vi.fn().mockResolvedValue({success: true})
  },
  promptPresetsAPI: {
    list: vi.fn().mockResolvedValue({
      success: true,
      data: [
        {id: 'quick-test', label: '要求测试', text: '请先运行与本次修改相关的测试，确认通过后再报告结果。'}
      ]
    }),
    save: vi.fn().mockResolvedValue({success: true})
  }
}))

function mountInput(props) {
  return shallowMount(ChatInput, {
    props: {
      currentModel: 'gpt-5.6-terra',
      defaultModel: 'gpt-5.6-terra',
      defaultModelChannelId: 'bearjia',
      availableModels: [
        {name: 'gpt-5.6-terra', channelId: 'bearjia', channelName: 'bearjia', active: true},
        {name: 'gpt-image-1', channelId: 'bearjia', channelName: 'bearjia', active: false}
      ],
      ...props
    },
    global: {stubs: {PetSprite: true, ChecklistSteps: true}}
  })
}

describe('ChatInput quick commands', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('keeps the preset list empty when no presets can be loaded', async () => {
    promptPresetsAPI.list.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountInput()
    await flushPromises()
    await wrapper.find('.quick-command-trigger').trigger('click')

    expect(wrapper.find('.quick-command-list').exists()).toBe(false)
    expect(wrapper.find('.quick-command-empty').text()).toBe('还没有常用要求')
    wrapper.unmount()
  })

  it('appends a preset command to the input', async () => {
    const wrapper = mountInput({inputText: '已有内容'})
    await flushPromises()
    await wrapper.find('.quick-command-trigger').trigger('click')
    await wrapper.find('.quick-command-copy').trigger('click')

    expect(wrapper.find('.input-row textarea').element.value).toBe(
      '已有内容\n请先运行与本次修改相关的测试，确认通过后再报告结果。'
    )
    expect(wrapper.emitted('update:inputText').at(-1)).toEqual([
      '已有内容\n请先运行与本次修改相关的测试，确认通过后再报告结果。'
    ])
    wrapper.unmount()
  })

  it('persists a newly added preset', async () => {
    const wrapper = mountInput()
    await wrapper.find('.quick-command-trigger').trigger('click')
    await wrapper.find('.quick-command-add').trigger('click')
    const fields = wrapper.findAll('.quick-command-form input, .quick-command-form textarea')
    await fields[0].setValue('检查状态')
    await fields[1].setValue('请检查当前状态并报告结果')
    await wrapper.find('.quick-command-form').trigger('submit')

    expect(promptPresetsAPI.save).toHaveBeenCalledWith(
      expect.arrayContaining([
        expect.objectContaining({label: '检查状态', text: '请检查当前状态并报告结果'})
      ])
    )
    wrapper.unmount()
  })
})

describe('ChatInput reasoning effort', () => {
  it('emits the selected reasoning effort', async () => {
    const wrapper = mountInput({currentReasoningEffort: 'max'})

    await wrapper.find('.model-actions > .reasoning-effort-selector .effort-btn').trigger('click')
    await wrapper.findAll('.chat-reasoning-levels button')[1].trigger('click')

    expect(wrapper.emitted('switchReasoningEffort')).toEqual([['low']])
    wrapper.unmount()
  })
})

describe('ChatInput default model actions', () => {
  it('renders queued messages behind a compact summary and preserves queue actions', async () => {
    const wrapper = mountInput({
      queuedMessages: [{id: 'queued-1', text: '继续处理当前任务'}]
    })

    expect(wrapper.find('.composer-queue-summary').text()).toBe('排队消息 1 条')
    expect(wrapper.find('.composer-queue-items-content').exists()).toBe(true)

    await wrapper.find('.composer-queue-guide').trigger('click')
    expect(wrapper.emitted('guideQueued')).toEqual([['queued-1']])

    await wrapper.find('.composer-queue-remove').trigger('click')
    expect(wrapper.emitted('removeQueued')).toEqual([['queued-1']])
    wrapper.unmount()
  })

  it('emits the selected row model and channel when setting a default model', async () => {
    const wrapper = mountInput()

    await wrapper.find('.model-btn').trigger('click')
    const buttons = wrapper.findAll('.model-default-action')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].attributes('disabled')).toBeDefined()

    await buttons[1].trigger('click')
    expect(wrapper.emitted('setDefaultModel')).toEqual([['gpt-image-1', 'bearjia']])
    wrapper.unmount()
  })
})
