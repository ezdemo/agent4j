#!/usr/bin/env node
'use strict';

// Loopra 核心分发包自动安装器（postinstall）
//
// npm install loopra-dist 时自动执行（等效于官方一键安装脚本）：
//   1. 解压包内 loopra-dist.tar.gz
//   2. 按当前平台执行包内自带安装器（Windows -> install.ps1 -Setup，
//      macOS/Linux -> install.sh --setup）
//   3. 安装器复用系统 Java 17+ / 已有捆绑 JRE，都没有时自动下载 JRE 25，
//      最终安装到 ~/.loopra 并配置 PATH
//
// 进度可见性：npm 默认隐藏生命周期脚本输出（安全特性）。
//   - 实时进度：npm install -g loopra-dist --foreground-scripts
//   - 完整日志：~/.loopra/install.log（无论哪种方式都会记录）
//   - 安装失败时，npm 会自动打印捕获到的脚本输出
//
// 镜像选择：
//   - 已设置 LOOPRA_MIRROR 时直接使用，不再询问；
//   - 交互式环境（直接运行脚本或 npm --foreground-scripts）弹出镜像菜单；
//   - npm 默认模式 / CI / 管道：跳过询问，默认 GitHub 直连（避免隐形挂起）。
//   选择的镜像以 LOOPRA_MIRROR 传入安装器（加速 JRE 等 GitHub 下载）。
//
// 不需要自动安装时，用 npm 官方逃生门跳过脚本：
//   npm install --ignore-scripts
//
// 零依赖，仅使用 Node.js 内置模块与系统自带 tar。

const fs = require('fs');
const os = require('os');
const path = require('path');
const readline = require('readline');
const { spawn, spawnSync } = require('child_process');

// 常见 GitHub 代理镜像（均已实测可访问；格式为 URL 前缀，安装器会拼接
// “<前缀>/https://github.com/...”）
const MIRROR_OPTIONS = [
  { label: 'GitHub 直连（默认）', value: '' },
  { label: 'gh-proxy.org', value: 'https://gh-proxy.org' },
  { label: 'ghfast.top', value: 'https://ghfast.top' },
  { label: 'gh-proxy.com', value: 'https://gh-proxy.com' },
  { label: 'ghproxy.net', value: 'https://ghproxy.net' },
];

const LOG_FILE = path.join(os.homedir(), '.loopra', 'install.log');

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

// 交互式选择镜像；stdin 关闭（EOF）或直接回车 -> 直连（''）
function askMirror() {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    let answered = false;
    const finish = (value) => {
      if (!answered) {
        answered = true;
        rl.close();
        resolve(value);
      }
    };

    const lines = ['', '[loopra-dist] 选择下载镜像（用于加速 JRE 等 GitHub 资源下载）：'];
    MIRROR_OPTIONS.forEach((m, i) => lines.push(`  ${i + 1}) ${m.label}`));
    lines.push('  6) 自定义镜像前缀（如 https://ghfast.top）');
    lines.push('  直接回车 = GitHub 直连');

    rl.question(lines.join('\n') + '\n请选择 [1-6]: ', (ans) => {
      const n = parseInt(ans.trim(), 10);
      if (n >= 1 && n <= MIRROR_OPTIONS.length) return finish(MIRROR_OPTIONS[n - 1].value);
      if (n === 6) {
        rl.question('输入镜像前缀（直接回车 = 直连）: ', (custom) => {
          const v = custom.trim().replace(/\/+$/, '');
          finish(v || '');
        });
        return;
      }
      finish('');
    });
    // stdin 关闭（管道/CI）或 Ctrl+C -> 直连
    rl.on('close', () => finish(''));
  });
}

// 运行安装器：输出转发到终端 + 日志，返回退出码
function runInstaller(cmd, args, env, logStream) {
  return new Promise((resolve) => {
    const child = spawn(cmd, args, { stdio: ['inherit', 'pipe', 'pipe'], env });
    child.stdout.on('data', (d) => {
      process.stdout.write(d);
      logStream.write(d);
    });
    child.stderr.on('data', (d) => {
      process.stderr.write(d);
      logStream.write(d);
    });
    child.on('close', (code, signal) => resolve(signal ? 1 : code));
    child.on('error', (err) => {
      logStream.write(`[loopra-dist] 无法启动安装器: ${err.message}\n`);
      resolve(1);
    });
  });
}

async function main() {
  // 逐行双写：终端 + 日志
  const logFd = (() => {
    try {
      fs.mkdirSync(path.dirname(LOG_FILE), { recursive: true });
      return fs.createWriteStream(LOG_FILE, { flags: 'a' });
    } catch {
      return null;
    }
  })();
  const log = (msg) => {
    console.log(msg);
    if (logFd) logFd.write(msg + '\n');
  };

  log(`[loopra-dist] ===== 开始安装 ${new Date().toISOString()} ====`);
  log(`[loopra-dist] 平台: ${process.platform} / ${process.arch}`);

  // [1/7] 校验分发包
  const pkgDir = path.join(__dirname, '..');
  const tarball = path.join(pkgDir, 'loopra-dist.tar.gz');
  log(`[loopra-dist] [1/7] 校验分发包: ${tarball}`);
  if (!fs.existsSync(tarball)) {
    log(`[loopra-dist] [1/7] 失败：未找到 ${tarball}`);
    console.error(`[loopra-dist] 未找到 ${tarball}`);
    process.exit(1);
  }

  // [2/7] 镜像选择
  let mirror = (process.env.LOOPRA_MIRROR || '').trim().replace(/\/+$/, '');
  const underNpm = !!process.env.npm_lifecycle_event;
  const foreground = process.env.npm_config_foreground_scripts === 'true';
  if (!mirror) {
    if (underNpm && !foreground) {
      log('[loopra-dist] [2/7] npm 默认模式：跳过交互镜像选择，使用 GitHub 直连');
      log('[loopra-dist] [2/7] 提示：需要镜像菜单或实时进度，请用 npm install --foreground-scripts');
    } else {
      log('[loopra-dist] [2/7] 选择下载镜像 ...');
      mirror = await askMirror();
    }
  }
  if (mirror) {
    log(`[loopra-dist] [2/7] 使用镜像: ${mirror}（以 LOOPRA_MIRROR 传给安装器）`);
  } else {
    log('[loopra-dist] [2/7] 使用 GitHub 直连');
  }

  const isWin = process.platform === 'win32';
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'loopra-dist-'));
  try {
    // [3/7] 解压
    log('[loopra-dist] [3/7] 解压 loopra-dist.tar.gz 到临时目录 ...');
    const tar = spawnSync('tar', ['-xzf', tarball, '-C', tmp], { stdio: 'inherit' });
    if (tar.error) {
      log(`[loopra-dist] [3/7] 失败：无法调用系统 tar: ${tar.error.message}`);
      console.error(`[loopra-dist] 无法调用系统 tar: ${tar.error.message}`);
      process.exit(1);
    }
    if (tar.status !== 0) {
      log('[loopra-dist] [3/7] 失败：解压返回非零退出码');
      console.error('[loopra-dist] 解压 loopra-dist.tar.gz 失败');
      process.exit(tar.status || 1);
    }

    // [4/7] 定位安装器
    const scriptName = isWin ? 'install.ps1' : 'install.sh';
    const script = findScript(tmp, scriptName);
    log(`[loopra-dist] [4/7] 定位平台安装器: ${scriptName}`);
    if (!script) {
      log(`[loopra-dist] [4/7] 失败：解压产物中未找到 ${scriptName}`);
      console.error(`[loopra-dist] 解压产物中未找到 ${scriptName}`);
      process.exit(1);
    }

    // [5/7] 执行安装器（Java 检测 / 可能的 JRE 下载 / 安装到 ~/.loopra / 配置 PATH）
    const cmd = isWin ? 'powershell.exe' : 'bash';
    const args = isWin
      ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script, '-Setup']
      : [script, '--setup'];
    const env = { ...process.env };
    if (mirror) env.LOOPRA_MIRROR = mirror;

    log(`[loopra-dist] [5/7] 执行安装器: ${cmd} ${args.join(' ')}`);
    log('[loopra-dist] [5/7] （安装器会检查 Java 17+，必要时自动下载 JRE 25，并写入 ~/.loopra 与 PATH）');
    log(`[loopra-dist] [5/7] 安装日志: ${LOG_FILE}`);
    const code = await runInstaller(cmd, args, env, logFd);

    // [6/7] 结果
    if (code !== 0) {
      log(`[loopra-dist] [6/7] 失败：安装器退出码 ${code}`);
      console.error(`[loopra-dist] 安装器失败（退出码 ${code}），完整日志: ${LOG_FILE}`);
      process.exit(code || 1);
    }

    // [7/7] 完成
    log('[loopra-dist] [6/7] 安装完成 ✓');
    log('[loopra-dist] [7/7] 现在可以运行: loopra web    （或 loopra web 0 随机端口）');
    log(`[loopra-dist] [7/7] 完整安装日志: ${LOG_FILE}`);
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
    if (logFd) logFd.end();
  }
}

main().catch((err) => {
  console.error(`[loopra-dist] 安装失败: ${err.message}`);
  process.exit(1);
});