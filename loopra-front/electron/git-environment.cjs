const { execFile } = require('child_process')
const { promisify } = require('util')

const execFileAsync = promisify(execFile)
const MAX_BUFFER = 16 * 1024 * 1024

async function runGit(cwd, args) {
  if (!cwd || typeof cwd !== 'string') throw new Error('Git 工作目录不能为空')
  const result = await execFileAsync('git', args, {
    cwd,
    windowsHide: true,
    maxBuffer: MAX_BUFFER
  })
  return {stdout: result.stdout || '', stderr: result.stderr || ''}
}

async function tryGit(cwd, args) {
  try {
    return {ok: true, ...(await runGit(cwd, args))}
  } catch (error) {
    return {
      ok: false,
      stdout: error.stdout || '',
      stderr: error.stderr || error.message || ''
    }
  }
}

function parseStatus(output) {
  const entries = []
  const parts = String(output || '').split('\0')
  for (let index = 0; index < parts.length; index += 1) {
    const item = parts[index]
    if (!item) continue
    const code = item.slice(0, 2)
    const path = item.slice(3)
    if (!path) continue
    const renamedPath = code.includes('R') || code.includes('C') ? parts[index + 1] : null
    if (renamedPath) index += 1
    entries.push({
      path: renamedPath ? `${path} → ${renamedPath}` : path,
      index: code[0] || ' ',
      workTree: code[1] || ' ',
      status: code
    })
  }
  return entries
}

function splitStatus(entries) {
  return {
    changed: entries.filter((entry) => entry.index !== '?' || entry.workTree !== '?'),
    untracked: entries.filter((entry) => entry.index === '?' && entry.workTree === '?')
  }
}

async function branchOf(cwd) {
  const symbolic = await tryGit(cwd, ['symbolic-ref', '--short', '-q', 'HEAD'])
  if (symbolic.ok && symbolic.stdout.trim()) return symbolic.stdout.trim()
  const detached = await tryGit(cwd, ['rev-parse', '--short', 'HEAD'])
  return detached.ok ? `HEAD (${detached.stdout.trim()})` : ''
}

async function trackingOf(cwd) {
  const result = await tryGit(cwd, ['rev-list', '--left-right', '--count', 'HEAD...@{upstream}'])
  if (!result.ok) return {ahead: 0, behind: 0, upstream: ''}
  const [behind, ahead] = result.stdout.trim().split(/\s+/).map((value) => Number(value) || 0)
  const upstream = await tryGit(cwd, ['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{upstream}'])
  return {ahead, behind, upstream: upstream.ok ? upstream.stdout.trim() : ''}
}

function parseHistory(output) {
  return String(output || '')
    .split('\x1e')
    .map((record) => record.trim())
    .filter(Boolean)
    .map((record) => {
      const [hash = '', shortHash = '', author = '', date = '', ...subjectParts] = record.split('\x1f')
      return {hash, shortHash, author, date, subject: subjectParts.join('\x1f')}
    })
}

async function history({cwd, branch = 'HEAD', limit = 30} = {}) {
  const count = Math.max(1, Math.min(Number(limit) || 30, 100))
  const result = await runGit(cwd, [
    'log',
    '-n', String(count),
    '--date=iso-strict',
    '--pretty=format:%H%x1f%h%x1f%an%x1f%aI%x1f%s%x1e',
    String(branch || 'HEAD'),
    '--'
  ])
  return parseHistory(result.stdout)
}

async function status(cwd) {
  const root = await tryGit(cwd, ['rev-parse', '--show-toplevel'])
  if (!root.ok) {
    return {
      initialized: false,
      rootPath: cwd,
      branch: '',
      dirty: false,
      changed: [],
      untracked: [],
      ahead: 0,
      behind: 0,
      upstream: '',
      message: '当前目录不是 Git 仓库'
    }
  }
  const rootPath = root.stdout.trim()
  const raw = await runGit(rootPath, ['status', '--porcelain=v1', '-z'])
  const entries = parseStatus(raw.stdout)
  const groups = splitStatus(entries)
  const tracking = await trackingOf(rootPath)
  return {
    initialized: true,
    rootPath,
    branch: await branchOf(rootPath),
    dirty: entries.length > 0,
    changed: groups.changed,
    untracked: groups.untracked,
    ahead: tracking.ahead,
    behind: tracking.behind,
    upstream: tracking.upstream,
    message: entries.length > 0 ? `${entries.length} 个文件有变更` : '工作区干净'
  }
}

async function commit(cwd, message, files = []) {
  const text = String(message || '').trim()
  if (!text) throw new Error('提交信息不能为空')
  const args = ['add']
  if (!Array.isArray(files) || files.length === 0) {
    args.push('-A')
  } else {
    args.push('--', ...files.map((file) => String(file)))
  }
  await runGit(cwd, args)
  const name = await tryGit(cwd, ['config', 'user.name'])
  const email = await tryGit(cwd, ['config', 'user.email'])
  const commitArgs = []
  if (!name.ok || !name.stdout.trim()) commitArgs.push('-c', 'user.name=Loopra')
  if (!email.ok || !email.stdout.trim()) commitArgs.push('-c', 'user.email=loopra@sorghum.site')
  commitArgs.push('commit', '-m', text)
  const result = await runGit(cwd, commitArgs)
  return {message: result.stdout.trim() || result.stderr.trim(), status: await status(cwd)}
}

async function push(cwd, remote = '', branch = '') {
  const args = ['push']
  if (remote) {
    args.push(String(remote))
    if (branch) args.push(String(branch))
  }
  const result = await runGit(cwd, args)
  return {message: result.stdout.trim() || result.stderr.trim(), status: await status(cwd)}
}

async function conflictFiles(cwd) {
  const result = await tryGit(cwd, ['diff', '--name-only', '--diff-filter=U'])
  return result.ok ? result.stdout.split(/\r?\n/).map((item) => item.trim()).filter(Boolean) : []
}

async function mergeCurrentIntoMain({currentPath, currentBranch, mainPath, mainBranch}) {
  if (!currentPath || !currentBranch || !mainPath || !mainBranch) {
    throw new Error('工作树与主工作区信息不完整')
  }
  const current = await status(currentPath)
  if (!current.initialized) throw new Error('当前环境不是 Git 仓库')
  if (current.dirty) return {merged: false, needsCommit: true, conflicted: false, conflictFiles: [], message: '请先提交当前环境的改动'}
  const main = await status(mainPath)
  if (!main.initialized) throw new Error('主工作区不是 Git 仓库')
  if (main.dirty) return {merged: false, needsCommit: false, conflicted: false, conflictFiles: [], message: '主工作区有未提交改动，请先处理'}

  const reverse = await tryGit(currentPath, ['merge', '--no-edit', mainBranch])
  if (!reverse.ok) {
    const conflicts = await conflictFiles(currentPath)
    return conflicts.length > 0
      ? {merged: false, needsCommit: false, conflicted: true, conflictFiles: conflicts, message: '工作树与主分支存在冲突，请先解决冲突'}
      : {merged: false, needsCommit: false, conflicted: false, conflictFiles: [], message: reverse.stderr || '同步主分支失败'}
  }
  const fastForward = await tryGit(mainPath, ['merge', '--ff-only', currentBranch])
  if (!fastForward.ok) {
    return {merged: false, needsCommit: false, conflicted: false, conflictFiles: [], message: fastForward.stderr || '主工作区快进合并失败'}
  }
  return {merged: true, needsCommit: false, conflicted: false, conflictFiles: [], message: '已合并到主工作区', status: await status(mainPath)}
}

function registerGitEnvironmentIpc(ipcMain) {
  ipcMain.handle('git-environment-status', async (_event, cwd) => status(cwd))
  ipcMain.handle('git-environment-history', async (_event, payload = {}) => history(payload))
  ipcMain.handle('git-environment-commit', async (_event, payload = {}) => commit(payload.cwd, payload.message, payload.files))
  ipcMain.handle('git-environment-push', async (_event, payload = {}) => push(payload.cwd, payload.remote, payload.branch))
  ipcMain.handle('git-environment-merge', async (_event, payload = {}) => mergeCurrentIntoMain(payload))
}

module.exports = {registerGitEnvironmentIpc}
