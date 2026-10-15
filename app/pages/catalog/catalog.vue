<template>
  <view class="page">
    <!-- 顶部：书名 + 章数 + 排序切换 + 下载 -->
    <view class="head">
      <view class="head-info">
        <text class="book-name">{{ bookName }}</text>
        <text class="chapter-count">共 {{ displayList.length }} 章 · 已下载 {{ downloaded }} 章</text>
      </view>
      <text class="sort-btn" @click="toggleSort">{{ asc ? '↓ 倒序' : '↑ 正序' }}</text>
      <text class="dl-btn" @click="openDlPanel">下载</text>
    </view>

    <!-- 章节列表 -->
    <scroll-view class="list" scroll-y :scroll-into-view="scrollTo">
      <view
        v-for="(ch, i) in displayList"
        :key="ch.key"
        :id="'ch' + ch.index"
        class="chapter-item"
        @click="pick(ch.index)"
      >
        <text class="chapter-title" :class="{ current: ch.index === currentIdx }">{{ ch.title }}</text>
        <text v-if="isCached(ch.index)" class="cached-tag">已存</text>
        <text v-if="ch.index === currentIdx" class="current-mark">✓</text>
      </view>
    </scroll-view>

    <!-- 下载范围选择面板 -->
    <view v-if="dlPanel" class="dl-mask" @click="dlPanel = false">
      <view class="dl-dialog" @click.stop>
        <text class="dl-title">批量下载</text>
        <view class="dl-options">
          <text class="dl-opt" :class="{ on: dlMode === 'all' }" @click="dlMode = 'all'">全本</text>
          <text class="dl-opt" :class="{ on: dlMode === 'last50' }" @click="dlMode = 'last50'">最近50章</text>
          <text class="dl-opt" :class="{ on: dlMode === 'range' }" @click="dlMode = 'range'">自定义</text>
        </view>
        <view v-if="dlMode === 'range'" class="dl-range">
          <input class="dl-input" type="number" v-model="dlFrom" placeholder="起(如1)" />
          <text class="dl-sep">—</text>
          <input class="dl-input" type="number" v-model="dlTo" placeholder="止(如100)" />
        </view>
        <button class="dl-start" :disabled="dlBusy" @click="startDownload">{{ dlBusy ? '下载已启动…' : '开始下载' }}</button>
      </view>
    </view>
  </view>
</template>

<script>
import api from '@/common/api.js'
import downloader from '@/common/downloader.js'

export default {
  data() {
    return {
      bookId: '',
      book: null,
      chapters: [],
      asc: true,        // true 正序 / false 倒序
      currentIdx: 0,
      scrollTo: '',
      downloaded: 0,    // 已下载章数
      dlPanel: false,   // 下载面板
      dlMode: 'all',    // all / last50 / range
      dlFrom: '',
      dlTo: '',
      dlBusy: false
    }
  },
  computed: {
    bookName() {
      return this.book ? this.book.name : '目录'
    },
    /** 排序后的展示列表（带原始章号） */
    displayList() {
      const list = this.chapters.map((ch, index) => ({ title: ch.title, index: index, key: 'k' + index }))
      return this.asc ? list : list.slice().reverse()
    }
  },
  onLoad(params) {
    this.bookId = params.bookId
    this.book = (uni.getStorageSync('bookshelf') || []).find(b => b.id === this.bookId)
    const prog = uni.getStorageSync('progress_' + this.bookId) || {}
    this.currentIdx = prog.chapterIndex || 0

    this.chapters = uni.getStorageSync('chapters_' + this.bookId) || []
    this.refreshDownloaded()
    // 下载进度变化时刷新「已下载」数
    uni.$on('downloadProgress', this.onDlProgress)
    if (this.chapters.length === 0 && this.book) {
      api.getChapters(this.book.sourceId, this.book.bookUrl).then(list => {
        this.chapters = list || []
        uni.setStorageSync('chapters_' + this.bookId, this.chapters)
        this.locateCurrent()
      }).catch(err => {
        uni.showToast({ title: '目录获取失败：' + err, icon: 'none' })
      })
    } else {
      this.locateCurrent()
    }
  },
  onUnload() {
    uni.$off('downloadProgress', this.onDlProgress)
  },
  methods: {
    onDlProgress() {
      this.refreshDownloaded()
    },
    refreshDownloaded() {
      this.downloaded = downloader.downloadedCount(this.bookId, this.chapters.length)
    },
    /** 打开下载面板 */
    openDlPanel() {
      if (this.chapters.length === 0) {
        uni.showToast({ title: '目录还没加载好', icon: 'none' })
        return
      }
      this.dlBusy = false
      this.dlPanel = true
    },
    /** 按所选范围启动下载 */
    startDownload() {
      const total = this.chapters.length
      let indices = []
      if (this.dlMode === 'all') {
        for (let i = 0; i < total; i++) indices.push(i)
      } else if (this.dlMode === 'last50') {
        const from = Math.max(0, total - 50)
        for (let i = from; i < total; i++) indices.push(i)
      } else {
        const from = Math.max(1, parseInt(this.dlFrom) || 1) - 1
        const to = Math.min(total, parseInt(this.dlTo) || total) - 1
        if (from > to) {
          uni.showToast({ title: '起始章号不对', icon: 'none' })
          return
        }
        for (let i = from; i <= to; i++) indices.push(i)
      }
      this.dlBusy = true
      downloader.startTask(this.book, this.chapters, indices)
      this.dlPanel = false
      uni.showToast({ title: '已开始后台下载', icon: 'success' })
    },
    /** 滚动到当前阅读章 */
    locateCurrent() {
      if (this.chapters.length === 0) return
      // 等列表渲染完再定位
      setTimeout(() => {
        this.scrollTo = 'ch' + Math.max(0, this.currentIdx - 2)
      }, 200)
    },
    toggleSort() {
      this.asc = !this.asc
    },
    isCached(index) {
      return !!uni.getStorageSync('chapter_' + this.bookId + '_' + index)
    },
    /** 点章 → 通知阅读页跳转并返回 */
    pick(index) {
      uni.$emit('jumpToChapter', index)
      uni.navigateBack()
    }
  }
}
</script>

<style>
.page {
  height: 100vh;
  background: #ffffff;
  display: flex;
  flex-direction: column;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #eeeeee;
}
.head-info { flex: 1; overflow: hidden; }
.book-name {
  font-size: 32rpx;
  font-weight: bold;
  color: #222222;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chapter-count {
  margin-top: 4rpx;
  font-size: 24rpx;
  color: #909399;
  display: block;
}
.sort-btn {
  color: #e8383d;
  font-size: 28rpx;
  padding: 10rpx 20rpx;
}
.dl-btn {
  color: #ffffff;
  background: #e8383d;
  font-size: 26rpx;
  padding: 10rpx 24rpx;
  border-radius: 28rpx;
}
/* 下载面板 */
.dl-mask {
  position: fixed;
  left: 0; top: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 99;
  display: flex;
  align-items: flex-end;
}
.dl-dialog {
  width: 100%;
  background: #ffffff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 40rpx calc(50rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.dl-title {
  font-size: 34rpx;
  font-weight: bold;
  color: #222222;
  display: block;
  text-align: center;
}
.dl-options {
  margin-top: 28rpx;
  display: flex;
  justify-content: space-around;
}
.dl-opt {
  padding: 16rpx 36rpx;
  border: 1rpx solid #e0e0e0;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #222222;
}
.dl-opt.on {
  color: #e8383d;
  border-color: #e8383d;
}
.dl-range {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dl-input {
  width: 180rpx;
  height: 72rpx;
  background: #f5f6f7;
  border-radius: 12rpx;
  text-align: center;
  font-size: 28rpx;
}
.dl-sep {
  margin: 0 20rpx;
  color: #909399;
}
.dl-start {
  margin-top: 32rpx;
  background: #e8383d;
  color: #ffffff;
  border-radius: 44rpx;
}
.list { flex: 1; }
.chapter-item {
  display: flex;
  align-items: center;
  padding: 26rpx 32rpx;
  border-bottom: 1rpx solid #f5f5f5;
}
.chapter-title {
  flex: 1;
  font-size: 28rpx;
  color: #444444;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chapter-title.current {
  color: #e8383d;
  font-weight: bold;
}
.cached-tag {
  font-size: 20rpx;
  color: #1aad19;
  margin-right: 12rpx;
}
.current-mark {
  color: #e8383d;
  font-size: 28rpx;
}
</style>
