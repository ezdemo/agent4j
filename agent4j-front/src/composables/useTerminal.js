import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'

/**
 * 终端组合式函数
 * 提供终端输出、命令处理、历史记录等功能
 */
export function useTerminal() {
  // 输出行
  const outputLines = ref([])
  
  // 处理状态
  const isProcessing = ref(false)
  
  // 命令历史
  const commandHistory = ref([])
  const historyIndex = ref(-1)
  
  // 终端配置
  const config = reactive({
    maxLines: 1000,
    autoScroll: true,
    showTimestamp: true,
    showLineNumbers: false,
    fontSize: 14,
    theme: 'dark'
  })
  
  // 计算属性
  const lineCount = computed(() => outputLines.value.length)
  const hasOutput = computed(() => outputLines.value.length > 0)
  const lastLine = computed(() => outputLines.value[outputLines.value.length - 1])
  
  // 添加输出行
  const addOutput = (text, type = 'info', options = {}) => {
    const line = {
      id: Date.now() + Math.random(),
      text,
      type,
      timestamp: new Date().toISOString(),
      lineNumber: outputLines.value.length + 1,
      ...options
    }
    
    outputLines.value.push(line)
    
    // 限制输出行数
    if (outputLines.value.length > config.maxLines) {
      const excess = outputLines.value.length - config.maxLines
      outputLines.value = outputLines.value.slice(excess)
    }
    
    // 自动滚动
    if (config.autoScroll) {
      scrollToBottom()
    }
    
    return line
  }
  
  // 添加多行输出
  const addOutputLines = (lines, type = 'info') => {
    lines.forEach(line => {
      if (typeof line === 'string') {
        addOutput(line, type)
      } else {
        addOutput(line.text, line.type || type, line.options)
      }
    })
  }
  
  // 添加分隔线
  const addSeparator = (char = '─', length = 50) => {
    addOutput(char.repeat(length), 'separator')
  }
  
  // 添加空行
  const addEmptyLine = () => {
    addOutput('', 'empty')
  }
  
  // 清空输出
  const clearOutput = () => {
    outputLines.value = []
  }
  
  // 滚动到底部
  const scrollToBottom = () => {
    requestAnimationFrame(() => {
      const container = document.querySelector('.terminal-output')
      if (container) {
        container.scrollTop = container.scrollHeight
      }
    })
  }
  
  // 处理命令
  const processCommand = async (command) => {
    if (!command || !command.trim()) return
    
    const trimmedCommand = command.trim()
    
    // 添加到历史记录
    commandHistory.value.unshift(trimmedCommand)
    if (commandHistory.value.length > 100) {
      commandHistory.value = commandHistory.value.slice(0, 100)
    }
    historyIndex.value = -1
    
    // 显示命令
    addOutput(`$ ${trimmedCommand}`, 'command')
    
    // 处理命令
    isProcessing.value = true
    
    try {
      if (trimmedCommand.startsWith('/')) {
        await handleSlashCommand(trimmedCommand)
      } else {
        await handleRegularCommand(trimmedCommand)
      }
    } catch (error) {
      addOutput(`错误: ${error.message}`, 'error')
    } finally {
      isProcessing.value = false
    }
  }
  
  // 处理斜杠命令
  const handleSlashCommand = async (command) => {
    const parts = command.split(' ')
    const cmd = parts[0].toLowerCase()
    const args = parts.slice(1)
    
    switch (cmd) {
      case '/help':
        showHelp()
        break
        
      case '/clear':
        clearOutput()
        addOutput('终端已清空', 'info')
        break
        
      case '/new':
        addOutput('已创建新会话', 'success')
        // 触发事件
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'new', args } 
        }))
        break
        
      case '/plan':
        addOutput('已启用计划模式 - 只读工具可用', 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'plan', args } 
        }))
        break
        
      case '/execute':
        addOutput('已禁用计划模式', 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'execute', args } 
        }))
        break
        
      case '/compact':
        addOutput('正在压缩历史消息...', 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'compact', args } 
        }))
        break
        
      case '/retry':
        addOutput('正在重试最后一条消息...', 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'retry', args } 
        }))
        break
        
      case '/rewind':
        if (args.length > 0) {
          const n = parseInt(args[0])
          if (isNaN(n) || n < 1) {
            addOutput('错误: 请指定有效的轮数', 'error')
          } else {
            addOutput(`正在回退到第 ${n} 轮对话...`, 'info')
            window.dispatchEvent(new CustomEvent('terminal-command', { 
              detail: { command: 'rewind', args: [n] } 
            }))
          }
        } else {
          addOutput('用法: /rewind <轮数>', 'error')
        }
        break
        
      case '/sessions':
        addOutput('正在加载会话列表...', 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'sessions', args } 
        }))
        break
        
      case '/load':
        if (args.length > 0) {
          addOutput(`正在加载会话: ${args[0]}`, 'info')
          window.dispatchEvent(new CustomEvent('terminal-command', { 
            detail: { command: 'load', args } 
          }))
        } else {
          addOutput('用法: /load <会话名称>', 'error')
        }
        break
        
      case '/init':
        addOutput('正在分析项目并生成文档...', 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'init', args } 
        }))
        break
        
      case '/hitl':
        addOutput('正在切换 HITL 模式...', 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'hitl', args } 
        }))
        break
        
      case '/agree':
        addOutput('已批准 HITL 待执行的工具调用', 'success')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'agree', args } 
        }))
        break
        
      case '/deny':
        addOutput('已拒绝 HITL 待执行的工具调用', 'warning')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'deny', args } 
        }))
        break
        
      case '/exit':
      case '/quit':
        addOutput('正在退出...', 'warning')
        setTimeout(() => {
          window.close()
        }, 1000)
        break
        
      default:
        addOutput(`未知命令: ${cmd}`, 'error')
        addOutput('输入 /help 查看可用命令', 'info')
    }
  }
  
  // 处理普通命令
  const handleRegularCommand = async (command) => {
    const cmd = command.toLowerCase()
    
    switch (cmd) {
      case 'help':
        showHelp()
        break
        
      case 'clear':
        clearOutput()
        addOutput('终端已清空', 'info')
        break
        
      case 'exit':
      case 'quit':
        addOutput('正在退出...', 'warning')
        setTimeout(() => {
          window.close()
        }, 1000)
        break
        
      case 'version':
        addOutput('Agent4j v1.0.0', 'info')
        break
        
      case 'status':
        addOutput('系统状态: 运行中', 'success')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'status' } 
        }))
        break
        
      default:
        // 尝试作为消息发送
        addOutput(`正在发送消息: ${command}`, 'info')
        window.dispatchEvent(new CustomEvent('terminal-command', { 
          detail: { command: 'message', args: [command] } 
        }))
    }
  }
  
  // 显示帮助信息
  const showHelp = () => {
    addEmptyLine()
    addOutput('Agent4j 命令帮助', 'title')
    addSeparator()
    addEmptyLine()
    
    addOutput('基本命令:', 'subtitle')
    addOutput('  help          - 显示此帮助信息', 'info')
    addOutput('  clear         - 清空终端输出', 'info')
    addOutput('  version       - 显示版本信息', 'info')
    addOutput('  status        - 显示系统状态', 'info')
    addOutput('  exit/quit     - 退出系统', 'info')
    addEmptyLine()
    
    addOutput('斜杠命令:', 'subtitle')
    addOutput('  /help         - 显示帮助信息', 'info')
    addOutput('  /new          - 开启新会话', 'info')
    addOutput('  /plan         - 进入计划模式', 'info')
    addOutput('  /execute      - 退出计划模式', 'info')
    addOutput('  /compact      - 折叠历史消息', 'info')
    addOutput('  /retry        - 撤回最后一条消息并重试', 'info')
    addOutput('  /rewind N     - 回退到第N轮对话', 'info')
    addOutput('  /sessions     - 列出历史会话', 'info')
    addOutput('  /load N       - 加载指定会话', 'info')
    addOutput('  /init         - 自动分析项目生成文档', 'info')
    addOutput('  /hitl         - 切换 HITL 模式', 'info')
    addOutput('  /agree        - 批准 HITL 待执行的工具调用', 'info')
    addOutput('  /deny         - 拒绝 HITL 待执行的工具调用', 'info')
    addOutput('  /exit         - 退出系统', 'info')
    addEmptyLine()
    
    addOutput('快捷键:', 'subtitle')
    addOutput('  Enter         - 发送消息', 'info')
    addOutput('  Shift+Enter   - 换行', 'info')
    addOutput('  Ctrl+K        - 聚焦搜索', 'info')
    addOutput('  Ctrl+N        - 新建对话', 'info')
    addOutput('  Ctrl+B        - 切换侧边栏', 'info')
    addOutput('  Escape        - 关闭弹窗', 'info')
    addEmptyLine()
  }
  
  // 获取上一条命令
  const getPreviousCommand = () => {
    if (commandHistory.value.length === 0) return ''
    
    if (historyIndex.value < commandHistory.value.length - 1) {
      historyIndex.value++
    }
    
    return commandHistory.value[historyIndex.value] || ''
  }
  
  // 获取下一条命令
  const getNextCommand = () => {
    if (historyIndex.value > 0) {
      historyIndex.value--
      return commandHistory.value[historyIndex.value]
    }
    
    historyIndex.value = -1
    return ''
  }
  
  // 重置历史索引
  const resetHistoryIndex = () => {
    historyIndex.value = -1
  }
  
  // 搜索输出
  const searchOutput = (query, options = {}) => {
    const { caseSensitive = false, regex = false } = options
    
    if (!query) return []
    
    const results = []
    const flags = caseSensitive ? 'g' : 'gi'
    
    let matcher
    try {
      matcher = regex ? new RegExp(query, flags) : new RegExp(escapeRegExp(query), flags)
    } catch (e) {
      return []
    }
    
    outputLines.value.forEach((line, index) => {
      if (matcher.test(line.text)) {
        results.push({
          line,
          index,
          matches: line.text.match(matcher)
        })
      }
    })
    
    return results
  }
  
  // 转义正则表达式特殊字符
  const escapeRegExp = (string) => {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  }
  
  // 导出输出
  const exportOutput = (format = 'text') => {
    const lines = outputLines.value.map(line => {
      const timestamp = config.showTimestamp ? `[${new Date(line.timestamp).toLocaleTimeString()}] ` : ''
      return `${timestamp}${line.text}`
    })
    
    if (format === 'json') {
      return JSON.stringify(outputLines.value, null, 2)
    }
    
    return lines.join('\n')
  }
  
  // 复制输出到剪贴板
  const copyOutput = async () => {
    const text = exportOutput('text')
    try {
      await navigator.clipboard.writeText(text)
      addOutput('已复制到剪贴板', 'success')
      return true
    } catch (error) {
      addOutput('复制失败', 'error')
      return false
    }
  }
  
  // 下载输出
  const downloadOutput = (filename = 'terminal-output.txt') => {
    const text = exportOutput('text')
    const blob = new Blob([text], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
    addOutput(`已下载到 ${filename}`, 'success')
  }
  
  // 监听事件
  const handleTerminalOutput = (event) => {
    const { type, text } = event.detail
    addOutput(text, type)
  }
  
  const handleTerminalClear = () => {
    clearOutput()
  }
  
  const handleTerminalCommand = (event) => {
    const { command, args } = event.detail
    processCommand(command)
  }
  
  // 生命周期
  onMounted(() => {
    window.addEventListener('terminal-output', handleTerminalOutput)
    window.addEventListener('terminal-clear', handleTerminalClear)
    window.addEventListener('terminal-command', handleTerminalCommand)
  })
  
  onUnmounted(() => {
    window.removeEventListener('terminal-output', handleTerminalOutput)
    window.removeEventListener('terminal-clear', handleTerminalClear)
    window.removeEventListener('terminal-command', handleTerminalCommand)
  })
  
  return {
    // 状态
    outputLines,
    isProcessing,
    commandHistory,
    historyIndex,
    config,
    
    // 计算属性
    lineCount,
    hasOutput,
    lastLine,
    
    // 方法
    addOutput,
    addOutputLines,
    addSeparator,
    addEmptyLine,
    clearOutput,
    scrollToBottom,
    processCommand,
    getPreviousCommand,
    getNextCommand,
    resetHistoryIndex,
    searchOutput,
    exportOutput,
    copyOutput,
    downloadOutput
  }
}

export default useTerminal