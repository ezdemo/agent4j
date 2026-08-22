/* @vitest-environment jsdom */

import {beforeAll, describe, expect, it, afterEach} from 'vitest'
import {md, initHighlight, highlightCode, highlightVersion, applyHighlightTheme, detectLanguage} from './highlight'
import {sanitize} from './sanitize'

beforeAll(async () => {
  // 等待 Shiki 预加载完成，保证同步高亮可用
  await initHighlight()
})

afterEach(() => {
  applyHighlightTheme('light')
})

describe('markdown 数学公式渲染（KaTeX / texmath）', () => {
  it('渲染单行 $$...$$ 显示公式（AI 回复常见格式）', () => {
    const html = sanitize(md.render('答案是 $$(6 - 5)! \\times 4! \\times 1 = 24$$'))
    expect(html).toContain('katex')
    expect(html).toContain('katex-display')
    expect(html).not.toContain('$$')
  })

  it('渲染独立行的块级 $$ 公式（前后空行）', () => {
    const html = sanitize(md.render('公式：\n\n$$\nE = mc^2\n$$\n\n结束'))
    expect(html).toContain('katex-display')
    expect(html).not.toContain('$$')
  })

  it('渲染行内 $...$ 公式', () => {
    const html = sanitize(md.render('设 $x + 1 = 2$ 成立'))
    expect(html).toContain('katex')
    expect(html).not.toContain('katex-display')
  })

  it('sanitize 保留 KaTeX 输出的 class 与内联 style', () => {
    const html = sanitize(md.render('$\\frac{1}{2}$'))
    expect(html).toContain('class="katex')
    expect(html).toMatch(/style="/)
  })

  it('非法 LaTeX 不抛错，降级展示源码', () => {
    expect(() => sanitize(md.render('$\\invalidcmd{x}$'))).not.toThrow()
  })

  it('普通美元符号不误判为公式', () => {
    const html = sanitize(md.render('价格是 $5 和 $6'))
    expect(html).not.toContain('katex')
    expect(html).toContain('$5')
  })

  it('代码块内的 $ 不被渲染为公式', () => {
    const html = sanitize(md.render('```js\nconst price = "$5";\n```'))
    expect(html).not.toContain('katex')
  })
})

describe('代码块渲染（Shiki）', () => {
  it('fence 输出代码块容器 + 头部（图标/语言/行数）+ 复制按钮', () => {
    const html = sanitize(md.render('```js\nconst a = 1\n```'))
    expect(html).toContain('code-block-wrap')
    expect(html).toContain('code-block-top')
    expect(html).toContain('code-block-icon')
    expect(html).toContain('<svg') // 图标（SVG）不被 sanitize 剥离
    expect(html).toContain('rx="2"') // SVG 属性（x/y/rx/ry）保留
    expect(html).toContain('code-block-lang')
    expect(html).toContain('code-block-meta')
    expect(html).toContain('1 行')
    expect(html).toContain('code-copy-btn')
    expect(html).toMatch(/code-block-top[^]*?code-copy-btn/) // 复制按钮在顶栏内
    expect(html).toContain('language-js')
  })

  it('Shiki 已就绪时输出 token 高亮 span', () => {
    const html = sanitize(md.render('```python\ndef hello():\n    return 42\n```'))
    expect(html).toContain('style="')
  })

  it('未识别语言按纯文本展示且不报错', () => {
    const html = sanitize(md.render('```nosuchlang\nx = 1\n```'))
    expect(html).toContain('x = 1')
  })

  it('超过 6 行的代码块带折叠头部（图标 + 语言 + 行数）', () => {
    const html = sanitize(md.render('```js\nl1\nl2\nl3\nl4\nl5\nl6\nl7\n```'))
    expect(html).toMatch(/code-block-wrap[^"]*collapsible/)
    expect(html).toContain('code-block-top')
    expect(html).toContain('code-block-icon')
    expect(html).toContain('code-block-chevron')
    expect(html).toContain('7 行')
  })

  it('6 行及以下的代码块不折叠，但顶栏与复制按钮始终展示', () => {
    const html = sanitize(md.render('```js\nl1\nl2\nl3\nl4\nl5\nl6\n```'))
    expect(html).toContain('code-block-wrap')
    expect(html).toContain('code-block-top')
    expect(html).toContain('code-copy-btn')
    expect(html).not.toContain('collapsible')
    expect(html).not.toContain('code-block-chevron') // 无折叠箭头，点击顶栏不生效
  })

  it('highlightCode 返回内联 token span（供 DiffViewer 复用）', async () => {
    const html = highlightCode('const a = 1', 'javascript')
    expect(html).not.toContain('<pre')
    expect(html).toContain('style="')
  })

  it('detectLanguage 按扩展名返回 shiki 语言', () => {
    expect(detectLanguage('src/foo.ts')).toBe('typescript')
    expect(detectLanguage('main.py')).toBe('python')
    expect(detectLanguage('style.css')).toBe('css')
    expect(detectLanguage('noext')).toBeNull()
  })

  it('主题切换后再次高亮输出不同配色，并 bump 渲染版本', () => {
    const v0 = highlightVersion.value
    const lightHtml = highlightCode('const a = 1', 'javascript')
    applyHighlightTheme('dark')
    const darkHtml = highlightCode('const a = 1', 'javascript')
    expect(highlightVersion.value).toBeGreaterThan(v0)
    expect(darkHtml).not.toBe(lightHtml)
  })
})

describe('链接渲染', () => {
  it('普通链接带 ai-link 类与新窗口属性', () => {
    const html = sanitize(md.render('[文档](https://example.com)'))
    expect(html).toContain('class="ai-link"')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener"')
  })

  it('本地文件路径渲染为 ai-file-link 并带 data-file-path', () => {
    const html = sanitize(md.render('打开 [App.vue](src/App.vue)'))
    expect(html).toContain('ai-file-link')
    expect(html).toContain('data-file-path="src/App.vue"')
  })
})

describe('GFM 扩展', () => {
  it('GitHub 告警框 > [!NOTE] 渲染为 markdown-alert 且不残留标记', () => {
    const html = sanitize(md.render('> [!NOTE]\n> 请先备份配置'))
    expect(html).toContain('markdown-alert-note')
    expect(html).not.toContain('[!NOTE]')
  })

  it('告警框 warning/caution 等类型均生效', () => {
    const html = sanitize(md.render('> [!WARNING]\n> 有风险'))
    expect(html).toContain('markdown-alert-warning')
  })

  it('Obsidian 风格 :::tip 容器', () => {
    const html = sanitize(md.render(':::tip\n换个思路\n:::'))
    expect(html).toContain('markdown-alert-tip')
    expect(html).not.toContain(':::tip')
  })

  it('任务列表 - [x] 渲染为 checkbox', () => {
    const html = sanitize(md.render('- [x] 已完成\n- [ ] 未完成'))
    expect(html).toContain('type="checkbox"')
    expect(html).toContain('checked')
  })

  it('表格渲染为 table 结构', () => {
    const html = sanitize(md.render('| A | B |\n|---|---|\n| 1 | 2 |'))
    expect(html).toContain('<table')
    expect(html).toContain('<th')
    expect(html).toContain('<td')
  })

  it('换行渲染为 <br>（breaks: true）', () => {
    const html = sanitize(md.render('第一行\n第二行'))
    expect(html).toContain('<br')
  })
})