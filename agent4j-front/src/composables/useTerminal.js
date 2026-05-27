import { ref, onMounted, onUnmounted } from 'vue'

export function useTerminal() {
  const outputLines = ref([])
  const isProcessing = ref(false)
  
  const addOutput = (text, type = 'info') => {
    const timestamp = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    outputLines.value.push({
      id: Date.now(),
      text,
      type,
      timestamp
    })
    
    // 限制输出行数
    if (outputLines.value.length > 1000) {
      outputLines.value = outputLines.value.slice(-500)
    }
  }
  
  const clearOutput = () => {
    outputLines.value = []
  }
  
  const processCommand = async (command) => {
    isProcessing.value = true
    
    try {
      // 这里可以添加实际的命令处理逻辑
      addOutput(`> ${command}`, 'command')
      
      // 模拟处理延迟
      await new Promise(resolve => setTimeout(resolve, 100))
      
      // 根据命令类型处理
      if (command.startsWith('/')) {
        handleSlashCommand(command)
      } else if (command.startsWith('help')) {
        addOutput('可用命令: /chat, /tools, /sessions, /settings, /help, clear, exit', 'info')
      } else if (command === 'clear') {
        clearOutput()
      } else if (command === 'exit') {
        addOutput('正在退出...', 'warning')
        setTimeout(() => window.close(), 1000)
      } else {
        addOutput(`未知命令: ${command}. 输入 'help' 查看可用命令。`, 'error')
      }
    } catch (error) {
      addOutput(`错误: ${error.message}`, 'error')
    } finally {
      isProcessing.value = false
    }
  }
  
  const handleSlashCommand = (command) => {
    const parts = command.split(' ')
    const cmd = parts[0]
    const args = parts.slice(1)
    
    switch (cmd) {
      case '/chat':
        addOutput('正在打开对话界面...', 'info')
        // 实际应用中会路由到 /chat
        break
      case '/tools':
        addOutput('正在加载工具列表...', 'info')
        break
      case '/sessions':
        addOutput('正在加载会话列表...', 'info')
        break
      case '/settings':
        addOutput('正在打开设置界面...', 'info')
        break
      case '/help':
        addOutput('帮助信息已显示', 'info')
        break
      case '/new':
        addOutput('已创建新会话', 'info')
        break
      case '/plan':
        addOutput('已启用计划模式 - 只读工具可用', 'info')
        break
      case '/execute':
        addOutput('已禁用计划模式', 'info')
        break
      case '/compact':
        addOutput('正在压缩历史消息...', 'info')
        break
      case '/retry':
        addOutput('正在重试最后一条消息...', 'info')
        break
      case '/rewind':
        if (args.length > 0) {
          addOutput(`正在回退到第 ${args[0]} 轮对话...`, 'info')
        } else {
          addOutput('请指定回退的轮数: /rewind N', 'error')
        }
        break
      default:
        addOutput(`未知命令: ${cmd}`, 'error')
    }
  }
  
  // 监听自定义事件
  const handleTerminalOutput = (event) => {
    const { type, text } = event.detail
    addOutput(text, type)
  }
  
  const handleTerminalClear = () => {
    clearOutput()
  }
  
  onMounted(() => {
    window.addEventListener('terminal-output', handleTerminalOutput)
    window.addEventListener('terminal-clear', handleTerminalClear)
  })
  
  onUnmounted(() => {
    window.removeEventListener('terminal-output', handleTerminalOutput)
    window.removeEventListener('terminal-clear', handleTerminalClear)
  })
  
  return {
    outputLines,
    isProcessing,
    addOutput,
    clearOutput,
    processCommand
  }
}