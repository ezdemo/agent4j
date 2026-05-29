# Agent4j Skill 系统 — Claude Code 风格

## 📋 概述

基于 DeepSeek-Reasonix 设计的 Skill 系统，支持：

- ✅ **Markdown + YAML frontmatter 格式**
- ✅ **多级目录**（项目级 > 自定义 > 全局级）
- ✅ **两种运行模式**（inline / subagent）
- ✅ **自动选择机制**（LLM 根据索引自动调用）

## 📁 文件结构

```
agent4j-bin/src/main/java/.../skill/
├── SkillV2.java           # Skill 接口
├── SkillV2Impl.java       # Skill 实现
└── SkillStoreV2.java      # Skill 存储管理

agent4j-bin/src/main/java/.../builtin/
├── RunSkillTool.java      # run_skill 工具
└── InstallSkillTool.java  # install_skill 工具

example-skills/            # 示例 skill
├── README.md
├── explore/SKILL.md       # 代码探索 skill
└── java-conventions/SKILL.md
```

## 📝 Skill 格式

```markdown
---
name: my-skill
description: 一句话描述（必须，会出现在 skill 索引中）
runAs: subagent  # inline（默认）| subagent
allowed-tools: read_file, glob, grep  # subagent 模式可用的工具
---

# Skill 正文

Markdown 格式的指令...
```

## 🗂️ 目录位置

### 项目级（优先级高）
```
<project>/.agent4j/skills/    ← 推荐
<project>/.agents/skills/
<project>/.claude/skills/
```

### 全局级
```
~/.agent4j/skills/            ← 推荐
~/.agents/skills/
~/.claude/skills/
```

## 🛠️ 工具

### run_skill
调用已安装的 skill。

```json
{
  "name": "run_skill",
  "arguments": {
    "name": "explore",
    "arguments": "Find all authentication handlers"
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
    "body": "# Instructions\n\nWhen invoked...",
    "scope": "project",
    "runAs": "inline"
  }
}
```

## 🚀 快速开始

### 1. 安装示例 skill
```bash
cp -r example-skills/* ~/.agent4j/skills/
```

### 2. 启动 Agent
```bash
java -cp ... site.sorghum.agent4j.bin.Agent4jApp
```

### 3. 使用 skill
```
> 帮我探索一下这个项目的认证机制

# LLM 会自动调用 explore skill
```

### 4. 创建自定义 skill
```
> 创建一个 skill 来帮我写单元测试

# LLM 会调用 install_skill 创建新 skill
```

## 📊 Skill 索引

系统启动时，会在系统提示中注入 skill 索引：

```
# Skills — playbooks you can invoke

- explore [🧬 subagent] — Run a focused read-only codebase investigation
- java-conventions — Java coding conventions for this project
```

LLM 根据这个索引决定调用哪个 skill。

## 🎯 Skill 类型

### Inline Skill（默认）
正文直接插入 LLM 上下文，LLM 阅读后执行。

**适用场景**：
- 编码规范
- 项目约定
- 常用模板

### Subagent Skill
在隔离的子代理中运行，只返回最终结果。节省主上下文 token。

**适用场景**：
- 代码探索
- 深度研究
- 代码审查

## 🎁 内置 Skill

| Skill | 类型 | 描述 |
|-------|------|------|
| explore | subagent | 代码探索，深度调查代码库 |
| java-conventions | inline | Java 编码规范 |

## 💡 最佳实践

1. **描述要精确** — LLM 根据描述选择 skill
2. **正文要清晰** — 写给 LLM 看的指令
3. **选择合适的模式** — 简单用 inline，复杂用 subagent
4. **限制工具** — subagent 只给必要的工具
5. **定期维护** — 删除不再需要的 skill

## 📚 参考

- [Claude Code Skills](https://docs.anthropic.com/claude-code/skills)
- [DeepSeek-Reasonix Skills](https://github.com/...)