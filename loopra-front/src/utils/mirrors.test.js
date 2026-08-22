/* @vitest-environment jsdom */
import {describe, expect, it, vi, beforeEach} from 'vitest'
import {
  MIRROR_SOURCES,
  applyLatencies,
  loadCachedLatencies,
  loadSelectedMirrorSource,
  measureMirrors,
  saveLatencies,
  saveSelectedMirrorSource,
  sortMirrors
} from './mirrors'

describe('MIRROR_SOURCES', () => {
  it('has the { value, label, latency } structure with latency null initially', () => {
    for (const m of MIRROR_SOURCES) {
      expect(typeof m.value).toBe('string')
      expect(m.value.startsWith('https://')).toBe(true)
      expect(typeof m.label).toBe('string')
      expect(m.latency).toBeNull()
    }
  })

  it('contains no duplicate values', () => {
    const values = MIRROR_SOURCES.map((m) => m.value)
    expect(new Set(values).size).toBe(values.length)
  })
})

describe('sortMirrors', () => {
  it('sorts by latency ascending with nulls at the end', () => {
    const list = [
      { value: 'a', latency: null },
      { value: 'b', latency: 2000 },
      { value: 'c', latency: 500 },
      { value: 'd', latency: null }
    ]
    const sorted = sortMirrors(list)
    expect(sorted.map((m) => m.value)).toEqual(['c', 'b', 'a', 'd'])
  })

  it('does not mutate the input list', () => {
    const list = [{ value: 'a', latency: 5 }, { value: 'b', latency: 1 }]
    sortMirrors(list)
    expect(list[0].value).toBe('a')
  })
})

describe('applyLatencies', () => {
  it('fills latency from the measured list by value', () => {
    const result = applyLatencies(
      [{ value: 'https://a/', label: 'a', latency: null }],
      [{ value: 'https://a/', latency: 123 }]
    )
    expect(result[0].latency).toBe(123)
  })

  it('keeps latency null for mirrors without a measurement', () => {
    const result = applyLatencies(
      [{ value: 'https://a/', label: 'a', latency: null }],
      []
    )
    expect(result[0].latency).toBeNull()
  })
})

describe('mirror source persistence', () => {
  beforeEach(() => localStorage.clear())

  it('defaults to normal', () => {
    expect(loadSelectedMirrorSource()).toBe('normal')
  })

  it('persists a selected mirror and restores it', () => {
    saveSelectedMirrorSource(MIRROR_SOURCES[0].value)
    expect(loadSelectedMirrorSource()).toBe(MIRROR_SOURCES[0].value)
  })

  it('ignores unknown values and falls back to normal', () => {
    saveSelectedMirrorSource('https://not-in-list.example/')
    expect(loadSelectedMirrorSource()).toBe('normal')
  })
})

describe('latency cache', () => {
  beforeEach(() => localStorage.clear())

  it('returns null when nothing is cached', () => {
    expect(loadCachedLatencies()).toBeNull()
  })

  it('returns cached latencies within the TTL', () => {
    saveLatencies([{ value: 'https://a/', latency: 100 }])
    const cached = loadCachedLatencies()
    expect(cached).toEqual([{ value: 'https://a/', latency: 100 }])
  })
})

describe('measureMirrors', () => {
  beforeEach(() => {
    localStorage.clear()
    delete window.electronAPI
  })

  it('uses the main-process IPC when available and returns sorted results', async () => {
    window.electronAPI = {
      mirror: {
        measureLatencies: vi.fn().mockResolvedValue({
          success: true,
          list: [
            { value: 'https://slow/', latency: 3000 },
            { value: 'https://fast/', latency: 100 },
            { value: 'https://dead/', latency: null }
          ]
        })
      }
    }
    const mirrors = [
      { value: 'https://slow/', label: 'slow', latency: null },
      { value: 'https://fast/', label: 'fast', latency: null },
      { value: 'https://dead/', label: 'dead', latency: null }
    ]
    const sorted = await measureMirrors(mirrors)
    expect(sorted.map((m) => m.value)).toEqual(['https://fast/', 'https://slow/', 'https://dead/'])
    expect(sorted[0].latency).toBe(100)
    expect(window.electronAPI.mirror.measureLatencies).toHaveBeenCalledTimes(1)
  })

  it('falls back to fetch when no electron API is present', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 206, body: null })
    global.fetch = fetchMock
    const mirrors = [{ value: 'https://m/', label: 'm', latency: null }]
    const sorted = await measureMirrors(mirrors)
    expect(fetchMock).toHaveBeenCalled()
    expect(sorted[0].latency).toBeGreaterThanOrEqual(0)
  })

  it('treats a failed fetch as null latency', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('network'))
    global.fetch = fetchMock
    const mirrors = [{ value: 'https://m/', label: 'm', latency: null }]
    const sorted = await measureMirrors(mirrors)
    expect(sorted[0].latency).toBeNull()
  })
})
