/* @vitest-environment jsdom */

import {shallowMount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'

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
    getSpritesheetUrl: vi.fn(),
    savePosition: vi.fn().mockResolvedValue({success: true})
  }
}))

import ChatInput from './ChatInput.vue'

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

describe('ChatInput default model actions', () => {
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
