<template>
  <view class="reader" :style="{ background: theme.bg }">

    <!-- 自定义顶部栏（工具栏呼出时显示） -->
    <view v-if="toolbar" class="topbar" :style="{ background: theme.barBg }">
      <view class="status-pad"></view>
      <view class="topbar-row">
        <text class="tb-back" @click="goBack">‹</text>
        <view class="tb-title">
          <text class="tb-book" :style="{ color: theme.text }">{{ book.name }}</text>
          <text class="tb-chapter" :style="{ color: theme.subText }">{{ content.title }}</text>
        </view>
      </view>
    </view>

    <!-- 正文：上下滚动模式 -->
    <scroll-view
      v-if="pageMode === 'scroll'"
      class="body"
      scroll-y
      :scroll-top="scrollTop"
      :style="{ background: theme.bg }"
      @scroll="onScroll"
    >
      <view class="content" :style="{ paddingTop: contentTop }" @click="toggleToolbar">
        <text class="chapter-title" :style="{ color: theme.text, fontSize: (fontSize + 4) + 'px' }">{{ content.title }}</text>
        <text
          v-for="(p, i) in content.paragraphs"
          :key="i"
          class="para"
          :style="{ color: theme.text, fontSize: fontSize + 'px', lineHeight: lineHeight, textIndent: '2em' }"
        >{{ p }}</text>
        <view class="chapter-end">
          <text v-if="currentIdx < total - 1" class="end-btn" :style="{ color: theme.subText }" @click.stop="nextChapter">下一章</text>
          <text v-else class="end-btn" :style="{ color: theme.subText }">已是最新章节</text>
        </view>
      </view>
      <view class="body-pad-bottom"></view>
    </scroll-view>

    <!-- 正文：左右滑动模式（滑动窗口：上一章/当前章/下一章） -->
    <swiper v-else class="body swiper" :current="slideCurrent" @change="onSlideChange" :style="{ background: theme.bg }">
      <swiper-item v-for="s in slides" :key="s.key">
        <scroll-view scroll-y class="slide-scroll">
          <view class="content" :style="{ paddingTop: contentTop }" @click="toggleToolbar">
            <text class="chapter-title" :style="{ color: theme.text, fontSize: (fontSize + 4) + 'px' }">{{ s.title }}</text>
            <template v-if="s.paragraphs">
              <text
                v-for="(p, i) in s.paragraphs"
                :key="i"
                class="para"
                :style="{ color: theme.text, fontSize: fontSize + 'px', lineHeight: lineHeight, textIndent: '2em' }"
              >{{ p }}</text>
            </template>
            <text v-else class="para loading-hint" :style="{ color: theme.subText }">加载中…</text>
          </view>
        </scroll-view>
      </swiper-item>
    </swiper>

    <!-- 加载中 / 出错提示 -->
    <view v-if="loading" class="center-tip"><text :style="{ color: theme.subText }">加载中…</text></view>
    <view v-if="error" class="center-tip">
      <text class="err-text" :style="{ color: theme.subText }">{{ error }}</text>
      <text class="retry" @click="loadContent(currentIdx)">重试</text>
    </view>

    <!-- 点击中央呼出的底部工具栏：目录 / 界面 / 设置 三键 -->
    <view v-if="toolbar" class="bottombar" :style="{ background: theme.barBg }">
      <view class="chapter-nav">
        <text class="nav-btn" :style="{ color: currentIdx > 0 ? theme.text : theme.subText }" @click="prevChapter">上一章</text>
        <text class="nav-pos" :style="{ color: theme.subText }">{{ currentIdx + 1 }}/{{ total }}</text>
        <text class="nav-btn" :style="{ color: currentIdx < total - 1 ? theme.text : theme.subText }" @click="nextChapter">下一章</text>
      </view>
      <view class="tool-row">
        <view class="tool-item" @click="openCatalog">
          <text class="tool-icon">☰</text>
          <text class="tool-name" :style="{ color: theme.subText }">目录</text>
        </view>
        <view class="tool-item" @click="panel = panel === 'ui' ? '' : 'ui'">
          <text class="tool-icon">Aa</text>
          <text class="tool-name" :style="{ color: theme.subText }">界面</text>
        </view>
        <view class="tool-item" @click="panel = panel === 'set' ? '' : 'set'">
          <text class="tool-icon">⚙</text>
          <text class="tool-name" :style="{ color: theme.subText }">设置</text>
        </view>
      </view>
    </view>

    <!-- 界面设置面板 -->
    <view v-if="panel === 'ui'" class="panel" :style="{ background: theme.barBg }">
      <view class="panel-row">
        <text class="panel-label" :style="{ color: theme.text }">字号</text>
        <view class="adjust">
          <text class="adj-btn" :style="{ color: theme.text }" @click="adjustFont(-1)">A-</text>
          <text class="adj-val" :style="{ color: theme.text }">{{ fontSize }}</text>
          <text class="adj-btn" :style="{ color: theme.text }" @click="adjustFont(1)">A+</text>
        </view>
      </view>
      <view class="panel-row">
        <text class="panel-label" :style="{ color: theme.text }">行距</text>
        <slider class="panel-slider" :min="12" :max="24" :step="1" :value="lineHeight10" activeColor="#E8383D" block-size="20" @change="onLineHeight" />
        <text class="panel-val" :style="{ color: theme.text }">{{ lineHeight.toFixed(1) }}</text>
      </view>
      <view class="panel-row">
        <text class="panel-label" :style="{ color: theme.text }">背景</text>
        <view class="themes">
          <view
            v-for="(t, key) in THEME_MAP"
            :key="key"
            class="theme-dot"
            :class="{ active: bgTheme === key && !nightMode }"
            :style="{ background: t.bg }"
            @click="pickTheme(key)"
          ></view>
        </view>
      </view>
      <view class="panel-row">
        <text class="panel-label" :style="{ color: theme.text }">翻页</text>
        <view class="modes">
          <text class="mode-btn" :class="{ on: pageMode === 'scroll' }" @click="setPageMode('scroll')">上下滚动</text>
          <text class="mode-btn" :class="{ on: pageMode === 'slide' }" @click="setPageMode('slide')">左右滑动</text>
        </view>
      </view>
    </view>

    <!-- 设置面板 -->
    <view v-if="panel === 'set'" class="panel" :style="{ background: theme.barBg }">
      <view class="panel-row">
        <text class="panel-label" :style="{ color: theme.text }">夜间模式</text>
        <switch :checked="nightMode" color="#E8383D" style="transform: scale(0.8)" @change="onNight" />
      </view>
      <view class="panel-row">
        <text class="panel-label" :style="{ color: theme.text }">屏幕常亮</text>
        <switch :checked="keepScreenOn" color="#E8383D" style="transform: scale(0.8)" @change="onKeepScreen" />
      </view>
      <view class="panel-row">
        <text class="panel-label" :style="{ color: theme.text }">服务器</text>
        <text class="panel-val link" @click="goAbout">去「关于」页修改 ›</text>
      </view>
    </view>

    <!-- 工具栏/面板存在时，点空白处收起 -->
    <view v-if="toolbar || panel" class="click-mask" @click="closePanels"></view>
  </view>
</template>

<script>
import api from '@/common/api.js'

/** 5 套阅读背景主题（夜间接 black） */
const THEME_MAP = {
  white: { bg: '#FFFFFF', text: '#222222', subText: '#909399', barBg: '#FFFFFF' },
  paper: { bg: '#F5EFDC', text: '#3B3226', subText: '#9C8F7A', barBg: '#F1EAD5' },
  green: { bg: '#C7EDCC', text: '#2C3A2E', subText: '#6B8A70', barBg: '#BDE4C4' },
  gray:  { bg: '#2B2B2B', text: '#B8B8B8', subText: '#777777', barBg: '#242424' },
  black: { bg: '#000000', text: '#9E9E9E', subText: '#666666', barBg: '#111111' }
}

export default {
  data() {
    return {
      THEME_MAP: THEME_MAP,
      bookId: '',
      book: null,
      chapters: [],
      currentIdx: 0,
      content: { title: '', paragraphs: [] },
      // slide 模式滑动窗口
      slides: [],
      slideCurrent: 0,
      swiperLock: false,   // 程序改 current 时吞掉一次 change 事件
      loading: false,
      error: '',
      toolbar: false,
      panel: '',
      fontSize: 18,
      lineHeight: 1.8,
      bgTheme: 'paper',
      pageMode: 'scroll',
      nightMode: false,
      keepScreenOn: true,
      scrollTop: 0,        // 绑定 scroll-view（只在跳章/恢复进度时改）
      scrollPos: 0,        // 用户实际滚动位置（只存进度，不绑定）
      savedScrollPos: 0,   // 启动时恢复用
      savingTimer: null,
      prefetching: {}
    }
  },
  computed: {
    total() {
      return this.chapters.length
    },
    theme() {
      return THEME_MAP[this.nightMode ? 'black' : this.bgTheme] || THEME_MAP.paper
    },
    contentTop() {
      return this.toolbar ? 'calc(var(--status-bar-height) + 110rpx)' : '30rpx'
    },
    lineHeight10() {
      return Math.round(this.lineHeight * 10)
    }
  },
  onLoad(params) {
    uni.$on('jumpToChapter', this.onJumpChapter)
    this.bookId = params.bookId
    this.book = (uni.getStorageSync('bookshelf') || []).find(b => b.id === this.bookId)
    if (!this.book) {
      uni.showToast({ title: '书籍不存在', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 800)
      return
    }
    const s = uni.getStorageSync('reader_settings') || {}
    this.fontSize = s.fontSize || 18
    this.lineHeight = s.lineHeight || 1.8
    this.bgTheme = s.bgTheme || 'paper'
    this.pageMode = s.pageMode || 'scroll'
    this.nightMode = !!s.nightMode
    this.keepScreenOn = s.keepScreenOn !== false

    this.chapters = uni.getStorageSync('chapters_' + this.bookId) || []
    const prog = uni.getStorageSync('progress_' + this.bookId) || {}
    const start = params.chapter !== undefined ? parseInt(params.chapter) : (prog.chapterIndex || 0)
    this.currentIdx = Math.max(0, Math.min(start, Math.max(0, this.total - 1)))
    this.savedScrollPos = prog.scrollTop || 0

    if (this.total === 0) {
      this.refreshChapters()
    }
    this.loadContent(this.currentIdx, params.chapter === undefined)
  },
  onShow() {
    if (this.keepScreenOn) uni.setKeepScreenOn({ keepScreenOn: true })
  },
  onHide() {
    this.saveProgress()
  },
  onUnload() {
    uni.$off('jumpToChapter', this.onJumpChapter)
    this.saveProgress()
    if (this.savingTimer) clearTimeout(this.savingTimer)
  },
  methods: {
    /** 目录页跳章 */
    onJumpChapter(idx) {
      this.loadContent(idx)
    },
    /** 拉目录（书架恢复场景） */
    refreshChapters() {
      api.getChapters(this.book.sourceId, this.book.bookUrl).then(list => {
        this.chapters = list || []
        uni.setStorageSync('chapters_' + this.bookId, this.chapters)
      }).catch(err => {
        this.error = '目录获取失败：' + err
      })
    },
    /** 读某一章：本地缓存优先，未命中走网络并写缓存 */
    loadContent(idx, restorePos) {
      if (idx < 0 || idx >= this.total) return
      this.currentIdx = idx
      const cache = uni.getStorageSync('chapter_' + this.bookId + '_' + idx)
      if (cache && cache.paragraphs) {
        this.applyContent(idx, cache, restorePos)
        return
      }
      const ch = this.chapters[idx]
      if (!ch) return
      this.loading = true
      this.error = ''
      api.getContent(this.book.sourceId, ch.chapterUrl).then(c => {
        this.loading = false
        const data = { title: c.title, paragraphs: c.paragraphs }
        uni.setStorageSync('chapter_' + this.bookId + '_' + idx, data)
        this.applyContent(idx, data, restorePos)
      }).catch(err => {
        this.loading = false
        this.error = String(err)
      })
    },
    /** 渲染某一章 + 更新书架进度 + 预取下一章 */
    applyContent(idx, data, restorePos) {
      this.error = ''
      this.content = data
      // scroll 模式：先归零，若有存档位置延迟恢复（内容渲染后再定位）
      this.scrollTop = 0
      if (restorePos && this.savedScrollPos > 0) {
        const pos = this.savedScrollPos
        setTimeout(() => { this.scrollTop = pos }, 300)
      }
      this.buildSlides(idx)
      this.saveProgress()
      this.prefetch(idx + 1)
      this.updateShelfProgress(idx, data.title)
    },
    /** slide 模式：以 idx 为中心建 3 章滑动窗口 */
    buildSlides(centerIdx) {
      if (this.pageMode !== 'slide') return
      const arr = []
      for (let i = centerIdx - 1; i <= centerIdx + 1; i++) {
        if (i < 0 || i >= this.total) continue
        const cache = uni.getStorageSync('chapter_' + this.bookId + '_' + i)
        arr.push({
          key: 's' + i,
          idx: i,
          title: cache ? cache.title : this.chapters[i].title,
          paragraphs: cache ? cache.paragraphs : null
        })
        if (!cache) this.fetchSlide(i)
      }
      this.swiperLock = true
      this.slides = arr
      const pos = arr.findIndex(s => s.idx === centerIdx)
      this.slideCurrent = pos < 0 ? 0 : pos
      setTimeout(() => { this.swiperLock = false }, 100)
    },
    /** 补拉窗口里缺的章 */
    fetchSlide(i) {
      const ch = this.chapters[i]
      if (!ch) return
      api.getContent(this.book.sourceId, ch.chapterUrl).then(c => {
        const data = { title: c.title, paragraphs: c.paragraphs }
        uni.setStorageSync('chapter_' + this.bookId + '_' + i, data)
        const item = this.slides.find(s => s.idx === i)
        if (item) {
          item.title = data.title
          item.paragraphs = data.paragraphs
        }
        if (i === this.currentIdx) this.content = data
      }).catch(() => {})
    },
    /** 预取下一章（静默） */
    prefetch(idx) {
      if (idx < 0 || idx >= this.total || this.prefetching[idx]) return
      if (uni.getStorageSync('chapter_' + this.bookId + '_' + idx)) return
      this.prefetching[idx] = true
      const ch = this.chapters[idx]
      api.getContent(this.book.sourceId, ch.chapterUrl).then(c => {
        uni.setStorageSync('chapter_' + this.bookId + '_' + idx, { title: c.title, paragraphs: c.paragraphs })
        delete this.prefetching[idx]
      }).catch(() => {
        delete this.prefetching[idx]
      })
    },
    /** 书架列表同步最新阅读信息 */
    updateShelfProgress(idx, title) {
      const shelf = uni.getStorageSync('bookshelf') || []
      const i = shelf.findIndex(b => b.id === this.bookId)
      if (i >= 0) {
        shelf[i].lastChapterIndex = idx
        shelf[i].lastChapterTitle = title
        shelf[i].lastReadTime = Date.now()
        uni.setStorageSync('bookshelf', shelf)
      }
    },
    saveProgress() {
      uni.setStorageSync('progress_' + this.bookId, {
        chapterIndex: this.currentIdx,
        scrollTop: this.scrollPos || 0
      })
    },
    onScroll(e) {
      // 节流记录滚动位置（只写 scrollPos，不碰绑定的 scrollTop，避免回跳）
      this.scrollPos = e.detail.scrollTop
      if (this.savingTimer) return
      this.savingTimer = setTimeout(() => { this.savingTimer = null }, 500)
    },
    prevChapter() {
      if (this.currentIdx <= 0) {
        uni.showToast({ title: '已经是第一章', icon: 'none' })
        return
      }
      this.loadContent(this.currentIdx - 1)
    },
    nextChapter() {
      if (this.currentIdx >= this.total - 1) {
        uni.showToast({ title: '已是最新章节', icon: 'none' })
        return
      }
      this.loadContent(this.currentIdx + 1)
    },
    /** slide 模式滑到新章：重建窗口 */
    onSlideChange(e) {
      if (this.swiperLock) {
        this.swiperLock = false
        return
      }
      const s = this.slides[e.detail.current]
      if (!s || s.idx === this.currentIdx) return
      this.currentIdx = s.idx
      const cache = uni.getStorageSync('chapter_' + this.bookId + '_' + s.idx)
      if (cache) {
        this.content = cache
        this.updateShelfProgress(s.idx, cache.title)
        this.saveProgress()
        this.prefetch(s.idx + 1)
      }
      this.buildSlides(s.idx)
    },
    openCatalog() {
      uni.navigateTo({ url: '/pages/catalog/catalog?bookId=' + this.bookId })
    },
    goBack() {
      this.saveProgress()
      uni.navigateBack()
    },
    goAbout() {
      uni.navigateTo({ url: '/pages/about/about' })
    },
    closePanels() {
      this.toolbar = false
      this.panel = ''
    },
    /** 点正文中央：呼出/收起工具栏 */
    toggleToolbar() {
      if (this.panel) {
        this.panel = ''
        return
      }
      this.toolbar = !this.toolbar
    },
    persistSettings() {
      uni.setStorageSync('reader_settings', {
        fontSize: this.fontSize,
        lineHeight: this.lineHeight,
        bgTheme: this.bgTheme,
        pageMode: this.pageMode,
        nightMode: this.nightMode,
        keepScreenOn: this.keepScreenOn
      })
    },
    adjustFont(d) {
      const v = this.fontSize + d
      if (v >= 12 && v <= 36) {
        this.fontSize = v
        this.persistSettings()
      }
    },
    onLineHeight(e) {
      this.lineHeight = e.detail.value / 10
      this.persistSettings()
    },
    pickTheme(key) {
      this.bgTheme = key
      this.nightMode = false
      this.persistSettings()
    },
    setPageMode(m) {
      this.pageMode = m
      if (m === 'slide') this.buildSlides(this.currentIdx)
      this.persistSettings()
    },
    onNight(e) {
      this.nightMode = e.detail.value
      this.persistSettings()
    },
    onKeepScreen(e) {
      this.keepScreenOn = e.detail.value
      uni.setKeepScreenOn({ keepScreenOn: this.keepScreenOn })
      this.persistSettings()
    }
  }
}
</script>

<style>
.reader {
  position: fixed;
  left: 0; top: 0; right: 0; bottom: 0;
}
.topbar {
  position: absolute;
  left: 0; right: 0; top: 0;
  z-index: 30;
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.06);
}
.status-pad { height: var(--status-bar-height); }
.topbar-row {
  height: 100rpx;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}
.tb-back {
  font-size: 56rpx;
  width: 60rpx;
  color: #e8383d;
}
.tb-title {
  flex: 1;
  overflow: hidden;
}
.tb-book {
  font-size: 30rpx;
  font-weight: bold;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tb-chapter {
  font-size: 22rpx;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.body {
  position: absolute;
  left: 0; top: 0; right: 0; bottom: 0;
}
.slide-scroll { height: 100%; }
.content {
  padding: 30rpx 40rpx 40rpx;
}
.chapter-title {
  font-weight: bold;
  margin-bottom: 30rpx;
  display: block;
}
.para {
  display: block;
  margin-bottom: 20rpx;
}
.loading-hint { text-align: center; padding-top: 100rpx; }
.chapter-end {
  margin-top: 40rpx;
  text-align: center;
  padding-bottom: 60rpx;
}
.end-btn { font-size: 26rpx; }
.body-pad-bottom { height: 200rpx; }
.center-tip {
  position: absolute;
  left: 0; right: 0;
  top: 40%;
  text-align: center;
  z-index: 20;
}
.err-text {
  display: block;
  font-size: 26rpx;
  padding: 0 60rpx;
  line-height: 1.6;
}
.retry {
  display: inline-block;
  margin-top: 24rpx;
  color: #e8383d;
  font-size: 28rpx;
  padding: 10rpx 40rpx;
  border: 1rpx solid #e8383d;
  border-radius: 32rpx;
}
.bottombar {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  z-index: 40;
  border-top: 1rpx solid rgba(0, 0, 0, 0.06);
  padding-bottom: env(safe-area-inset-bottom);
}
.chapter-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 40rpx 8rpx;
}
.nav-btn { font-size: 28rpx; }
.nav-pos { font-size: 24rpx; }
.tool-row {
  display: flex;
  padding: 16rpx 0 30rpx;
}
.tool-item {
  flex: 1;
  text-align: center;
}
.tool-icon {
  font-size: 40rpx;
  display: block;
  color: #e8383d;
  font-weight: bold;
}
.tool-name {
  font-size: 22rpx;
  margin-top: 6rpx;
  display: block;
}
.panel {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  z-index: 50;
  border-top: 1rpx solid rgba(0, 0, 0, 0.06);
  padding: 24rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
}
.panel-row {
  display: flex;
  align-items: center;
  min-height: 90rpx;
}
.panel-label {
  width: 120rpx;
  font-size: 28rpx;
}
.adjust {
  display: flex;
  align-items: center;
}
.adj-btn {
  width: 90rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  border: 1rpx solid rgba(128, 128, 128, 0.4);
  border-radius: 10rpx;
  font-size: 28rpx;
}
.adj-val {
  width: 100rpx;
  text-align: center;
  font-size: 28rpx;
}
.panel-slider { flex: 1; margin: 0 16rpx; }
.panel-val {
  font-size: 26rpx;
  min-width: 70rpx;
  text-align: right;
}
.panel-val.link { color: #e8383d; }
.themes {
  display: flex;
  flex: 1;
  justify-content: space-around;
}
.theme-dot {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(128, 128, 128, 0.5);
}
.theme-dot.active {
  border: 4rpx solid #e8383d;
}
.modes {
  display: flex;
  flex: 1;
  justify-content: space-around;
}
.mode-btn {
  padding: 12rpx 28rpx;
  border: 1rpx solid rgba(128, 128, 128, 0.4);
  border-radius: 10rpx;
  font-size: 26rpx;
}
.mode-btn.on {
  color: #e8383d;
  border-color: #e8383d;
}
.click-mask {
  position: absolute;
  left: 0; top: 0; right: 0; bottom: 0;
  z-index: 25;
}
</style>
