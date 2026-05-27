# Agent4J Frontend

> Agent4J 的复古终端风格前端界面

## 项目简介

Agent4J Frontend 是 Agent4J Java AI Agent 框架的前端界面，采用复古终端风格设计，使用 Vue 3 构建。

## 特性

- 🖥️ **复古终端风格** - 绿色磷光屏幕效果，扫描线动画
- 💬 **对话界面** - 与 AI 助手进行多轮对话
- 🔧 **工具管理** - 查看和管理可用工具
- 📁 **会话管理** - 管理历史会话记录
- ⚙️ **系统设置** - 配置 AI 参数和系统选项
- 📱 **响应式设计** - 支持桌面和移动设备

## 技术栈

- **框架**: Vue 3 (Composition API)
- **构建工具**: Vite
- **状态管理**: Pinia
- **路由**: Vue Router
- **HTTP 客户端**: Axios
- **样式**: CSS3 (复古终端主题)

## 项目结构

```
agent4j-front/
├── public/              # 静态资源
├── src/
│   ├── assets/          # 资源文件
│   │   ├── styles/      # 全局样式
│   │   └── logo.svg     # 项目图标
│   ├── components/      # 组件目录
│   ├── composables/     # 组合式函数
│   │   └── useTerminal.js
│   ├── router/          # 路由配置
│   │   └── index.js
│   ├── services/        # API 服务
│   │   └── api.js
│   ├── stores/          # 状态管理
│   │   └── app.js
│   ├── utils/           # 工具函数
│   │   └── helpers.js
│   ├── views/           # 页面视图
│   │   ├── Home.vue     # 首页
│   │   ├── Chat.vue     # 对话页面
│   │   ├── Tools.vue    # 工具页面
│   │   ├── Sessions.vue # 会话页面
│   │   ├── Settings.vue # 设置页面
│   │   ├── Help.vue     # 帮助页面
│   │   └── NotFound.vue # 404页面
│   ├── App.vue          # 根组件
│   └── main.js          # 入口文件
├── index.html           # HTML 入口
├── package.json         # 项目配置
└── vite.config.js       # Vite 配置
```

## 安装与运行

### 安装依赖

```bash
cd agent4j-front
npm install
```

### 开发模式

```bash
npm run dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist/` 目录。

### 预览生产版本

```bash
npm run preview
```

## 配置

### API 代理配置

在 `vite.config.js` 中配置了 API 代理：

```javascript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

确保 Agent4J 后端服务运行在 `http://localhost:8080`。

### 环境变量

创建 `.env` 文件：

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_APP_TITLE=Agent4J
```

## 页面说明

### 首页 (`/`)
- 系统状态概览
- 快速命令入口
- 最近活动记录

### 对话页面 (`/chat`)
- 与 AI 助手实时对话
- 支持工具调用展示
- 思考过程折叠显示
- 文件附件支持

### 工具页面 (`/tools`)
- 工具列表展示
- 工具详情查看
- 搜索和筛选功能
- 使用示例显示

### 会话页面 (`/sessions`)
- 历史会话列表
- 会话统计信息
- 会话加载和导出

### 设置页面 (`/settings`)
- 基本设置（语言、主题、字体）
- AI 设置（API配置、模型选择）
- 工作区设置
- 安全设置
- 高级设置

### 帮助页面 (`/help`)
- 快速入门指南
- 命令参考
- 工具列表
- 配置说明
- 故障排除

## 命令说明

在终端输入框中可以使用以下命令：

| 命令 | 描述 |
|------|------|
| `/chat` | 开始与AI助手对话 |
| `/tools` | 查看可用工具列表 |
| `/sessions` | 管理会话历史 |
| `/settings` | 系统配置设置 |
| `/help` | 查看帮助信息 |
| `/new` | 开启新会话 |
| `/plan` | 进入计划模式 |
| `/execute` | 退出计划模式 |
| `/compact` | 折叠历史消息 |
| `/retry` | 撤回最后一条消息并重试 |
| `/rewind N` | 回退到第N轮对话 |
| `clear` | 清空终端输出 |
| `exit` | 退出系统 |

## 主题定制

项目支持多种复古终端主题：

- **retro-green** - 经典绿色磷光屏
- **retro-amber** - 琥珀色磷光屏
- **retro-blue** - 蓝色磷光屏
- **dark** - 深色主题
- **light** - 浅色主题

在设置页面可以切换主题。

## 快捷键

| 快捷键 | 描述 |
|--------|------|
| `Enter` | 发送消息 |
| `Shift+Enter` | 换行 |
| `Ctrl+L` | 清空屏幕 |
| `Ctrl+C` | 取消当前操作 |
| `Ctrl+R` | 搜索历史命令 |
| `Tab` | 自动补全 |

## 开发说明

### 添加新页面

1. 在 `src/views/` 目录下创建新的 `.vue` 文件
2. 在 `src/router/index.js` 中添加路由配置
3. 在 `src/App.vue` 的导航菜单中添加链接

### 添加新组件

1. 在 `src/components/` 目录下创建新的 `.vue` 文件
2. 在需要使用的页面中导入组件

### 状态管理

使用 Pinia 进行状态管理：

```javascript
// 创建新的 store
import { defineStore } from 'pinia'

export const useMyStore = defineStore('my', () => {
  // 状态
  const myState = ref(null)
  
  // 方法
  const updateState = (newValue) => {
    myState.value = newValue
  }
  
  return {
    myState,
    updateState
  }
})
```

## 浏览器支持

- Chrome 80+
- Firefox 75+
- Safari 13+
- Edge 80+

## 许可证

MIT License

## 相关项目

- [Agent4J](https://gitee.com/ezdemo/agent4j) - Java AI Agent 框架