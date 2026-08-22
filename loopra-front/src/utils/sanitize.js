import DOMPurify from 'dompurify'

// SVG 图标白名单（代码块/折叠头里的内联图标，如 CODE_ICON、CHEVRON_DOWN_ICON、COPY_ICON）
const SVG_TAGS = ['svg', 'path', 'polyline', 'circle', 'line', 'rect', 'polygon', 'animate']
const SVG_ATTRS = [
  'viewBox', 'd', 'points', 'fill', 'fill-rule', 'stroke', 'stroke-width',
  'stroke-linecap', 'stroke-linejoin', 'cx', 'cy', 'r', 'x', 'y', 'rx', 'ry',
  'x1', 'x2', 'y1', 'y2',
  'width', 'height', 'opacity', 'transform', 'dur', 'begin', 'values', 'repeatCount',
]

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
      'pre', 'code', 'blockquote', 'button',
      'div', 'span',
      'details', 'summary',
      'input', // 用于 checkbox
      ...SVG_TAGS,
    ],
    ALLOWED_ATTR: [
      'href', 'target', 'rel', 'title', 'alt', 'src',
      'class', 'id', 'style',
      'type', 'checked', 'disabled',
      'data-*', // 允许 data 属性
      ...SVG_ATTRS,
    ],
    ALLOW_DATA_ATTR: true,
  })
}
