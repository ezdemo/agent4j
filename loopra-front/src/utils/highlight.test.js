/* @vitest-environment jsdom */

import {describe, expect, it} from 'vitest'
import {md} from './highlight'
import {sanitize} from './sanitize'

describe('markdown 数学公式渲染（KaTeX）', () => {
  it('渲染单行 $$...$$ 显示公式（AI 回复常见格式）', () => {
    const html = sanitize(md.parse('答案是 $$(6 - 5)! \\times 4! \\times 1 = 24$$'))
    expect(html).toContain('katex')
    expect(html).toContain('katex-display')
    expect(html).not.toContain('$$')
  })

  it('渲染独立行的块级 $$ 公式（前后空行，marked 块级扩展标准格式）', () => {
    const html = sanitize(md.parse('公式：\n\n$$\nE = mc^2\n$$\n\n结束'))
    expect(html).toContain('katex-display')
    expect(html).not.toContain('$$')
  })

  it('渲染行内 $...$ 公式', () => {
    const html = sanitize(md.parse('设 $x + 1 = 2$ 成立'))
    expect(html).toContain('katex')
    expect(html).not.toContain('katex-display')
  })

  it('sanitize 保留 KaTeX 输出的 class 与内联 style', () => {
    const html = sanitize(md.parse('$\\frac{1}{2}$'))
    expect(html).toContain('class="katex')
    expect(html).toMatch(/style="/)
  })

  it('非法 LaTeX 不抛错，降级展示源码', () => {
    expect(() => sanitize(md.parse('$\\invalidcmd{x}$'))).not.toThrow()
    const html = sanitize(md.parse('$\\invalidcmd{x}$'))
    expect(html).toContain('katex')
  })

  it('普通美元符号不误判为公式', () => {
    const html = sanitize(md.parse('价格是 $5 和 $6'))
    expect(html).not.toContain('katex')
    expect(html).toContain('$5')
  })

  it('代码块内的 $ 不被渲染为公式', () => {
    const html = sanitize(md.parse('```js\nconst price = "$5";\n```'))
    expect(html).not.toContain('katex')
  })
})
