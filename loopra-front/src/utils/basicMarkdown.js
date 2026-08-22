import MarkdownIt from 'markdown-it'

/**
 * 轻量 Markdown（系统提示词 / 计划预览等不需要代码高亮的场景）
 */
export const basicMarkdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: false
})