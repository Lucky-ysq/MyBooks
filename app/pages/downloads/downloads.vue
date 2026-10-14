<template>
  <view class="page">
    <!-- 汇总 -->
    <view class="summary">
      <text class="sum-text">共 {{ tasks.length }} 本下载任务 · 本地缓存占用 {{ cacheSize }}</text>
      <text class="clear-btn" @click="clearAll">清空全部</text>
    </view>

    <!-- 空状态 -->
    <view v-if="tasks.length === 0" class="empty">
      <text class="empty-icon">📥</text>
      <text class="empty-text">还没有下载任务</text>
      <text class="empty-tip">打开书的目录页，点「下载」批量缓存章节</text>
    </view>

    <!-- 任务列表 -->
    <view class="task-list">
      <view v-for="t in tasks" :key="t.bookId" class="task-item">
        <view class="t-head">
          <text class="t-name">{{ bookName(t.bookId) }}</text>
          <text class="t-status" :class="t.status">{{ statusText(t) }}</text>
        </view>
        <view class="t-bar">
          <view class="t-bar-inner" :style="{ width: percent(t) + '%' }"></view>
        </view>
        <view class="t-foot">
          <text class="t-count">{{ t.done.length + doneExtra(t) }}/{{ t.total }} 章</text>
          <view class="t-btns">
            <text
              v-if="t.status === 'running'"
              class="t-btn"
              @click="pause(t)"
            >暂停</text>
            <text
              v-if="t.status === 'paused'"
              class="t-btn resume"
              @click="resume(t)"
            >继续</text>
            <text
              v-if="t.status === 'done' && t.done.length + doneExtra(t) < t.total"
              class="t-btn resume"
              @click="resume(t)"
            >补下未完成</text>
            <text class="t-btn del" @click="remove(t)">删除缓存</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
const downloader = require('@/common/downloader.js')

export default {
  data() {
    return {
      tasks: [],
      cacheSize: '0KB'
    }
  },
  onShow() {
    this.load()
  },
  onLoad() {
    uni.$on('downloadProgress', this.onProgress)
  },
  onUnload() {
    uni.$off('downloadProgress', this.onProgress)
  },
  methods: {
    onProgress() {
      this.load()
    },
    load() {
      this.tasks = downloader.allTasks()
      const info = uni.getStorageInfoSync()
      this.cacheSize = info.currentSize + 'KB'
      if (info.currentSize > 1024) {
        this.cacheSize = (info.currentSize / 1024).toFixed(1) + 'MB'
      }
    },
    bookName(bookId) {
      const shelf = uni.getStorageSync('bookshelf') || []
      const b = shelf.find(x => x.id === bookId)
      return b ? b.name : bookId
    },
    statusText(t) {
      if (t.status === 'running') return '下载中'
      if (t.status === 'paused') return '已暂停'
      return '已完成'
    },
    /** done 数组之外的缓存章（本地已有但任务没记录的） */
    doneExtra(t) {
      const cached = downloader.downloadedCount(t.bookId, t.total)
      return Math.max(0, cached - t.done.length)
    },
    percent(t) {
      if (!t.total) return 0
      const done = Math.min(t.done.length + this.doneExtra(t), t.total)
      return Math.floor((done / t.total) * 100)
    },
    pause(t) {
      downloader.pauseTask(t.bookId)
    },
    resume(t) {
      downloader.resumeTask(t.bookId)
    },
    remove(t) {
      uni.showModal({
        title: '删除缓存',
        content: '删除《' + this.bookName(t.bookId) + '》的 ' + t.total + ' 章本地缓存？',
        confirmColor: '#E8383D',
        success: (res) => {
          if (res.confirm) {
            downloader.removeTask(t.bookId, t.total)
            this.load()
          }
        }
      })
    },
    clearAll() {
      if (this.tasks.length === 0) return
      uni.showModal({
        title: '清空全部缓存',
        content: '将删除所有下载任务和章节缓存，书架不受影响',
        confirmColor: '#E8383D',
        success: (res) => {
          if (!res.confirm) return
          for (const t of this.tasks) {
            downloader.removeTask(t.bookId, t.total)
          }
          this.load()
        }
      })
    }
  }
}
</script>

<style>
.page {
  min-height: 100vh;
  background: #f5f6f7;
  padding: 24rpx;
}
.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8rpx 8rpx 24rpx;
}
.sum-text { font-size: 24rpx; color: #909399; }
.clear-btn { font-size: 26rpx; color: #e8383d; }
.empty {
  padding-top: 200rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-icon { font-size: 90rpx; }
.empty-text {
  margin-top: 24rpx;
  font-size: 30rpx;
  color: #222222;
}
.empty-tip {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9aa0a6;
}
.task-list {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 8rpx 28rpx;
}
.task-item {
  padding: 28rpx 0;
  border-bottom: 1rpx solid #f5f5f5;
}
.task-item:last-child { border-bottom: none; }
.t-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.t-name {
  font-size: 30rpx;
  font-weight: bold;
  color: #222222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.t-status { font-size: 24rpx; color: #909399; margin-left: 16rpx; }
.t-status.running { color: #e8383d; }
.t-status.done { color: #1aad19; }
.t-bar {
  margin-top: 20rpx;
  height: 12rpx;
  background: #f0f1f3;
  border-radius: 6rpx;
  overflow: hidden;
}
.t-bar-inner {
  height: 100%;
  background: #e8383d;
  border-radius: 6rpx;
  transition: width 0.3s;
}
.t-foot {
  margin-top: 16rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.t-count { font-size: 24rpx; color: #909399; }
.t-btns { display: flex; }
.t-btn {
  font-size: 26rpx;
  color: #666666;
  margin-left: 28rpx;
}
.t-btn.resume { color: #e8383d; }
.t-btn.del { color: #909399; }
</style>
