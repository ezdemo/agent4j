/* @vitest-environment jsdom */
import {describe, expect, it} from 'vitest'
import {
  UPDATE_SOURCE_MIRROR,
  UPDATE_SOURCE_NORMAL,
  buildUpdateCommand,
  buildUpdatePrompt,
  loadUpdateSource,
  saveUpdateSource,
  updateScriptName
} from './updateScripts'

describe('updateScriptName', () => {
  it('builds web scripts for the normal source', () => {
    expect(updateScriptName(UPDATE_SOURCE_NORMAL, false, 'ps1')).toBe('setup.ps1')
    expect(updateScriptName(UPDATE_SOURCE_NORMAL, false, 'sh')).toBe('setup.sh')
  })

  it('builds mirror scripts for the mirror source', () => {
    expect(updateScriptName(UPDATE_SOURCE_MIRROR, false, 'ps1')).toBe('setup-mirror.ps1')
    expect(updateScriptName(UPDATE_SOURCE_MIRROR, false, 'sh')).toBe('setup-mirror.sh')
  })

  it('builds gui scripts for the desktop runtime (Electron)', () => {
    expect(updateScriptName(UPDATE_SOURCE_NORMAL, true, 'ps1')).toBe('setup-gui.ps1')
    expect(updateScriptName(UPDATE_SOURCE_NORMAL, true, 'sh')).toBe('setup-gui.sh')
    expect(updateScriptName(UPDATE_SOURCE_MIRROR, true, 'ps1')).toBe('setup-gui-mirror.ps1')
    expect(updateScriptName(UPDATE_SOURCE_MIRROR, true, 'sh')).toBe('setup-gui-mirror.sh')
  })
})

describe('buildUpdateCommand', () => {
  it('returns windows and unix commands with short labels', () => {
    const cmd = buildUpdateCommand(UPDATE_SOURCE_NORMAL, false)
    expect(cmd.windows).toBe('irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.ps1 | iex')
    expect(cmd.windowsLabel).toBe('irm ...setup.ps1 | iex')
    expect(cmd.unix).toBe('curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.sh | bash')
    expect(cmd.unixLabel).toBe('curl ...setup.sh | bash')
  })

  it('switches to mirror scripts when the mirror source is selected', () => {
    const cmd = buildUpdateCommand(UPDATE_SOURCE_MIRROR, true)
    expect(cmd.windows).toContain('setup-gui-mirror.ps1')
    expect(cmd.unix).toContain('setup-gui-mirror.sh')
  })
})

describe('buildUpdatePrompt', () => {
  it('includes the electron gui script commands for the desktop runtime', () => {
    const prompt = buildUpdatePrompt(UPDATE_SOURCE_NORMAL, true)
    expect(prompt).toContain('setup-gui.ps1')
    expect(prompt).toContain('setup-gui.sh')
    expect(prompt).toContain('~/.loopra-gui')
  })

  it('switches to mirror scripts when the mirror source is selected', () => {
    const prompt = buildUpdatePrompt(UPDATE_SOURCE_MIRROR, true)
    expect(prompt).toContain('setup-gui-mirror.ps1')
    expect(prompt).toContain('setup-gui-mirror.sh')
  })

  it('uses plain setup scripts for the web runtime', () => {
    const prompt = buildUpdatePrompt(UPDATE_SOURCE_NORMAL, false)
    expect(prompt).toContain('setup.ps1')
    expect(prompt).not.toContain('~/.loopra-gui')
  })
})

describe('update source persistence', () => {
  it('defaults to the normal source', () => {
    localStorage.clear()
    expect(loadUpdateSource()).toBe(UPDATE_SOURCE_NORMAL)
  })

  it('persists the mirror source and restores it', () => {
    localStorage.clear()
    saveUpdateSource(UPDATE_SOURCE_MIRROR)
    expect(loadUpdateSource()).toBe(UPDATE_SOURCE_MIRROR)
  })

  it('resets to the normal source', () => {
    localStorage.clear()
    saveUpdateSource(UPDATE_SOURCE_MIRROR)
    saveUpdateSource(UPDATE_SOURCE_NORMAL)
    expect(loadUpdateSource()).toBe(UPDATE_SOURCE_NORMAL)
  })
})
