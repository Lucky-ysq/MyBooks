<template>
  <view class="page">
    <!-- 顶部：书名 + 章数 + 排序切换 -->
    <view class="head">
      <view class="head-info">
        <text class="book-name">{{ bookName }}</text>
        <text class="chapter-count">共 {{ displayList.length }} 章</text>
      </view>
      <text class="sort-btn" @click="toggleSort">{{ asc ? '↓ 倒序' : '↑ 正序' }}</text>
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
  </view>
</template>

<script>
const api = require('@/common/api.js')

export default {
  data() {
    return {
      bookId: '',
      book: null,
      chapters: [],
      asc: true,        // true 正序 / false 倒序
      currentIdx: 0,
      scrollTo: ''
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
  methods: {
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
