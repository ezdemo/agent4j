import {describe, expect, it} from 'vitest'
import {hasConfiguredModelChannel} from './modelChannels'

describe('hasConfiguredModelChannel', () => {
  it('returns false until the backend confirms a real API key is configured', () => {
    expect(hasConfiguredModelChannel({})).toBe(false)
    expect(hasConfiguredModelChannel({modelChannelsConfigured: false})).toBe(false)
    expect(hasConfiguredModelChannel({
      modelChannelsConfigured: false,
      modelChannels: [{baseUrl: 'https://api.example.com', apiKey: '****', models: [{name: 'gpt-test'}]}]
    })).toBe(false)
  })

  it('uses the backend configuration status', () => {
    expect(hasConfiguredModelChannel({modelChannelsConfigured: true})).toBe(true)
  })
})
