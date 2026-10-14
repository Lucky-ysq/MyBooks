/**
 * 全局下载器：目录页发起批量下载 → 逐章抓正文存本地。
 * 任务状态存 download_{bookId}：{ bookId, sourceId, bookUrl, total, chapters, queue:[待下章号], done:[已下章号], status }
 * status: running 下载中 / paused 已暂停 / done 全部完成
 * 页面通过 uni.$on('downloadProgress', bookId) 刷新界面。
 */
const api = require('./api.js')

let timer = null
let busy = false

/** 扫描 storage 里所有 download_ 开头的任务 */
function allTasks() {
  const keys = uni.getStorageInfoSync().keys
  const tasks = []
  for (const k of keys) {
    if (k.indexOf('download_') === 0) {
      const t = uni.getStorageSync(k)
      if (t && t.bookId) tasks.push(t)
    }
  }
  return tasks
}

function saveTask(t) {
  uni.setStorageSync('download_' + t.bookId, t)
}

function notify(bookId) {
  uni.$emit('downloadProgress', bookId)
}

/** 保证轮询循环在跑 */
function ensureLoop() {
  if (timer) return
  timer = setInterval(poll, 900)
}

function stopLoopIfIdle() {
  const running = allTasks().some(t => t.status === 'running')
  if (!running && timer) {
    clearInterval(timer)
    timer = null
  }
}

/** 轮询：取一个 running 任务，并发下 2 章 */
function poll() {
  if (busy) return
  const task = allTasks().find(t => t.status === 'running' && t.queue.length > 0)
  if (!task) {
    // 队列空的任务标记完成
    for (const t of allTasks()) {
      if (t.status === 'running' && t.queue.length === 0) {
        t.status = 'done'
        saveTask(t)
        notify(t.bookId)
      }
    }
    stopLoopIfIdle()
    return
  }
  busy = true
  const batch = task.queue.splice(0, 2) // 并发 2
  const jobs = batch.map(idx => downloadOne(task, idx))
  Promise.all(jobs).then(() => {
    busy = false
    saveTask(task)
    notify(task.bookId)
  }).catch(() => {
    busy = false
    saveTask(task)
    notify(task.bookId)
  })
}

/** 下载并缓存单章；本地已有则跳过 */
function downloadOne(task, idx) {
  const cacheKey = 'chapter_' + task.bookId + '_' + idx
  if (uni.getStorageSync(cacheKey)) {
    if (task.done.indexOf(idx) < 0) task.done.push(idx)
    return Promise.resolve()
  }
  const ch = task.chapters[idx]
  if (!ch) return Promise.resolve()
  return api.getContent(task.sourceId, ch.chapterUrl).then(c => {
    uni.setStorageSync('chapter_' + task.bookId + '_' + idx, { title: c.title, paragraphs: c.paragraphs })
    if (task.done.indexOf(idx) < 0) task.done.push(idx)
  })
}

/** 发起下载（从目录页调用）
 *  book: 书架对象；chapters: 目录数组；indices: 要下载的章号数组
 */
function startTask(book, chapters, indices) {
  // 过滤掉已缓存的章
  const queue = indices.filter(i => !uni.getStorageSync('chapter_' + book.id + '_' + i))
  const task = {
    bookId: book.id,
    sourceId: book.sourceId,
    bookUrl: book.bookUrl,
    total: chapters.length,
    chapters: chapters,
    done: [],
    queue: queue,
    status: 'running'
  }
  saveTask(task)
  ensureLoop()
  notify(book.id)
}

function pauseTask(bookId) {
  const t = uni.getStorageSync('download_' + bookId)
  if (t && t.status === 'running') {
    t.status = 'paused'
    saveTask(t)
    notify(bookId)
  }
}

function resumeTask(bookId) {
  const t = uni.getStorageSync('download_' + bookId)
  if (t) {
    t.status = 'running'
    saveTask(t)
    ensureLoop()
    notify(bookId)
  }
}

/** 删除任务 + 该书全部章节缓存 */
function removeTask(bookId, total) {
  const t = uni.getStorageSync('download_' + bookId)
  const n = total || (t && t.total) || 0
  for (let i = 0; i < n; i++) {
    uni.removeStorageSync('chapter_' + bookId + '_' + i)
  }
  uni.removeStorageSync('download_' + bookId)
  notify(bookId)
  stopLoopIfIdle()
}

/** 某本书已下载章数（以缓存为准，比 done 数组可靠） */
function downloadedCount(bookId, total) {
  let count = 0
  for (let i = 0; i < total; i++) {
    if (uni.getStorageSync('chapter_' + bookId + '_' + i)) count++
  }
  return count
}

/** App 启动时调用：有 running 任务则恢复轮询 */
function init() {
  const hasRunning = allTasks().some(t => t.status === 'running' && t.queue.length > 0)
  if (hasRunning) ensureLoop()
}

module.exports = {
  startTask,
  pauseTask,
  resumeTask,
  removeTask,
  downloadedCount,
  allTasks,
  init
}
