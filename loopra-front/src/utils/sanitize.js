import DOMPurify from 'dompurify'

/**
 * 净化HTML内容，防止XSS攻击
 * @param {string} html - 原始HTML字符串
 * @returns {string} - 净化后的安全HTML
 */
export function sanitize(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'p', 'br', 'hr',
      'strong', 'em', 'b', 'i', 'u', 's', 'del', 'mark', 'sub', 'sup',
      'a', 'img',
      'ul', 'ol', 'li',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'pre', 'code', 'blockquote',
      'div', 'span',
      'details', 'summary',
      'input', // 用于 checkbox
    ],
    ALLOWED_ATTR: [
      'href', 'target', 'rel', 'title', 'alt', 'src',
      'class', 'id', 'style',
      'type', 'checked', 'disabled',
      'data-*', // 允许 data 属性
    ],
    ALLOW_DATA_ATTR: true,
  })
}
