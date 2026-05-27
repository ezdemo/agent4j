/**
 * Agent4J 前端工具函数库
 * 提供常用的工具函数和辅助方法
 */

/**
 * 格式化文件大小
 * @param {number} bytes - 字节数
 * @param {number} decimals - 小数位数
 * @returns {string} 格式化后的文件大小
 */
export const formatFileSize = (bytes, decimals = 2) => {
  if (bytes === 0) return '0 Bytes'
  
  const k = 1024
  const dm = decimals < 0 ? 0 : decimals
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i]
}

/**
 * 格式化数字
 * @param {number} num - 数字
 * @param {number} digits - 位数
 * @returns {string} 格式化后的数字
 */
export const formatNumber = (num, digits = 1) => {
  if (!num) return '0'
  
  const si = [
    { value: 1E18, symbol: 'E' },
    { value: 1E15, symbol: 'P' },
    { value: 1E12, symbol: 'T' },
    { value: 1E9, symbol: 'G' },
    { value: 1E6, symbol: 'M' },
    { value: 1E3, symbol: 'K' }
  ]
  
  for (let i = 0; i < si.length; i++) {
    if (num >= si[i].value) {
      return (num / si[i].value).toFixed(digits).replace(/\.0+$|(\.[0-9]*[1-9])0+$/, '$1') + si[i].symbol
    }
  }
  
  return num.toString()
}

/**
 * 格式化Token数量
 * @param {number} tokens - Token数量
 * @returns {string} 格式化后的Token数量
 */
export const formatTokens = (tokens) => {
  if (!tokens) return '0'
  if (tokens >= 1000000) return (tokens / 1000000).toFixed(1) + 'M'
  if (tokens >= 1000) return (tokens / 1000).toFixed(1) + 'K'
  return tokens.toString()
}

/**
 * 格式化日期时间
 * @param {Date|string|number} date - 日期对象、字符串或时间戳
 * @param {string} format - 格式化模式
 * @returns {string} 格式化后的日期时间字符串
 */
export const formatDateTime = (date, format = 'YYYY-MM-DD HH:mm:ss') => {
  const d = new Date(date)
  
  if (isNaN(d.getTime())) {
    return '无效日期'
  }
  
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  
  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 相对时间格式化
 * @param {Date|string|number} date - 日期对象、字符串或时间戳
 * @returns {string} 相对时间字符串
 */
export const timeAgo = (date) => {
  if (!date) return ''
  
  const now = Date.now()
  const d = new Date(date)
  
  if (isNaN(d.getTime())) {
    return ''
  }
  
  const diff = now - d.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  const weeks = Math.floor(days / 7)
  const months = Math.floor(days / 30)
  const years = Math.floor(days / 365)
  
  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  if (weeks < 4) return `${weeks}周前`
  if (months < 12) return `${months}个月前`
  return `${years}年前`
}

/**
 * 防抖函数
 * @param {Function} func - 要防抖的函数
 * @param {number} wait - 等待时间（毫秒）
 * @param {boolean} immediate - 是否立即执行
 * @returns {Function} 防抖后的函数
 */
export const debounce = (func, wait = 300, immediate = false) => {
  let timeout
  
  return function executedFunction(...args) {
    const context = this
    
    const later = () => {
      timeout = null
      if (!immediate) func.apply(context, args)
    }
    
    const callNow = immediate && !timeout
    
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
    
    if (callNow) func.apply(context, args)
  }
}

/**
 * 节流函数
 * @param {Function} func - 要节流的函数
 * @param {number} limit - 限制时间（毫秒）
 * @returns {Function} 节流后的函数
 */
export const throttle = (func, limit = 300) => {
  let inThrottle
  
  return function executedFunction(...args) {
    const context = this
    
    if (!inThrottle) {
      func.apply(context, args)
      inThrottle = true
      setTimeout(() => inThrottle = false, limit)
    }
  }
}

/**
 * 深拷贝对象
 * @param {any} obj - 要拷贝的对象
 * @returns {any} 拷贝后的对象
 */
export const deepClone = (obj) => {
  if (obj === null || typeof obj !== 'object') {
    return obj
  }
  
  if (obj instanceof Date) {
    return new Date(obj.getTime())
  }
  
  if (obj instanceof Array) {
    return obj.reduce((arr, item, i) => {
      arr[i] = deepClone(item)
      return arr
    }, [])
  }
  
  if (obj instanceof Object) {
    return Object.keys(obj).reduce((newObj, key) => {
      newObj[key] = deepClone(obj[key])
      return newObj
    }, {})
  }
  
  return obj
}

/**
 * 生成唯一ID
 * @param {string} prefix - 前缀
 * @returns {string} 唯一ID
 */
export const generateId = (prefix = '') => {
  const timestamp = Date.now().toString(36)
  const random = Math.random().toString(36).substr(2, 9)
  return prefix ? `${prefix}_${timestamp}_${random}` : `${timestamp}_${random}`
}

/**
 * 生成UUID
 * @returns {string} UUID
 */
export const generateUUID = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

/**
 * 截断文本
 * @param {string} text - 原始文本
 * @param {number} maxLength - 最大长度
 * @param {string} suffix - 后缀
 * @returns {string} 截断后的文本
 */
export const truncateText = (text, maxLength = 100, suffix = '...') => {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength - suffix.length) + suffix
}

/**
 * 转义HTML特殊字符
 * @param {string} text - 原始文本
 * @returns {string} 转义后的文本
 */
export const escapeHtml = (text) => {
  if (!text) return ''
  
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  }
  
  return text.replace(/[&<>"']/g, (m) => map[m])
}

/**
 * 反转义HTML特殊字符
 * @param {string} html - HTML文本
 * @returns {string} 反转义后的文本
 */
export const unescapeHtml = (html) => {
  if (!html) return ''
  
  const map = {
    '&amp;': '&',
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&#039;': "'"
  }
  
  return html.replace(/&amp;|&lt;|&gt;|&quot;|&#039;/g, (m) => map[m])
}

/**
 * 简单的Markdown转HTML
 * @param {string} markdown - Markdown文本
 * @returns {string} HTML文本
 */
export const markdownToHtml = (markdown) => {
  if (!markdown) return ''
  
  let html = markdown
    // 代码块
    .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>')
    // 行内代码
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // 粗体
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    // 斜体
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    // 链接
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
    // 标题
    .replace(/^### (.*$)/gm, '<h4>$1</h4>')
    .replace(/^## (.*$)/gm, '<h3>$1</h3>')
    .replace(/^# (.*$)/gm, '<h2>$1</h2>')
    // 无序列表
    .replace(/^\s*[-*]\s+(.*$)/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
    // 有序列表
    .replace(/^\s*\d+\.\s+(.*$)/gm, '<li>$1</li>')
    // 换行
    .replace(/\n/g, '<br>')
  
  return html
}

/**
 * 复制文本到剪贴板
 * @param {string} text - 要复制的文本
 * @returns {Promise<boolean>} 是否成功
 */
export const copyToClipboard = async (text) => {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    } else {
      // 降级方案
      const textArea = document.createElement('textarea')
      textArea.value = text
      textArea.style.position = 'fixed'
      textArea.style.left = '-999999px'
      textArea.style.top = '-999999px'
      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()
      
      try {
        document.execCommand('copy')
        return true
      } catch (err) {
        console.error('Failed to copy:', err)
        return false
      } finally {
        document.body.removeChild(textArea)
      }
    }
  } catch (error) {
    console.error('Failed to copy:', error)
    return false
  }
}

/**
 * 下载文件
 * @param {string} content - 文件内容
 * @param {string} filename - 文件名
 * @param {string} mimeType - MIME类型
 */
export const downloadFile = (content, filename, mimeType = 'text/plain') => {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/**
 * 下载JSON文件
 * @param {Object} data - JSON数据
 * @param {string} filename - 文件名
 */
export const downloadJson = (data, filename) => {
  const content = JSON.stringify(data, null, 2)
  downloadFile(content, filename, 'application/json')
}

/**
 * 读取文件内容
 * @param {File} file - 文件对象
 * @returns {Promise<string>} 文件内容
 */
export const readFileContent = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.onerror = (e) => reject(e)
    reader.readAsText(file)
  })
}

/**
 * 检查是否为移动设备
 * @returns {boolean} 是否为移动设备
 */
export const isMobile = () => {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
}

/**
 * 检查是否为触摸设备
 * @returns {boolean} 是否为触摸设备
 */
export const isTouchDevice = () => {
  return 'ontouchstart' in window || navigator.maxTouchPoints > 0
}

/**
 * 获取URL参数
 * @param {string} name - 参数名
 * @returns {string|null} 参数值
 */
export const getUrlParam = (name) => {
  const urlParams = new URLSearchParams(window.location.search)
  return urlParams.get(name)
}

/**
 * 设置URL参数
 * @param {string} key - 参数名
 * @param {string} value - 参数值
 */
export const setUrlParam = (key, value) => {
  const url = new URL(window.location)
  url.searchParams.set(key, value)
  window.history.replaceState({}, '', url)
}

/**
 * 删除URL参数
 * @param {string} key - 参数名
 */
export const removeUrlParam = (key) => {
  const url = new URL(window.location)
  url.searchParams.delete(key)
  window.history.replaceState({}, '', url)
}

/**
 * 本地存储操作
 */
export const storage = {
  get: (key, defaultValue = null) => {
    try {
      const item = localStorage.getItem(key)
      return item ? JSON.parse(item) : defaultValue
    } catch (error) {
      console.error('Failed to get from storage:', error)
      return defaultValue
    }
  },
  
  set: (key, value) => {
    try {
      localStorage.setItem(key, JSON.stringify(value))
      return true
    } catch (error) {
      console.error('Failed to set to storage:', error)
      return false
    }
  },
  
  remove: (key) => {
    try {
      localStorage.removeItem(key)
      return true
    } catch (error) {
      console.error('Failed to remove from storage:', error)
      return false
    }
  },
  
  clear: () => {
    try {
      localStorage.clear()
      return true
    } catch (error) {
      console.error('Failed to clear storage:', error)
      return false
    }
  },
  
  has: (key) => {
    return localStorage.getItem(key) !== null
  }
}

/**
 * 会话存储操作
 */
export const sessionStorage = {
  get: (key, defaultValue = null) => {
    try {
      const item = window.sessionStorage.getItem(key)
      return item ? JSON.parse(item) : defaultValue
    } catch (error) {
      console.error('Failed to get from session storage:', error)
      return defaultValue
    }
  },
  
  set: (key, value) => {
    try {
      window.sessionStorage.setItem(key, JSON.stringify(value))
      return true
    } catch (error) {
      console.error('Failed to set to session storage:', error)
      return false
    }
  },
  
  remove: (key) => {
    try {
      window.sessionStorage.removeItem(key)
      return true
    } catch (error) {
      console.error('Failed to remove from session storage:', error)
      return false
    }
  },
  
  clear: () => {
    try {
      window.sessionStorage.clear()
      return true
    } catch (error) {
      console.error('Failed to clear session storage:', error)
      return false
    }
  }
}

/**
 * 延迟执行
 * @param {number} ms - 延迟时间（毫秒）
 * @returns {Promise} Promise对象
 */
export const sleep = (ms) => {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * 重试函数
 * @param {Function} fn - 要重试的函数
 * @param {number} maxRetries - 最大重试次数
 * @param {number} delay - 重试间隔（毫秒）
 * @returns {Promise} Promise对象
 */
export const retry = async (fn, maxRetries = 3, delay = 1000) => {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await fn()
    } catch (error) {
      if (i === maxRetries - 1) throw error
      
      console.warn(`Retry ${i + 1}/${maxRetries} after ${delay}ms`)
      await sleep(delay * (i + 1))
    }
  }
}

/**
 * 格式化JSON
 * @param {Object|string} data - JSON数据或字符串
 * @param {number} indent - 缩进空格数
 * @returns {string} 格式化后的JSON字符串
 */
export const formatJson = (data, indent = 2) => {
  try {
    const obj = typeof data === 'string' ? JSON.parse(data) : data
    return JSON.stringify(obj, null, indent)
  } catch (error) {
    console.error('Failed to format JSON:', error)
    return typeof data === 'string' ? data : JSON.stringify(data)
  }
}

/**
 * 验证邮箱格式
 * @param {string} email - 邮箱地址
 * @returns {boolean} 是否有效
 */
export const isValidEmail = (email) => {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return re.test(email)
}

/**
 * 验证URL格式
 * @param {string} url - URL地址
 * @returns {boolean} 是否有效
 */
export const isValidUrl = (url) => {
  try {
    new URL(url)
    return true
  } catch (error) {
    return false
  }
}

/**
 * 获取文件扩展名
 * @param {string} filename - 文件名
 * @returns {string} 文件扩展名
 */
export const getFileExtension = (filename) => {
  if (!filename) return ''
  const parts = filename.split('.')
  return parts.length > 1 ? parts.pop().toLowerCase() : ''
}

/**
 * 获取文件名（不含扩展名）
 * @param {string} filename - 文件名
 * @returns {string} 文件名
 */
export const getFileNameWithoutExtension = (filename) => {
  if (!filename) return ''
  const parts = filename.split('.')
  return parts.length > 1 ? parts.slice(0, -1).join('.') : filename
}

/**
 * 检查文件是否为图片
 * @param {string} filename - 文件名
 * @returns {boolean} 是否为图片
 */
export const isImageFile = (filename) => {
  const extensions = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  const ext = getFileExtension(filename)
  return extensions.includes(ext)
}

/**
 * 检查文件是否为代码文件
 * @param {string} filename - 文件名
 * @returns {boolean} 是否为代码文件
 */
export const isCodeFile = (filename) => {
  const extensions = [
    'js', 'jsx', 'ts', 'tsx', 'vue', 'svelte',
    'html', 'css', 'scss', 'less', 'sass',
    'java', 'py', 'rb', 'php', 'go', 'rs', 'swift', 'kt',
    'c', 'cpp', 'h', 'hpp', 'cs',
    'json', 'xml', 'yaml', 'yml', 'toml',
    'md', 'txt', 'log'
  ]
  const ext = getFileExtension(filename)
  return extensions.includes(ext)
}

/**
 * 获取语言对应的图标
 * @param {string} language - 编程语言
 * @returns {string} 图标类名
 */
export const getLanguageIcon = (language) => {
  const icons = {
    javascript: 'js',
    typescript: 'ts',
    python: 'py',
    java: 'java',
    go: 'go',
    rust: 'rs',
    ruby: 'rb',
    php: 'php',
    swift: 'swift',
    kotlin: 'kt',
    csharp: 'cs',
    html: 'html',
    css: 'css',
    json: 'json',
    markdown: 'md'
  }
  return icons[language?.toLowerCase()] || 'file'
}

/**
 * 防抖搜索
 * @param {Function} searchFn - 搜索函数
 * @param {number} delay - 延迟时间
 * @returns {Function} 防抖后的搜索函数
 */
export const debouncedSearch = (searchFn, delay = 300) => {
  return debounce(async (query) => {
    if (!query || query.trim().length < 2) {
      return []
    }
    return await searchFn(query)
  }, delay)
}

/**
 * 高亮搜索关键词
 * @param {string} text - 原始文本
 * @param {string} keyword - 关键词
 * @returns {string} 高亮后的HTML
 */
export const highlightKeyword = (text, keyword) => {
  if (!text || !keyword) return text
  
  const escapedKeyword = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${escapedKeyword})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

/**
 * 计算两个日期之间的差值
 * @param {Date} date1 - 日期1
 * @param {Date} date2 - 日期2
 * @returns {Object} 差值对象
 */
export const dateDiff = (date1, date2) => {
  const d1 = new Date(date1)
  const d2 = new Date(date2)
  const diffMs = Math.abs(d2 - d1)
  
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)
  
  return {
    milliseconds: diffMs,
    seconds: diffSeconds,
    minutes: diffMinutes,
    hours: diffHours,
    days: diffDays
  }
}

/**
 * 格式化持续时间
 * @param {number} seconds - 秒数
 * @returns {string} 格式化后的持续时间
 */
export const formatDuration = (seconds) => {
  if (!seconds || seconds < 0) return '0秒'
  
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)
  
  const parts = []
  if (hours > 0) parts.push(`${hours}小时`)
  if (minutes > 0) parts.push(`${minutes}分钟`)
  if (secs > 0 || parts.length === 0) parts.push(`${secs}秒`)
  
  return parts.join('')
}

/**
 * 生成随机颜色
 * @returns {string} 随机颜色值
 */
export const generateRandomColor = () => {
  return '#' + Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0')
}

/**
 * 生成随机ID颜色
 * @param {string} id - ID字符串
 * @returns {string} 颜色值
 */
export const getColorById = (id) => {
  if (!id) return '#6366f1'
  
  let hash = 0
  for (let i = 0; i < id.length; i++) {
    hash = id.charCodeAt(i) + ((hash << 5) - hash)
    hash = hash & hash
  }
  
  const hue = Math.abs(hash % 360)
  return `hsl(${hue}, 70%, 60%)`
}

/**
 * 检查对象是否为空
 * @param {Object} obj - 对象
 * @returns {boolean} 是否为空
 */
export const isEmptyObject = (obj) => {
  return obj && Object.keys(obj).length === 0 && obj.constructor === Object
}

/**
 * 深度合并对象
 * @param {Object} target - 目标对象
 * @param {Object} source - 源对象
 * @returns {Object} 合并后的对象
 */
export const deepMerge = (target, source) => {
  const result = { ...target }
  
  for (const key in source) {
    if (source.hasOwnProperty(key)) {
      if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
        result[key] = deepMerge(result[key] || {}, source[key])
      } else {
        result[key] = source[key]
      }
    }
  }
  
  return result
}

/**
 * 数组分块
 * @param {Array} array - 数组
 * @param {number} size - 块大小
 * @returns {Array} 分块后的数组
 */
export const chunkArray = (array, size) => {
  const chunks = []
  for (let i = 0; i < array.length; i += size) {
    chunks.push(array.slice(i, i + size))
  }
  return chunks
}

/**
 * 数组去重
 * @param {Array} array - 数组
 * @param {string} key - 去重键（对象数组）
 * @returns {Array} 去重后的数组
 */
export const uniqueArray = (array, key) => {
  if (!key) {
    return [...new Set(array)]
  }
  
  const seen = new Set()
  return array.filter(item => {
    const value = item[key]
    if (seen.has(value)) {
      return false
    }
    seen.add(value)
    return true
  })
}

/**
 * 数组排序
 * @param {Array} array - 数组
 * @param {string} key - 排序键
 * @param {string} order - 排序顺序（asc/desc）
 * @returns {Array} 排序后的数组
 */
export const sortArray = (array, key, order = 'asc') => {
  return [...array].sort((a, b) => {
    let valueA = a[key]
    let valueB = b[key]
    
    if (typeof valueA === 'string') {
      valueA = valueA.toLowerCase()
      valueB = valueB.toLowerCase()
    }
    
    if (order === 'asc') {
      return valueA > valueB ? 1 : valueA < valueB ? -1 : 0
    } else {
      return valueA < valueB ? 1 : valueA > valueB ? -1 : 0
    }
  })
}

/**
 * 过滤数组
 * @param {Array} array - 数组
 * @param {string} query - 搜索查询
 * @param {Array} keys - 搜索键
 * @returns {Array} 过滤后的数组
 */
export const filterArray = (array, query, keys) => {
  if (!query) return array
  
  const lowerQuery = query.toLowerCase()
  
  return array.filter(item => {
    return keys.some(key => {
      const value = item[key]
      if (typeof value === 'string') {
        return value.toLowerCase().includes(lowerQuery)
      }
      return false
    })
  })
}

export default {
  formatFileSize,
  formatNumber,
  formatTokens,
  formatDateTime,
  timeAgo,
  debounce,
  throttle,
  deepClone,
  generateId,
  generateUUID,
  truncateText,
  escapeHtml,
  unescapeHtml,
  markdownToHtml,
  copyToClipboard,
  downloadFile,
  downloadJson,
  readFileContent,
  isMobile,
  isTouchDevice,
  getUrlParam,
  setUrlParam,
  removeUrlParam,
  storage,
  sessionStorage,
  sleep,
  retry,
  formatJson,
  isValidEmail,
  isValidUrl,
  getFileExtension,
  getFileNameWithoutExtension,
  isImageFile,
  isCodeFile,
  getLanguageIcon,
  debouncedSearch,
  highlightKeyword,
  dateDiff,
  formatDuration,
  generateRandomColor,
  getColorById,
  isEmptyObject,
  deepMerge,
  chunkArray,
  uniqueArray,
  sortArray,
  filterArray
}