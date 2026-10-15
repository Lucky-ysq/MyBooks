/**
 * 后端接口封装。
 * 服务器地址存在本地 storage（server_url），可在「我的-关于」里修改。
 * 所有方法返回 Promise，成功 resolve 后端 data，失败 reject 错误信息。
 */

function server() {
  return uni.getStorageSync('server_url') || 'http://127.0.0.1:8080'
}

/** 底层 GET 请求 */
function get(path, params) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: server() + path,
      method: 'GET',
      data: params || {},
      success(res) {
        const body = res.data
        if (body && body.code === 0) {
          resolve(body.data)
        } else {
          reject((body && body.msg) || '接口返回异常')
        }
      },
      fail(err) {
        reject('连不上服务器，请检查网络或「我的-关于」里的服务器地址')
      }
    })
  })
}

/** 底层请求（POST/PUT/DELETE） */
function send(path, method, data) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: server() + path,
      method: method,
      data: data || {},
      success(res) {
        const body = res.data
        if (body && body.code === 0) {
          resolve(body.data)
        } else {
          reject((body && body.msg) || '接口返回异常')
        }
      },
      fail() {
        reject('连不上服务器，请检查网络或「我的-关于」里的服务器地址')
      }
    })
  })
}

// ---------- 具体 API ----------

export default {
  server,

  /** 健康检查 */
  health() {
    return get('/api/health')
  },

  /** 书源列表 */
  getSources() {
    return get('/api/sources')
  },

  /** 新增书源 */
  saveSource(source) {
    return send('/api/sources', 'POST', source)
  },

  /** 修改书源 */
  updateSource(id, source) {
    return send('/api/sources/' + id, 'PUT', source)
  },

  /** 删除书源 */
  deleteSource(id) {
    return send('/api/sources/' + id, 'DELETE')
  },

  /** 设为默认书源 */
  setDefaultSource(id) {
    return send('/api/sources/' + id + '/default', 'POST')
  },

  /** 测试书源（返回搜索结果数组） */
  testSource(id, keyword) {
    return get('/api/sources/' + id + '/test', { keyword: keyword || '斗破' })
  },

  /** 搜索图书 */
  search(sourceId, keyword) {
    return get('/api/search', { sourceId: sourceId, keyword: keyword })
  },

  /** 书籍详情 */
  getDetail(sourceId, bookUrl) {
    return get('/api/book/detail', { sourceId: sourceId, bookUrl: bookUrl })
  },

  /** 章节目录 */
  getChapters(sourceId, bookUrl) {
    return get('/api/book/chapters', { sourceId: sourceId, bookUrl: bookUrl })
  },

  /** 章节正文 */
  getContent(sourceId, chapterUrl) {
    return get('/api/book/content', { sourceId: sourceId, chapterUrl: chapterUrl })
  },

  /** 封面代理地址（封面 URL 走后端代理防 403） */
  coverProxy(coverUrl) {
    if (!coverUrl) return ''
    return server() + '/api/image?url=' + encodeURIComponent(coverUrl)
  }
}
