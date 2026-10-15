/**
 * 通用工具函数
 */

/**
 * 由 bookUrl 生成书架唯一 ID（djb2 哈希，简单够用）
 * 同一来源同一本书的 bookUrl 固定不变，所以 ID 稳定
 */
function bookId(bookUrl) {
  let h = 5381
  for (let i = 0; i < bookUrl.length; i++) {
    h = ((h << 5) + h + bookUrl.charCodeAt(i)) & 0x7fffffff
  }
  return h.toString(36)
}

/** 阅读百分比文案：0 显示「未读」 */
function percentText(index, total) {
  if (!total || total <= 0) return '未读'
  const p = Math.floor(((index + 1) / total) * 100)
  return '已读 ' + Math.min(p, 100) + '%'
}

/** 时间格式化：今天显示 HH:mm，否则显示 MM-DD */
function shortTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const pad = (n) => (n < 10 ? '0' + n : '' + n)
  if (d.toDateString() === now.toDateString()) {
    return pad(d.getHours()) + ':' + pad(d.getMinutes())
  }
  return (d.getMonth() + 1) + '-' + pad(d.getDate())
}

export default { bookId, percentText, shortTime }
