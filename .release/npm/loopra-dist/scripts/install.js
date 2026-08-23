#!/usr/bin/env node
'use strict';

// Loopra 核心分发包自动安装器（postinstall）
//
// npm install loopra-dist 时自动执行（等效于官方一键安装脚本）：
//   1. 解压包内 loopra-dist.tar.gz
//   2. 按当前平台执行包内自带安装器（Windows -> install.ps1 -Setup，
//      macOS/Linux -> install.sh --setup）
//   3. 安装器复用系统 Java 17+ / 已有捆绑 JRE，都没有时自动下载 JRE 25，
//      最终安装到 ~/.loopra 并配置 PATH（可设 LOOPRA_MIRROR 指定镜像加速）
//
// 不需要自动安装时，用 npm 官方逃生门跳过脚本：
//   npm install --ignore-scripts
//
// 零依赖，仅使用 Node.js 内置模块与系统自带 tar。

const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

function findScript(dir, name) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      const found = findScript(p, name);
      if (found) return found;
    } else if (ent.name === name) {
      return p;
    }
  }
  return null;
}

function main() {
  const pkgDir = path.join(__dirname, '..');
  const tarball = path.join(pkgDir, 'loopra-dist.tar.gz');
  if (!fs.existsSync(tarball)) {
    console.error(`[loopra-dist] 未找到 ${tarball}`);
    process.exit(1);
  }

  const isWin = process.platform === 'win32';
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'loopra-dist-'));
  try {
    console.log('[loopra-dist] 解压 loopra-dist.tar.gz ...');
    const tar = spawnSync('tar', ['-xzf', tarball, '-C', tmp], { stdio: 'inherit' });
    if (tar.error) {
      console.error(`[loopra-dist] 无法调用系统 tar: ${tar.error.message}`);
      process.exit(1);
    }
    if (tar.status !== 0) {
      console.error('[loopra-dist] 解压 loopra-dist.tar.gz 失败');
      process.exit(tar.status || 1);
    }

    const scriptName = isWin ? 'install.ps1' : 'install.sh';
    const script = findScript(tmp, scriptName);
    if (!script) {
      console.error(`[loopra-dist] 解压产物中未找到 ${scriptName}`);
      process.exit(1);
    }

    const cmd = isWin ? 'powershell.exe' : 'bash';
    const args = isWin
      ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script, '-Setup']
      : [script, '--setup'];

    console.log(`[loopra-dist] 执行平台安装器: ${cmd} ${args.join(' ')}`);
    const res = spawnSync(cmd, args, { stdio: 'inherit' });
    if (res.error) {
      console.error(`[loopra-dist] 无法启动安装器: ${res.error.message}`);
      process.exit(1);
    }
    // 安装器被信号终止时 status 为 null，按失败处理
    process.exit(res.status == null ? 1 : res.status);
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

main();