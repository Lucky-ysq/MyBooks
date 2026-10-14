<template>
  <view class="page">
    <!-- 搜索区 -->
    <view class="search-row">
      <input class="search-input" v-model="keyword" placeholder="搜索书名或作者" confirm-type="search" @confirm="doSearch" />
      <button class="search-btn" size="mini" @click="doSearch" :disabled="loading">搜索</button>
    </view>

    <!-- 书源选择 -->
    <view class="source-row" v-if="sources.length > 0">
      <text class="source-label">书源：</text>
      <picker :range="sourceNames" @change="onSourceChange">
        <text class="source-value">{{ currentSource ? currentSource.name : '选择书源' }} ▾</text>
      </picker>
    </view>

    <!-- 结果列表 -->
    <view class="result-list">
      <view v-for="(item, index) in results" :key="index" class="result-item" @click="showDetail(item)">
        <image class="cover" :src="item.coverUrl ? coverProxy(item.coverUrl) : '/static/default-cover.png'" mode="aspectFill" />
        <view class="info">
          <text class="name">{{ item.name }}</text>
          <text class="author">{{ item.author || '佚名' }}</text>
          <text class="intro">{{ item.intro || '暂无简介' }}</text>
        </view>
      </view>
    </view>

    <!-- 状态提示 -->
    <view v-if="loading" class="tip"><text>搜索中…</text></view>
    <view v-if="!loading && searched && results.length === 0" class="tip"><text>没有找到相关图书，换个关键字或书源试试</text></view>
    <view v-if="error" class="tip"><text class="error">{{ error }}</text></view>

    <!-- 书籍详情弹层 -->
    <view v-if="detail" class="mask" @click="detail = null">
      <view class="dialog" @click.stop>
        <view class="dialog-head">
          <image class="dialog-cover" :src="detail.coverUrl ? coverProxy(detail.coverUrl) : '/static/default-cover.png'" mode="aspectFill" />
          <view class="dialog-info">
            <text class="dialog-name">{{ detail.name }}</text>
            <text class="dialog-author">{{ detail.author || '佚名' }}</text>
            <text class="dialog-latest">{{ detail.latestChapter || '' }}</text>
          </view>
        </view>
        <scroll-view scroll-y class="dialog-intro">
          <text>{{ detail.intro || '暂无简介' }}</text>
        </scroll-view>
        <view class="dialog-btns">
          <button class="btn btn-ghost" size="mini" @click="detail = null">取消</button>
          <button class="btn btn-primary" size="mini" :disabled="adding" @click="addShelf">{{ inShelf ? '已在书架' : '加入书架' }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
const api = require('@/common/api.js')
const util = require('@/common/util.js')

export default {
  data() {
    return {
      keyword: '',
      sources: [],        // 启用的书源
      sourceNames: [],    // picker 显示用
      currentSource: null,
      results: [],
      loading: false,
      searched: false,
      error: '',
      detail: null,       // 详情弹层数据
      adding: false
    }
  },
  onLoad() {
    this.loadSources()
  },
  methods: {
    coverProxy(url) {
      return api.coverProxy(url)
    },
    /** 加载启用书源，默认选 isDefault 的 */
    loadSources() {
      api.getSources().then(list => {
        this.sources = (list || []).filter(s => s.enabled)
        this.sourceNames = this.sources.map(s => s.name)
        if (this.sources.length > 0) {
          const def = this.sources.find(s => s.isDefault)
          this.currentSource = def || this.sources[0]
        } else {
          uni.showToast({ title: '还没有可用书源，请到「我的-书源管理」添加', icon: 'none', duration: 2500 })
        }
      }).catch(err => {
        this.error = String(err)
      })
    },
    onSourceChange(e) {
      this.currentSource = this.sources[e.detail.value]
    },
    doSearch() {
      const key = (this.keyword || '').trim()
      if (!key) {
        uni.showToast({ title: '请输入关键字', icon: 'none' })
        return
      }
      if (!this.currentSource) {
        uni.showToast({ title: '请先选择书源', icon: 'none' })
        return
      }
      this.loading = true
      this.error = ''
      this.searched = false
      api.search(this.currentSource.id, key).then(list => {
        this.results = list || []
        this.searched = true
        this.loading = false
      }).catch(err => {
        this.error = String(err)
        this.results = []
        this.loading = false
      })
    },
    /** 点搜索结果：拉详情弹窗 */
    showDetail(item) {
      if (this.adding) return
      uni.showLoading({ title: '加载详情' })
      api.getDetail(this.currentSource.id, item.bookUrl).then(d => {
        uni.hideLoading()
        // 详情缺的字段用搜索结果补
        this.detail = {
          name: d.name || item.name,
          author: d.author || item.author,
          coverUrl: d.coverUrl || item.coverUrl,
          intro: d.intro || item.intro,
          latestChapter: d.latestChapter,
          bookUrl: item.bookUrl,
          sourceId: this.currentSource.id
        }
      }).catch(err => {
        uni.hideLoading()
        uni.showToast({ title: String(err), icon: 'none' })
      })
    },
    /** 加入书架：拉目录记总章数，写本地存储 */
    addShelf() {
      if (this.inShelf) {
        this.detail = null
        return
      }
      this.adding = true
      const d = this.detail
      api.getChapters(d.sourceId, d.bookUrl).then(chapters => {
        const shelf = uni.getStorageSync('bookshelf') || []
        const id = util.bookId(d.bookUrl)
        shelf.push({
          id: id,
          name: d.name,
          author: d.author,
          cover: d.coverUrl,
          sourceId: d.sourceId,
          bookUrl: d.bookUrl,
          totalChapters: chapters.length,
          latestChapter: d.latestChapter || (chapters.length ? chapters[chapters.length - 1].title : ''),
          lastChapterIndex: -1, // 未读
          lastChapterTitle: '',
          lastReadTime: 0,
          addedTime: Date.now()
        })
        uni.setStorageSync('bookshelf', shelf)
        // 目录也缓存下来（后续下载/阅读要用，断网可看）
        uni.setStorageSync('chapters_' + id, chapters)
        this.adding = false
        this.detail = null
        uni.showToast({ title: '已加入书架', icon: 'success' })
      }).catch(err => {
        this.adding = false
        uni.showToast({ title: '目录获取失败：' + err, icon: 'none', duration: 2500 })
      })
    }
  },
  computed: {
    inShelf() {
      if (!this.detail) return false
      const shelf = uni.getStorageSync('bookshelf') || []
      return shelf.some(b => b.bookUrl === this.detail.bookUrl)
    }
  }
}
</script>

<style>
.page {
  min-height: 100vh;
  background: #ffffff;
  padding: 24rpx 32rpx;
}
.search-row {
  display: flex;
  align-items: center;
}
.search-input {
  flex: 1;
  height: 72rpx;
  background: #f5f6f7;
  border-radius: 36rpx;
  padding: 0 28rpx;
  font-size: 28rpx;
}
.search-btn {
  margin-left: 16rpx;
  background: #e8383d;
  color: #ffffff;
}
.source-row {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
}
.source-label { color: #909399; font-size: 26rpx; }
.source-value { color: #e8383d; font-size: 26rpx; }

.result-list { margin-top: 12rpx; }
.result-item {
  display: flex;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #eeeeee;
}
.cover {
  width: 110rpx;
  height: 148rpx;
  background: #f0f1f3;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.info {
  flex: 1;
  margin-left: 20rpx;
  overflow: hidden;
}
.name {
  font-size: 30rpx;
  font-weight: bold;
  color: #222222;
}
.author {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #909399;
}
.intro {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #9aa0a6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tip {
  margin-top: 60rpx;
  text-align: center;
  color: #9aa0a6;
  font-size: 26rpx;
}
.error { color: #e8383d; }

/* 详情弹层 */
.mask {
  position: fixed;
  left: 0; top: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99;
}
.dialog {
  width: 620rpx;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx;
}
.dialog-head { display: flex; }
.dialog-cover {
  width: 120rpx;
  height: 160rpx;
  background: #f0f1f3;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.dialog-info {
  margin-left: 20rpx;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.dialog-name { font-size: 34rpx; font-weight: bold; color: #222222; }
.dialog-author { font-size: 26rpx; color: #909399; }
.dialog-latest { font-size: 24rpx; color: #9aa0a6; }
.dialog-intro {
  margin-top: 20rpx;
  max-height: 240rpx;
  font-size: 26rpx;
  color: #666666;
  line-height: 1.6;
}
.dialog-btns {
  margin-top: 28rpx;
  display: flex;
  justify-content: flex-end;
}
.btn-primary { background: #e8383d; color: #ffffff; }
.btn-ghost { background: #f5f6f7; color: #666666; margin-right: 16rpx; }
</style>
