<template>
  <view class="page">
    <!-- 顶部：标题 + 搜索入口 -->
    <view class="header">
      <text class="title">遇夏小说</text>
      <view class="search-bar" @click="goSearch">
        <text class="search-icon">🔍</text>
        <text class="search-placeholder">搜索书名或作者</text>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-if="books.length === 0" class="empty">
      <text class="empty-icon">📚</text>
      <text class="empty-text">书架还是空的</text>
      <text class="empty-tip">点上方搜索，找一本书加入书架吧</text>
    </view>

    <!-- 书架列表 -->
    <view class="book-list">
      <view v-for="book in books" :key="book.id" class="book-item" @click="openBook(book)" @longpress="removeBook(book)">
        <image class="cover" :src="book.cover ? book.cover : '/static/default-cover.png'" mode="aspectFill" />
        <view class="info">
          <text class="name">{{ book.name }}</text>
          <text class="author">{{ book.author || '佚名' }}</text>
          <text class="progress">{{ progressText(book) }}</text>
          <text class="latest">{{ book.latestChapter || '' }}</text>
        </view>
        <view v-if="isDownloaded(book)" class="badge">已下载</view>
      </view>
    </view>
  </view>
</template>

<script>
import api from '@/common/api.js'
import util from '@/common/util.js'

export default {
  data() {
    return {
      books: [] // 书架数组（来自本地存储）
    }
  },
  onShow() {
    this.loadBooks()
  },
  methods: {
    /** 从本地存储读书架 */
    loadBooks() {
      const list = uni.getStorageSync('bookshelf') || []
      // 最近阅读的排前面
      list.sort((a, b) => (b.lastReadTime || 0) - (a.lastReadTime || 0))
      this.books = list
    },
    progressText(book) {
      if (!book.lastChapterTitle) return '未读'
      return '第' + (book.lastChapterIndex + 1) + '章 · ' + util.percentText(book.lastChapterIndex, book.totalChapters)
    },
    isDownloaded(book) {
      const task = uni.getStorageSync('download_' + book.id)
      return task && task.status === 'done'
    },
    goSearch() {
      uni.navigateTo({ url: '/pages/search/search' })
    },
    /** 点书进阅读页（M3 实现 reader，先占位） */
    openBook(book) {
      uni.navigateTo({
        url: '/pages/reader/reader?bookId=' + book.id
      })
    },
    /** 长按删除（连带本地章节缓存） */
    removeBook(book) {
      uni.showModal({
        title: '移出书架',
        content: '《' + book.name + '》将从书架移除' + (this.isDownloaded(book) ? '，本地下载章节一并删除' : ''),
        confirmText: '删除',
        confirmColor: '#E8383D',
        success: (res) => {
          if (!res.confirm) return
          const list = (uni.getStorageSync('bookshelf') || []).filter(b => b.id !== book.id)
          uni.setStorageSync('bookshelf', list)
          uni.removeStorageSync('progress_' + book.id)
          // 删除章节缓存
          const total = book.totalChapters || 0
          for (let i = 0; i < total; i++) {
            uni.removeStorageSync('chapter_' + book.id + '_' + i)
          }
          uni.removeStorageSync('chapters_' + book.id)
          uni.removeStorageSync('download_' + book.id)
          this.loadBooks()
          uni.showToast({ title: '已移除', icon: 'none' })
        }
      })
    }
  }
}
</script>

<style>
.page {
  min-height: 100vh;
  background: #ffffff;
}
.header {
  padding: 24rpx 32rpx 8rpx;
}
.title {
  font-size: 44rpx;
  font-weight: bold;
  color: #222222;
}
.search-bar {
  margin-top: 24rpx;
  height: 72rpx;
  background: #f5f6f7;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  padding: 0 28rpx;
}
.search-icon {
  font-size: 26rpx;
  margin-right: 12rpx;
}
.search-placeholder {
  color: #9aa0a6;
  font-size: 28rpx;
}
.empty {
  padding-top: 220rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-icon { font-size: 96rpx; }
.empty-text {
  margin-top: 24rpx;
  font-size: 32rpx;
  color: #222222;
}
.empty-tip {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #9aa0a6;
}
.book-list { padding-bottom: 40rpx; }
.book-item {
  position: relative;
  display: flex;
  padding: 24rpx 32rpx;
}
.cover {
  width: 130rpx;
  height: 174rpx;
  background: #f0f1f3;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.info {
  flex: 1;
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 6rpx 0;
  overflow: hidden;
}
.name {
  font-size: 32rpx;
  font-weight: bold;
  color: #222222;
  /* 超出一行省略 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.author { font-size: 26rpx; color: #909399; }
.progress { font-size: 26rpx; color: #909399; }
.latest {
  font-size: 26rpx;
  color: #9aa0a6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.badge {
  position: absolute;
  top: 24rpx;
  right: 32rpx;
  background: #e8383d;
  color: #ffffff;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
}
</style>
