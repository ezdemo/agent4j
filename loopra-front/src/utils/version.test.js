import {describe, expect, it} from 'vitest'
import versionModule from '../../electron/version.cjs'

const {compareVersions} = versionModule

describe('compareVersions', () => {
  it('treats a fourth numeric segment as a higher version', () => {
    expect(compareVersions('26.8.3.2', '26.8.3')).toBe(1)
    expect(compareVersions('26.8.3', '26.8.3.2')).toBe(-1)
  })

  it('treats missing trailing segments as zero', () => {
    expect(compareVersions('26.8.3', '26.8.3.0')).toBe(0)
  })

  it('accepts an optional v prefix', () => {
    expect(compareVersions('v26.8.3.2', '26.8.3')).toBe(1)
  })
})
