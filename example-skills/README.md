# Agent4j Skills V2 — Claude Code 风格

## 📋 Skill 格式

每个 skill 是一个 Markdown 文件，使用 YAML frontmatter 定义元数据：

```markdown
---
name: my-skill
description: 一句话描述（必须，会出现在 skill 索引中）
runAs: subagent  # inline（默认）| subagent
allowed-tools: read_file, glob, grep  # subagent 模式可用的工具
model: deepseek-v4-pro  # subagent 模式模型覆盖
---

# Skill 正文

这里是 Markdown 格式的指令...
```

## 📁 目录结构

Skill 文件可以放在以下位置（按优先级排序）：

### 项目级（优先级高）
```
<project>/.agent4j/skills/    ← 推荐
<project>/.agents/skills/     ← 兼容其他工具
<project>/.claude/skills/     ← 兼容 Claude Code
```

### 全局级
```
~/.agent4j/skills/            ← 推荐
~/.agents/skills/             ← 兼容其他工具
~/.claude/skills/             ← 兼容 Claude Code
```

### 文件格式
```
skills/
├── my-skill.md               ← 单文件格式
├── explore/                  ← 目录格式
│   └── SKILL.md
└── review/
    └── SKILL.md
```

## 🎯 Skill 类型

### 1. Inline Skill（默认）
正文中内容直接插入 LLM 上下文，LLM 阅读后执行。

```markdown
---
name: java-conventions
description: Java coding conventions for this project
runAs: inline
---

# Java Coding Conventions

当编写 Java 代码时，遵循以下规范...
```

**适用场景**：
- 编码规范
- 项目约定
- 常用模板

### 2. Subagent Skill
在隔离的子代理中运行，只返回最终结果。节省主上下文 token。

```markdown
---
name: explore
description: Run a focused read-only codebase investigation
runAs: subagent
allowed-tools: read_file, glob, grep, tree
---

You are an exploration subagent. Investigate the codebase...
```

**适用场景**：
- 代码探索
- 深度研究
- 代码审查

## 🛠️ 工具

### run_skill
调用已安装的 skill。

```json
{
  "name": "run_skill",
  "arguments": {
    "name": "explore",
    "arguments": "Find all places where authentication is handled"
  }
}
```

### install_skill
创建新的 skill。

```json
{
  "name": "install_skill",
  "arguments": {
    "name": "my-skill",
    "description": "My custom skill",
    "body": "# Instructions\n\nWhen this skill is invoked...",
    "scope": "project",
    "runAs": "inline"
  }
}
```

## 🚀 使用方法

### 1. 安装示例 skill
```bash
# 复制到项目目录
cp -r example-skills-v2/* <project>/.agent4j/skills/

# 或复制到全局目录
cp -r example-skills-v2/* ~/.agent4j/skills/
```

### 2. 创建自定义 skill
```bash
# 方法1：手动创建文件
mkdir -p ~/.agent4j/skills/my-skill
cat > ~/.agent4j/skills/my-skill/SKILL.md << 'EOF'
---
name: my-skill
description: My custom skill
---

# My Skill

Instructions here...
EOF

# 方法2：使用 LLM 创建
# 在对话中说："创建一个 skill 来帮我做 X"
```

### 3. 使用 skill
```bash
# 在对话中，LLM 会自动选择合适的 skill
> 帮我探索一下这个项目的认证机制

# LLM 会自动调用 run_skill({ name: "explore", arguments: "..." })
```

## 📊 Skill 索引

系统启动时，会在系统提示中注入 skill 索引：

```
# Skills — playbooks you can invoke

- explore [🧬 subagent] — Run a focused read-only codebase investigation
- java-conventions — Java coding conventions for this project
- review [🧬 subagent] — Review code changes
```

LLM 根据这个索引决定调用哪个 skill。

## 🔧 高级功能

### 1. Skill 依赖
在 skill 正文中引用其他 skill：

```markdown
---
name: full-review
description: Full code review with exploration
runAs: subagent
---

First, use `explore` skill to understand the codebase structure.
Then review the changes...
```

### 2. 工具限制
subagent 模式可以限制可用工具：

```markdown
---
name: safe-explore
description: Safe exploration without file modification
runAs: subagent
allowed-tools: read_file, glob, grep, tree
---
```

### 3. 模型覆盖
subagent 模式可以使用不同模型：

```markdown
---
name: deep-analysis
description: Deep analysis with pro model
runAs: subagent
model: deepseek-v4-pro
---
```

## 🎁 内置 Skill

### explore
代码探索 subagent，用于深度调查代码库。

### research
研究 subagent，结合网络搜索和代码阅读。

### review
代码审查 subagent，审查当前分支的变更。

### security-review
安全审查 subagent，专注于安全问题。

## 📝 最佳实践

1. **描述要精确** — LLM 根据描述选择 skill
2. **正文要清晰** — 写给 LLM 看的指令
3. **选择合适的模式** — 简单指令用 inline，复杂任务用 subagent
4. **限制工具** — subagent 只给必要的工具
5. **定期维护** — 删除不再需要的 skill

## 🔗 参考

- [Claude Code Skills](https://docs.anthropic.com/claude-code/skills)
- [DeepSeek-Reasonix Skills](https://github.com/...)