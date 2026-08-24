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
// 进度可见性：npm 默认隐藏生命周期脚本输出（安全特性）。本脚本会把进度和
// 安装器输出直接写到控制台设备（Windows CONOUT$ / POSIX /dev/tty），
// 绕开 npm 的捕获，因此默认 npm install 也能看到实时进度和测速结果。
//   - 完整日志：~/.loopra/install.log（永远记录）
//   - 非交互环境（CI/管道，无控制台）同样自动测速，不弹询问
//   - 安装失败时，npm 也会自动打印捕获到的脚本输出
//
// 镜像选择（GitHub 代理镜像，非 npm registry）：
//   - 已设置 LOOPRA_MIRROR 时直接使用，不测速；
//   - 未设置时自动测速：并发 HEAD 探测各候选代理镜像多轮取中位，自动选用
//     延迟最低者（GitHub 直连延迟不准，不参与比拼）；全部探测失败则回退直连。
//   选中的镜像以 LOOPRA_MIRROR 传入安装器（加速 JRE 等 GitHub 下载）。
//
// 不需要自动安装时，用 npm 官方逃生门跳过脚本：
//   npm install --ignore-scripts
//
// 零依赖，仅使用 Node.js 内置模块与系统自带 tar。

const fs = require('fs');
const os = require('os');
const path = require('path');
const https = require('https');
const { spawn, spawnSync } = require('child_process');

// 常见 GitHub 代理镜像（均已实测可访问；格式为 URL 前缀，安装器会拼接
// “<前缀>/https://github.com/...”）。仅对代理测速；GitHub 直连的 HEAD
// 延迟不代表实际下载可用性（不准），不参与比拼，只作为全部失败时的回退。
const MIRROR_OPTIONS = [
  { label: 'gh-proxy.org', value: 'https://gh-proxy.org' },
  { label: 'ghfast.top', value: 'https://ghfast.top' },
  { label: 'gh-proxy.com', value: 'https://gh-proxy.com' },
  { label: 'ghproxy.net', value: 'https://ghproxy.net' },
];

// 测速参数：并发探测 <前缀>/https://github.com/ 的 HEAD 响应延迟
const PROBE_URL = 'https://github.com/';
const PROBE_ROUNDS = 3;
const PROBE_TIMEOUT_MS = 5000;

const LOG_FILE = path.join(os.homedir(), '.loopra', 'install.log');

// 打开控制台设备（绕过 npm 对 stdout 的捕获）；无控制台（CI）时返回 null。
// 注意：Windows 无控制台会话中打开 CONOUT$ 可能创建普通文件而不是设备，
// 必须 fstat 校验并清理，避免在 cwd 留下垃圾文件。
function openConsoleFd() {
  const dev = process.platform === 'win32' ? 'CONOUT$' : '/dev/tty';
  let fd = null;
  try {
    fd = fs.openSync(dev, 'w');
    const st = fs.fstatSync(fd);
    if (st.isFile()) {
      // 伪设备文件：不是真实控制台
      fs.closeSync(fd);
      fd = null;
      try {
        fs.unlinkSync(dev);
      } catch {}
      return null;
    }
    return fd;
  } catch {
    if (fd != null) {
      try {
        fs.closeSync(fd);
      } catch {}
    }
    return null;
  }
}

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

// 写入终端：stdout 是 TTY 时正常写 stdout；被 npm 捕获（非 TTY）时改写控制台设备。
// stdout 始终也写一份，便于管道/日志场景观测。
class TerminalBridge {
  constructor() {
    this.consoleFd = null;
    if (!process.stdout.isTTY) {
      this.consoleFd = openConsoleFd();
    }
  }

  write(text) {
    try {
      process.stdout.write(text);
    } catch {}
    if (this.consoleFd != null) {
      try {
        fs.writeSync(this.consoleFd, text);
      } catch {}
    }
  }
}

// 单次 HEAD 探测；超时/失败记 Infinity（不参与最优先）
function probeOnce(prefix) {
  return new Promise((resolve) => {
    const url = prefix ? `${prefix}/${PROBE_URL}` : PROBE_URL;
    const started = Date.now();
    const req = https.request(url, { method: 'HEAD' }, (res) => {
      res.resume(); // 丢弃响应体
      resolve(Date.now() - started);
    });
    req.setTimeout(PROBE_TIMEOUT_MS, () => {
      req.destroy();
      resolve(Infinity);
    });
    req.on('error', () => resolve(Infinity));
    req.end();
  });
}

// 并发测速全部代理候选，每候选多轮并发取中位，返回延迟最低者；全失败返回 null
// （调用方回退 GitHub 直连）
async function detectFastestMirror(log) {
  const detected = await Promise.all(
    MIRROR_OPTIONS.map(async (m) => {
      const times = await Promise.all(
        Array.from({ length: PROBE_ROUNDS }, () => probeOnce(m.value))
      );
      const sorted = times.filter(Number.isFinite).sort((a, b) => a - b);
      const median =
        sorted.length > 0 ? sorted[Math.floor(sorted.length / 2)] : Infinity;
      return { label: m.label, value: m.value, median };
    })
  );
  log('[loopra-dist] 测速结果（中位延迟）:');
  for (const d of detected) {
    log(`  ${d.label}: ${Number.isFinite(d.median) ? `${d.median} ms` : 'FAIL'}`);
  }
  const best = detected
    .filter((d) => Number.isFinite(d.median))
    .sort((a, b) => a.median - b.median)[0];
  return best || null;
}

// 运行安装器：输出转发到终端（绕过 npm 捕获）+ 日志，返回退出码
function runInstaller(cmd, args, env, bridge, logStream) {
  return new Promise((resolve) => {
    const child = spawn(cmd, args, { stdio: ['inherit', 'pipe', 'pipe'], env });
    child.stdout.on('data', (d) => {
      bridge.write(d);
      logStream.write(d);
    });
    child.stderr.on('data', (d) => {
      bridge.write(d);
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
  const bridge = new TerminalBridge();

  // 双写：终端（桥接）+ 日志
  let logFd = null;
  try {
    fs.mkdirSync(path.dirname(LOG_FILE), { recursive: true });
    logFd = fs.createWriteStream(LOG_FILE, { flags: 'a' });
  } catch {}
  const log = (msg) => {
    bridge.write(msg + '\n');
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

  // [2/7] 镜像选择：已有 LOOPRA_MIRROR 直接用；否则自动测速选最快
  let mirror = (process.env.LOOPRA_MIRROR || '').trim().replace(/\/+$/, '');
  if (mirror) {
    log(`[loopra-dist] [2/7] 使用 LOOPRA_MIRROR 指定镜像: ${mirror}`);
  } else {
    log('[loopra-dist] [2/7] 自动测速选择最快镜像 ...');
    const best = await detectFastestMirror(log);
    if (best) {
      mirror = best.value;
      log(
        `[loopra-dist] [2/7] 自动选择: ${best.label} (${best.median} ms)${
          best.value ? '，将作为 LOOPRA_MIRROR 传给安装器' : ''
        }`
      );
    } else {
      log('[loopra-dist] [2/7] 所有镜像探测失败，回退 GitHub 直连');
    }
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
    const code = await runInstaller(cmd, args, env, bridge, logFd);

    // [6/7] 结果
    if (code !== 0) {
      log(`[loopra-dist] [6/7] 失败：安装器退出码 ${code}`);
      console.error(`[loopra-dist] 安装器失败（退出码 ${code}），完整日志: ${LOG_FILE}`);
      process.exit(code || 1);
    }

    // [7/7] 完成
    log('[loopra-dist] [6/7] 安装完成');
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